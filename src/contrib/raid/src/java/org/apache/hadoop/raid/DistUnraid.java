package org.apache.hadoop.raid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FsShell;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileRecordReader;
import org.apache.hadoop.raid.RaidNode.Statistics;
import org.apache.hadoop.raid.protocol.PolicyInfo;
import org.apache.hadoop.util.StringUtils;

public class DistUnraid {

  protected static final Log LOG = LogFactory.getLog(DistUnraid.class);

  static final String NAME = "distUnraid";
  static final String JOB_DIR_LABEL = NAME + ".job.dir";
  static final String OP_LIST_LABEL = NAME + ".op.list";
  static final String OP_COUNT_LABEL = NAME + ".op.count";
  static final int   OP_LIST_BLOCK_SIZE = 32 * 1024 * 1024; // block size of control file
  static final short OP_LIST_REPLICATION = 10; // replication factor of control file

  private static final long OP_PER_MAP = 100;
  private static final int MAX_MAPS_PER_NODE = 20;
  private static final int SYNC_FILE_MAX = 10;
  private static final SimpleDateFormat dateForm = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private static String jobName = NAME;

  static enum Counter {
    FILES_SUCCEEDED, FILES_FAILED, PROCESSED_BLOCKS, PROCESSED_SIZE, META_BLOCKS, META_SIZE
  }

  protected JobConf jobconf;

  /** {@inheritDoc} */
  public void setConf(Configuration conf) {
    if (jobconf != conf) {
      jobconf = conf instanceof JobConf ? (JobConf) conf : new JobConf(conf);
    }
  }

  /** {@inheritDoc} */
  public JobConf getConf() {
    return jobconf;
  }

  public DistUnraid(Configuration conf) {
    setConf(createJobConf(conf));
  }

  private static final Random RANDOM = new Random();

  protected static String getRandomId() {
    return Integer.toString(RANDOM.nextInt(Integer.MAX_VALUE), 36);
  }

  /**
   * 
   * helper class which holds the policy and paths
   * 
   */
  public static class UnraidArray {
    public int stripeLength;
    public String srcPath;
    public String destPath;
    public Integer Block;

    UnraidArray(String srcPath, String destPath ,Integer Block,int stripeLength) {
      this.srcPath = srcPath;
      this.Block = Block;
      this.destPath = destPath;
      this.stripeLength = stripeLength;
    }
  }

  List<UnraidArray> raidPolicyPathPairList = new ArrayList<UnraidArray>();

  /** Responsible for generating splits of the src file list. */
  static class DistUnraidInputFormat implements InputFormat<Text, ArrayWritableText> {
    /** Do nothing. */
    public void validateInput(JobConf job) {
    }

    /**
     * Produce splits such that each is no greater than the quotient of the
     * total size and the number of splits requested.
     * 
     * @param job
     *          The handle to the JobConf object
     * @param numSplits
     *          Number of splits requested
     */
    public InputSplit[] getSplits(JobConf job, int numSplits)
        throws IOException {
      final int srcCount = job.getInt(OP_COUNT_LABEL, -1);
      final int targetcount = srcCount / numSplits;
      String srclist = job.get(OP_LIST_LABEL, "");
      if (srcCount < 0 || "".equals(srclist)) {
        throw new RuntimeException("Invalid metadata: #files(" + srcCount
            + ") listuri(" + srclist + ")");
      }
      Path srcs = new Path(srclist);
      FileSystem fs = srcs.getFileSystem(job);

      List<FileSplit> splits = new ArrayList<FileSplit>(numSplits);

      Text key = new Text();
      
      Text[] v = new Text[3];
      ArrayWritableText value = new ArrayWritableText(Text.class,v);
      SequenceFile.Reader in = null;
      long prev = 0L;
      int count = 0; // count src
      try {
        for (in = new SequenceFile.Reader(fs, srcs, job); in.next(key, value);) {
          long curr = in.getPosition();
          long delta = curr - prev;
          if (++count > targetcount) {
            count = 0;
            splits.add(new FileSplit(srcs, prev, delta, (String[]) null));
            prev = curr;
          }
        }
      } finally {
        in.close();
      }
      long remaining = fs.getFileStatus(srcs).getLen() - prev;
      if (remaining != 0) {
        splits.add(new FileSplit(srcs, prev, remaining, (String[]) null));
      }
      LOG.info("jobname= " + jobName + " numSplits=" + numSplits + 
               ", splits.size()=" + splits.size());
      return splits.toArray(new FileSplit[splits.size()]);
    }

    /** {@inheritDoc} */
    public RecordReader<Text, ArrayWritableText> getRecordReader(InputSplit split,
        JobConf job, Reporter reporter) throws IOException {
      return new SequenceFileRecordReader<Text, ArrayWritableText>(job,
          (FileSplit) split);
    }
  }

  /** The mapper for raiding files. */
  static class DistUnraidMapper implements
      Mapper<Text, ArrayWritableText, WritableComparable, Text> {
    private JobConf jobconf;
    private boolean ignoreFailures;

    private int failcount = 0;
    private int succeedcount = 0;
    private Statistics st = null;

    private String getCountString() {
      return "Succeeded: " + succeedcount + " Failed: " + failcount;
    }

    /** {@inheritDoc} */
    public void configure(JobConf job) {
      this.jobconf = job;
      ignoreFailures = false;
      st = new Statistics();
    }

    /** Run a FileOperation */
    public void map(Text file, ArrayWritableText value,
        OutputCollector<WritableComparable, Text> out, Reporter reporter)
        throws IOException {
    	LOG.info("**********************start map**************************");
    	//file:srcPath
    	//values[0]:BlockIndexNo
    	//values[1]:destPath
    	//values[2]:stripeLength
    	String[] args = new String[3];
        Text[] values = values = (Text[])value.toArray();
        	
        int Block = Integer.valueOf(values[0].toString());     
        
        Path destPath = new Path(values[1].toString().trim());
        FileSystem fs = FileSystem.get(destPath.toUri(), jobconf);
        destPath = destPath.makeQualified(fs);
        
        int stripeLength = Integer.valueOf(values[2].toString());
        
        Path inputPath = new Path(file.toString());
        Path srcPath = inputPath.makeQualified(inputPath.getFileSystem(jobconf));
        

        if(LOGDISPLAY1) LOG.info("srcPath = " +srcPath.toString());
        if(LOGDISPLAY1) LOG.info("destPath = " +destPath);
        if(LOGDISPLAY1) LOG.info("Block = " +Block);
        if(LOGDISPLAY1) LOG.info("stripeLength = " +stripeLength);
	    
        Path unRaidFile = RaidNode.unRaid(jobconf, srcPath, destPath, stripeLength, Block);
        
        
        if(LOGDISPLAY1)  LOG.info("------unRaidFile == "+unRaidFile+"-----");
		
		fs.delete(new Path(file.toString()));
		
		args[0] = "-cp";
		args[1] = unRaidFile.toUri().getPath();
		args[2] = file.toString();
		
		FsShell fsShell = new FsShell(jobconf);
		try {
			fsShell.run(args);
		} catch (Exception e) {
			e.printStackTrace();
		}
		fs.delete(unRaidFile);
		LOG.info("**********************end map**************************");
        
        
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
      if (failcount == 0 || ignoreFailures) {
        return;
      }
      throw new IOException(getCountString());
    }
    
  }

  /**
   * create new job conf based on configuration passed.
   * 
   * @param conf
   * @return
   */
  private static JobConf createJobConf(Configuration conf) {
    JobConf jobconf = new JobConf(conf, DistUnraid.class);
    jobName = NAME + " " + dateForm.format(new Date(RaidNode.now()));
    jobconf.setJobName(jobName);
    jobconf.setMapSpeculativeExecution(false);

    
    jobconf.setJarByClass(DistUnraid.class);
    jobconf.setInputFormat(DistUnraidInputFormat.class);
    jobconf.setOutputKeyClass(Text.class);
    jobconf.setOutputValueClass(Text.class);
    
    jobconf.setMapperClass(DistUnraidMapper.class);
    jobconf.setNumReduceTasks(0);
    return jobconf;
  }

  /** Add paths to be raided */
  public void addUnraidBlock(String srcPath, String destPath ,Integer Block,int stripeLength) {
    raidPolicyPathPairList.add(new UnraidArray(srcPath, destPath, Block,stripeLength));
  }

  /** Calculate how many maps to run. */
  private static int getMapCount(int srcCount, int numNodes) {
    int numMaps = (int) (srcCount / OP_PER_MAP);
    numMaps = Math.min(numMaps, numNodes * MAX_MAPS_PER_NODE);
    return Math.max(numMaps, MAX_MAPS_PER_NODE);
  }

  /** invokes mapred job do parallel raiding */
  public void doDistUnraid() throws IOException {
	if(LOGDISPLAY1)  LOG.info("06.1------DistUnraid.doDistUnraid()-----");
    if (raidPolicyPathPairList.size() == 0) {
      LOG.info("DistRaid has no paths to raid.");
      return;
    }
    try {
      if (setup()) {
        JobClient.runJob(jobconf);
      }
    } finally {
      // delete job directory
      final String jobdir = jobconf.get(JOB_DIR_LABEL);
      if (jobdir != null) {
        final Path jobpath = new Path(jobdir);
        jobpath.getFileSystem(jobconf).delete(jobpath, true);
      }
    }
    raidPolicyPathPairList.clear();
    if(LOGDISPLAY1)  LOG.info("06.1+++++DistUnraid.doDistUnraid()+++++");
  }

  /**
   * set up input file which has the list of input files.
   * 
   * @return boolean
   * @throws IOException
   */
  private boolean setup() throws IOException {
	  if(LOGDISPLAY1)  LOG.info("07.1------DistRaid.setup()-----");
    final String randomId = getRandomId();
    JobClient jClient = new JobClient(jobconf);
    Path jobdir = new Path(jClient.getSystemDir(), NAME + "_" + randomId);

    LOG.info(JOB_DIR_LABEL + "=" + jobdir);
    jobconf.set(JOB_DIR_LABEL, jobdir.toString());
    Path log = new Path(jobdir, "_logs");

    // The control file should have small size blocks. This helps
    // in spreading out the load from mappers that will be spawned.
    jobconf.setInt("dfs.blocks.size",  OP_LIST_BLOCK_SIZE);

    FileOutputFormat.setOutputPath(jobconf, log);
    LOG.info("log=" + log);

    // create operation list
    FileSystem fs = jobdir.getFileSystem(jobconf);
    Path opList = new Path(jobdir, "_" + OP_LIST_LABEL);
    jobconf.set(OP_LIST_LABEL, opList.toString());
    int opCount = 0, synCount = 0;
    SequenceFile.Writer opWriter = null;
    try {
      opWriter = SequenceFile.createWriter(fs, jobconf, opList, Text.class,
          ArrayWritableText.class, SequenceFile.CompressionType.NONE);
      for (UnraidArray p : raidPolicyPathPairList) {
    	  Text[] value = new Text[3];
    	  value[0] = new Text(p.Block.toString());
    	  value[1] = new Text(p.destPath);
    	  value[2] = new Text(String.valueOf(p.stripeLength));
          ArrayWritableText aw = new ArrayWritableText(Text.class,value);
          
          opWriter.append(new Text(p.srcPath), aw);
          opCount++;
          if (++synCount > SYNC_FILE_MAX) {
            opWriter.sync();
            synCount = 0;
          }
        
      }

    } finally {
      if (opWriter != null) {
        opWriter.close();
      }
      fs.setReplication(opList, OP_LIST_REPLICATION); // increase replication for control file
    }
    raidPolicyPathPairList.clear();
    
    jobconf.setInt(OP_COUNT_LABEL, opCount);
    LOG.info("Number of files=" + opCount);
    jobconf.setNumMapTasks(getMapCount(opCount, new JobClient(jobconf)
        .getClusterStatus().getTaskTrackers()));
    LOG.info("jobName= " + jobName + " numMapTasks=" + jobconf.getNumMapTasks());
    
    if(LOGDISPLAY)  LOG.info("07.1+++++DistRaid.setup()+++++");
    return opCount != 0;

  }
  
  
  
  
  public static  boolean LOGDISPLAY=false;
  public static  boolean LOGDISPLAY1=false;
  public static  boolean LOGDISPLAY2=false;
  public static  boolean LOGDISPLAY3=false;
  public static  boolean LOGDISPLAY4=false;
  public static  boolean LOGDISPLAY5=false;
  public static  boolean LOGDISPLAY6=false;
  public static  boolean LOGDISPLAY7=false;
  public static  boolean LOGDISPLAY8=false;
  public static  boolean LOGDISPLAY9=false;
  public static  boolean LOGDISPLAY10=false;
  public static  boolean LOGDISPLAY0=false;
  public static  boolean LOGDEBUG=false;
  static
  {
	   LOGDISPLAY=System.getProperty("LOGDISPLAY")!=null&&System.getProperty("LOGDISPLAY").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY1=System.getProperty("LOGDISPLAY1")!=null&&System.getProperty("LOGDISPLAY1").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY2=System.getProperty("LOGDISPLAY2")!=null&&System.getProperty("LOGDISPLAY2").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY3=System.getProperty("LOGDISPLAY3")!=null&&System.getProperty("LOGDISPLAY3").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY4=System.getProperty("LOGDISPLAY4")!=null&&System.getProperty("LOGDISPLAY4").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY5=System.getProperty("LOGDISPLAY5")!=null&&System.getProperty("LOGDISPLAY5").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY6=System.getProperty("LOGDISPLAY6")!=null&&System.getProperty("LOGDISPLAY6").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY7=System.getProperty("LOGDISPLAY7")!=null&&System.getProperty("LOGDISPLAY7").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY8=System.getProperty("LOGDISPLAY8")!=null&&System.getProperty("LOGDISPLAY8").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY9=System.getProperty("LOGDISPLAY9")!=null&&System.getProperty("LOGDISPLAY9").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY10=System.getProperty("LOGDISPLAY10")!=null&&System.getProperty("LOGDISPLAY10").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY0=System.getProperty("LOGDISPLAY0")!=null&&System.getProperty("LOGDISPLAY0").equalsIgnoreCase("true")?true:false;
	   LOGDEBUG=System.getProperty("LOGDEBUG")!=null&&System.getProperty("LOGDEBUG").equalsIgnoreCase("true")?true:false;
  }
}
