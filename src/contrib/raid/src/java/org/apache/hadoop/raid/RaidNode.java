/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.raid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;
import java.lang.Thread;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.ipc.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.tools.HadoopArchives;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Daemon;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.fs.FsShell;
import org.apache.hadoop.fs.HarFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.mapred.Reporter;

import org.apache.hadoop.raid.protocol.PolicyInfo;
import org.apache.hadoop.raid.protocol.PolicyList;
import org.apache.hadoop.raid.protocol.RaidProtocol;

/**
 * A {@link RaidNode} that implements 
 */
public class RaidNode implements RaidProtocol {

  static{
    Configuration.addDefaultResource("hdfs-default.xml");
    Configuration.addDefaultResource("hdfs-site.xml");
    Configuration.addDefaultResource("mapred-default.xml");
    Configuration.addDefaultResource("mapred-site.xml");
  }
  public static final Log LOG = LogFactory.getLog( "org.apache.hadoop.raid.RaidNode");
  public static final long SLEEP_TIME = 10000L; // 10 seconds
  public static final int DEFAULT_PORT = 60000;
  public static final int DEFAULT_STRIPE_LENGTH = 5; // default value of stripe length
  public static final String DEFAULT_RAID_LOCATION = "/raid";
  public static final String HAR_SUFFIX = "_raid.har";
  
  /** RPC server */
  private Server server;
  /** RPC server address */
  private InetSocketAddress serverAddress = null;
  /** only used for testing purposes  */
  private boolean stopRequested = false;

  /** Configuration Manager */
  private ConfigManager configMgr;

  /** hadoop configuration */
  private Configuration conf;

  protected boolean initialized;  // Are we initialized?
  protected volatile boolean running; // Are we running?

  /** Deamon thread to trigger policies */
  Daemon triggerThread = null;

  /** Deamon thread to delete obsolete parity files */
  Daemon purgeThread = null;
  
  /** Deamon thread to har raid directories */
  Daemon harThread = null;

  /** Do do distributed raiding */
  boolean isRaidLocal = false;
  
  Daemon recoverThread = null;
  
  Daemon fsShellThread = null;
  
  private ArrayList<Map<FileStatus,ArrayList<Integer>>> queue = new ArrayList<Map<FileStatus,ArrayList<Integer>>>();
  private LinkedBlockingDeque<ArrayList<Map<FileStatus,ArrayList<Integer>>>> qqueue = new LinkedBlockingDeque<ArrayList<Map<FileStatus,ArrayList<Integer>>>>();
  // statistics about RAW hdfs blocks. This counts all replicas of a block.
  // statistics about RAW hdfs blocks. This counts all replicas of a block.
  public static class Statistics {
    long numProcessedBlocks; // total blocks encountered in namespace
    long processedSize;   // disk space occupied by all blocks
    long remainingSize;      // total disk space post RAID
    
    long numMetaBlocks;      // total blocks in metafile
    long metaSize;           // total disk space for meta files

    public void clear() {
    	numProcessedBlocks = 0;
    	processedSize = 0;
    	remainingSize = 0;
    	numMetaBlocks = 0;
    	metaSize = 0;
    }
    public String toString() {
      long save = processedSize - (remainingSize + metaSize);
      long savep = 0;
      if (processedSize > 0) {
        savep = (save * 100)/processedSize;
      }
      String msg = " numProcessedBlocks = " + numProcessedBlocks +
                   " processedSize = " + processedSize +
                   " postRaidSize = " + remainingSize +
                   " numMetaBlocks = " + numMetaBlocks +
                   " metaSize = " + metaSize +
                   " %save in raw disk space = " + savep;
      return msg;
    }
  }

  // Startup options
  static public enum StartupOption{
    TEST ("-test"),
    REGULAR ("-regular");

    private String name = null;
    private StartupOption(String arg) {this.name = arg;}
    public String getName() {return name;}
  }
  
  /**
   * Start RaidNode.
   * <p>
   * The raid-node can be started with one of the following startup options:
   * <ul> 
   * <li>{@link StartupOption#REGULAR REGULAR} - normal raid node startup</li>
   * </ul>
   * The option is passed via configuration field: 
   * <tt>fs.raidnode.startup</tt>
   * 
   * The conf will be modified to reflect the actual ports on which 
   * the RaidNode is up and running if the user passes the port as
   * <code>zero</code> in the conf.
   * 
   * @param conf  confirguration
   * @throws IOException
   */

  RaidNode(Configuration conf) throws IOException {
	if(LOGDISPLAY)  LOG.info("02------RaidNode.init()-----");
    try {
      initialize(conf);
    } catch (IOException e) {
      this.stop();
      throw e;
    } catch (Exception e) {
      this.stop();
      throw new IOException(e);
    }
    if(LOGDISPLAY)  LOG.info("02+++++RaidNode.init()+++++");
  }

  public long getProtocolVersion(String protocol,
                                 long clientVersion) throws IOException {
    if (protocol.equals(RaidProtocol.class.getName())) {
      return RaidProtocol.versionID;
    } else {
      throw new IOException("Unknown protocol to name node: " + protocol);
    }
  }

  /**
   * Wait for service to finish.
   * (Normally, it runs forever.)
   */
  public void join() {
    try {
      if (server != null) server.join();
      if (triggerThread != null) triggerThread.join();
      if (purgeThread != null) purgeThread.join();
    } catch (InterruptedException ie) {
      // do nothing
    }
  }
  
  /**
   * Stop all RaidNode threads and wait for all to finish.
   */
  public void stop() {
    if (stopRequested) {
      return;
    }
    stopRequested = true;
    running = false;
    if (server != null) server.stop();
    if (triggerThread != null) triggerThread.interrupt();
    if (purgeThread != null) purgeThread.interrupt();
  }

  private static InetSocketAddress getAddress(String address) {
    return NetUtils.createSocketAddr(address);
  }

  public static InetSocketAddress getAddress(Configuration conf) {
    String nodeport = conf.get("raid.server.address");
    if (nodeport == null) {
      nodeport = "localhost:" + DEFAULT_PORT;
    }
    return getAddress(nodeport);
  }

  public InetSocketAddress getListenerAddress() {
    return server.getListenerAddress();
  }
  
  private void initialize(Configuration conf) 
    throws IOException, SAXException, InterruptedException, RaidConfigurationException,
           ClassNotFoundException, ParserConfigurationException {
	if(LOGDISPLAY)  LOG.info("03------RaidNode.initialize()-----");
    this.conf = conf;
    InetSocketAddress socAddr = RaidNode.getAddress(conf);
    int handlerCount = conf.getInt("fs.raidnode.handler.count", 10);

    isRaidLocal = conf.getBoolean("fs.raidnode.local", false);
    // read in the configuration
    configMgr = new ConfigManager(conf);

    // create rpc server 
    this.server = RPC.getServer(this, socAddr.getHostName(), socAddr.getPort(),
                                handlerCount, false, conf);

    // The rpc-server port can be ephemeral... ensure we have the correct info
    this.serverAddress = this.server.getListenerAddress();
    LOG.info("RaidNode up at: " + this.serverAddress);

    initialized = true;
    running = true;
    this.server.start(); // start RPC server

    // start the deamon thread to fire polcies appropriately
    this.triggerThread = new Daemon(new TriggerMonitor());
    this.triggerThread.start();

    // start the thread that deletes obsolete parity files
    this.purgeThread = new Daemon(new PurgeMonitor());
    this.purgeThread.start();

    // start the thread that creates HAR files
    this.harThread = new Daemon(new HarMonitor());
    this.harThread.start();
    
    this.recoverThread = new Daemon(new RecoverMonitor());
    this.recoverThread.start();
    if(LOGDISPLAY)  LOG.info("03+++++RaidNode.initialize()+++++");
  }

  
  /**
   * Implement RaidProtocol methods
   */

  /** {@inheritDoc} */
  public PolicyList[] getAllPolicies() throws IOException {
    Collection<PolicyList> list = configMgr.getAllPolicies();
    return list.toArray(new PolicyList[list.size()]);
  }

  /** {@inheritDoc} */
  public String recoverFile(String inStr, long corruptOffset) throws IOException {

    LOG.info("Recover File for " + inStr + " for corrupt offset " + corruptOffset);
    Path inputPath = new Path(inStr);
    Path srcPath = inputPath.makeQualified(inputPath.getFileSystem(conf));
    PolicyInfo info = findMatchingPolicy(srcPath);
    if (info != null) {

      // find stripe length from config
      int stripeLength = getStripeLength(conf, info);

      // create destination path prefix
      String destPrefix = getDestinationPath(conf, info);
      Path destPath = new Path(destPrefix.trim());
      FileSystem fs = FileSystem.get(destPath.toUri(), conf);
      destPath = destPath.makeQualified(fs);
      Path unraided = unRaidWithoutRecoverFile(conf, srcPath, destPath, stripeLength, (int)corruptOffset);
      //Path unraided = unRaid(conf, srcPath, destPath, stripeLength, corruptOffset);
      if (unraided != null) {
        return unraided.toString();
      }
    }
    return null;
  }
  
  /**
   * Periodically 
   */
  class RecoverMonitor implements Runnable {
    /**
     */
    public void run() {
      if(LOGDISPLAY)  LOG.info("------RaidNode.RecoverMonitor.run()-----");
      while (running) {
        try {
          doProcess();
        } catch (Exception e) {
          LOG.error(StringUtils.stringifyException(e));
        } finally {
          LOG.info("Recover thread continuing to run...");
        }
      }
      if(LOGDISPLAY)  LOG.info("+++++RaidNode.RecoverMonitor.run()+++++");
    }


    /**
     * Keep processing policies.
     * If the config file has changed, then reload config file and start afresh.
     */
    private void doProcess() throws IOException, InterruptedException {
    	if(LOGDISPLAY)  LOG.info("------RaidNode.RecoverMonitor.doProcess()-----");
    	ArrayList<Map<FileStatus,ArrayList<Integer>>> queue = null;
    	DistUnraid urd = new DistUnraid(conf);
    	ArrayList<Integer> missBlocks = null;
    	String unRaidFile = null;
    	FileSystem fs = null;
    	Path destPath = null;
    	String[] args = new String[3];
    	int stripeLength = 0;
    	String destPrefix;
    	DFSClient dfs = new DFSClient(conf);
      long prevExec = 0;
      while (running) {
    	  if(LOGDISPLAY)  LOG.info("=====================unraid==============================");
        boolean reload = configMgr.reloadConfigsIfNecessary();
        while(!reload && now() < prevExec + configMgr.getPeriodicity()){
          Thread.sleep(SLEEP_TIME);
          reload = configMgr.reloadConfigsIfNecessary();
        }
        prevExec = now();  
        queue = qqueue.take();
        //recoveredFileToMissBlocks = queue.take();   
        if(LOGDISPLAY)  LOG.info("*****start unraid!!!*****");
        if(queue != null)
        	for(Map<FileStatus,ArrayList<Integer>> recoveredFileToMissBlocks : queue)        
		        if(recoveredFileToMissBlocks != null){
		        	for(FileStatus file : recoveredFileToMissBlocks.keySet()){
		        		missBlocks = recoveredFileToMissBlocks.get(file);
		        		if(missBlocks != null && checkMissBlocks(missBlocks,file)){
		        			
			        		for(Integer Block : missBlocks){	
			        			if(LOGDISPLAY)  LOG.info("------file == "+file.getPath().toUri().getPath()+"-----");
			        			if(LOGDISPLAY)  LOG.info("------Block == "+Block+"-----");
			        			/*
			        		    Path srcPath = file.getPath();
			        		    PolicyInfo info = findMatchingPolicy(srcPath);
			        		    if (info != null) {
			        		      // find stripe length from config
			        		      stripeLength = getStripeLength(conf, info);
			        		      if(LOGDISPLAY)  LOG.info("stripeLength = " +stripeLength);
			        		      // create destination path prefix
			        		      destPrefix = getDestinationPath(conf, info);
			        		     
			        		      destPath = new Path(destPrefix.trim());
			        		      fs = FileSystem.get(destPath.toUri(), conf);
			        		      destPath = destPath.makeQualified(fs);
			        		      if(LOGDISPLAY)  LOG.info("destPath = " +destPath);
			        		      //Path unraided = unRaid(conf, srcPath, destPath, stripeLength, Block);			        		      
			        		    }
			        		    if(LOGDISPLAY1)  LOG.info("srcPath = " +srcPath.toString());
			        		    if(LOGDISPLAY1)  LOG.info("destPath = " +destPath);
			        		    if(LOGDISPLAY1)  LOG.info("Block = " +Block);
			        		    if(LOGDISPLAY1)  LOG.info("stripeLength = " +stripeLength);
			        			*/
			        			
			        			unRaidFile = recoverFile(file.getPath().toUri().getPath(),Block);
			        			
			        			if(LOGDISPLAY)  LOG.info("------unRaidFile == "+unRaidFile+"-----");
			        			
			        			
			        			
			        			/*
			        			fs = file.getPath().getFileSystem(conf);
			        			fs.delete(new Path(file.getPath().toUri().getPath()));
			        			
			        			args[0] = "-cp";
			        			args[1] = new Path(unRaidFile).toUri().getPath();
			        			args[2] = file.getPath().toUri().getPath();
			        			
			        			FsShell fsShell = new FsShell(conf);
			        			try {
									fsShell.run(args);
								} catch (Exception e) {
									e.printStackTrace();
								}
								fs.delete(new Path(unRaidFile));*/
			        			//fsShellThread = new Daemon(new FsShellMonitor(args));
			        			//fsShellThread.start();
			        			//fs.rename(new Path(new Path(unRaidFile).toUri().getPath()),new Path(file.getPath().toUri().getPath()));
			        		}
		        		}
		        	}
		        }  
        urd.doDistUnraid(); 
        queue.clear();
      
    } 
      if(LOGDISPLAY)  LOG.info("+++++RaidNode.RecoverMonitor.doProcess()+++++");
  }
    private boolean checkMissBlocks(ArrayList<Integer> missBlocks,FileStatus file){
    	Integer[] intMissBlocks = new Integer[missBlocks.size()];
    	int stripeLength = 0;
    	Path inputPath = file.getPath();
        Path srcPath;
    	
    	missBlocks.toArray(intMissBlocks);    	
    	Arrays.sort(intMissBlocks);
    	
		try {
			srcPath = inputPath.makeQualified(inputPath.getFileSystem(conf));
			PolicyInfo info = findMatchingPolicy(srcPath);
	        stripeLength = getStripeLength(conf, info);
		} catch (IOException e) {
			e.printStackTrace();
		}
        int tmp = -1;
    	for(Integer i : intMissBlocks){
    		if(tmp == -1){
    			tmp = i;
    		} else {
    			if(Math.abs(tmp - i) < stripeLength){
    				LOG.info("There are many missing blocks in a same stripe!!!!");   
    				return false;
    			} else {
    				tmp = i;
    			}
    		}
    	}
    	return true;
    }
  }
  
  /**
   * Periodically 
   */
  class FsShellMonitor implements Runnable {
	FsShell fsShell = null;
	String[] args = null;
	FsShellMonitor(String[] args){
		this.args = args;
		fsShell = new FsShell(conf);
	}
    public void run() {
      if(LOGDISPLAY)  LOG.info("------RaidNode.FsShellMonitor.run()-----");
      try {
			//fsShell.main(args);
			fsShell.run(args);
		} catch (Exception e) {
			e.printStackTrace();
		}
      if(LOGDISPLAY)  LOG.info("+++++RaidNode.FsShellMonitor.run()+++++");
    }
   }

  /**
   * Periodically checks to see which policies should be fired.
   */
  class TriggerMonitor implements Runnable {
    /**
     */
    public void run() {
      if(LOGDISPLAY)  LOG.info("04.1------RaidNode.TriggerMonitor.run()-----");
      while (running) {
        try {
          doProcess();
        } catch (Exception e) {
          LOG.error(StringUtils.stringifyException(e));
        } finally {
          LOG.info("Trigger thread continuing to run...");
        }
      }
      if(LOGDISPLAY)  LOG.info("04.1+++++RaidNode.TriggerMonitor.run()+++++");
    }


    /**
     * Keep processing policies.
     * If the config file has changed, then reload config file and start afresh.
     */
    private void doProcess() throws IOException, InterruptedException {
    	if(LOGDISPLAY)  LOG.info("05.1------RaidNode.TriggerMonitor.doProcess()-----");
    	PolicyList.CompareByPath lexi = new PolicyList.CompareByPath();

      long prevExec = 0;
      DistRaid dr = null;
      while (running) {
    	  if(LOGDISPLAY)  LOG.info("===================================================");
        boolean reload = configMgr.reloadConfigsIfNecessary();
        while(!reload && now() < prevExec + configMgr.getPeriodicity()){
          Thread.sleep(SLEEP_TIME);
          reload = configMgr.reloadConfigsIfNecessary();
        }

        prevExec = now();
        
        // activate all categories
        Collection<PolicyList> all = configMgr.getAllPolicies();
        
        // sort all policies by reverse lexicographical order. This is needed
        // to make the nearest policy take precedence.
        PolicyList[] sorted = all.toArray(new PolicyList[all.size()]);
        Arrays.sort(sorted, lexi);

        if (!isRaidLocal) {
          dr = new DistRaid(conf);
        }
        // paths we have processed so far
        List<String> processed = new LinkedList<String>();
        
        for (PolicyList category : sorted) {
          for (PolicyInfo info: category.getAll()) {

            long modTimePeriod = 0;
            short srcReplication = 0;
            String str = info.getProperty("modTimePeriod");
            if (str != null) {
               modTimePeriod = Long.parseLong(info.getProperty("modTimePeriod")); 
            }
            str = info.getProperty("srcReplication");
            if (str != null) {
               srcReplication = Short.parseShort(info.getProperty("srcReplication")); 
            }

            LOG.info("Triggering Policy Filter " + info.getName() +
                     " " + info.getSrcPath());
            List<FileStatus> filteredPaths = null;
            if(LOGDISPLAY1)  LOG.info("------info.getSrcPath() == "+info.getSrcPath()+"-----");
            if(LOGDISPLAY1)  LOG.info("------ getDestinationPath(conf, info) == "+ getDestinationPath(conf, info) +"-----");
            
            try { 
              filteredPaths = selectFiles(conf, info.getSrcPath(), 
                                          getDestinationPath(conf, info),
                                          modTimePeriod,
                                          srcReplication,
                                          prevExec);
              if(queue.isEmpty() != true){
              	if(LOGDISPLAY)  LOG.info("queue is not empty!!!");
              	if(LOGDISPLAY)  LOG.info("queue.size() = "+queue.size());
              	qqueue.put(queue);
              } else {
              	if(LOGDISPLAY)  LOG.info("queue is empty!!!");
              }
              
            } catch (Exception e) {
              LOG.info("Exception while invoking filter on policy " + info.getName() +
                       " srcPath " + info.getSrcPath() + 
                       " exception " + StringUtils.stringifyException(e));
              continue;
            }
            if(LOGDISPLAY1)  LOG.info("info.toString() == "+info.toString());
            
            if(filteredPaths == null){
            	if(LOGDISPLAY1)  LOG.info("------filteredPaths == null-----");
            }
            if(filteredPaths != null && filteredPaths.size() == 0){
            	if(LOGDISPLAY1)  LOG.info("------filteredPaths.size() == 0-----");
            }
            
            if (filteredPaths == null || filteredPaths.size() == 0) {
              LOG.info("No filtered paths for policy " + info.getName());
               continue;
            }

            // If any of the filtered path has already been accepted 
            // by a previous policy, then skip it.
            for (Iterator<FileStatus> iter = filteredPaths.iterator(); iter.hasNext();) {
              String fs = iter.next().getPath().toString() + "/";
              for (String p : processed) {
                if (p.startsWith(fs)) {
                  iter.remove();
                  break;
                }
              }
            }
            
            // Apply the action on accepted paths
            LOG.info("Triggering Policy Action " + info.getName());
            try {
            	if (isRaidLocal){
            	  doRaid(conf, info, filteredPaths);
            	}
            	else{
            	  //add paths for distributed raiding
            	  dr.addRaidPaths(info, filteredPaths);
            	}
            } catch (Exception e) {
              LOG.info("Exception while invoking action on policy " + info.getName() +
                       " srcPath " + info.getSrcPath() + 
                       " exception " + StringUtils.stringifyException(e));
              continue;
            }

            // add these paths to processed paths
            for (Iterator<FileStatus> iter = filteredPaths.iterator(); iter.hasNext();) {
              String p = iter.next().getPath().toString() + "/";
              processed.add(p);
            }
          }
        }
        processed.clear(); // free up memory references before yielding

        //do the distributed raiding
        if (!isRaidLocal) {
          dr.doDistRaid();
        } 
      }
      if(LOGDISPLAY)  LOG.info("05.1+++++RaidNode.TriggerMonitor.doProcess()+++++");
    }    
  }

  /**
   * Returns the policy that matches the specified path.
   * The method below finds the first policy that matches an input path. Since different 
   * policies with different purposes and destinations might be associated with the same input
   * path, we should be skeptical about the places using the method and we should try to change
   * the code to avoid it.
   */
  private PolicyInfo findMatchingPolicy(Path inpath) throws IOException {
    PolicyList.CompareByPath lexi = new PolicyList.CompareByPath();
    Collection<PolicyList> all = configMgr.getAllPolicies();
        
    // sort all policies by reverse lexicographical order. This is needed
    // to make the nearest policy take precedence.
    PolicyList[] sorted = all.toArray(new PolicyList[all.size()]);
    Arrays.sort(sorted, lexi);

    // loop through all categories of policies.
    for (PolicyList category : sorted) {
      PolicyInfo first = category.getAll().iterator().next();
      if (first != null) {
        Path[] srcPaths = first.getSrcPathExpanded(); // input src paths unglobbed
        if (srcPaths == null) {
          continue;
        }

        for (Path src: srcPaths) {
          if (inpath.toString().startsWith(src.toString())) {
            // if the srcpath is a prefix of the specified path
            // we have a match! 
            return first;
          }
        }
      }
    }
    return null; // no matching policies
  }

  
  static private Path getOriginalParityFile(Path destPathPrefix, Path srcPath) {
    return new Path(destPathPrefix, makeRelative(srcPath));
  }
  
  private static class ParityFilePair {
    private Path path;
    private FileSystem fs;
    
    public ParityFilePair( Path path, FileSystem fs) {
      this.path = path;
      this.fs = fs;
    }
    
    public Path getPath() {
      return this.path;
    }
    
    public FileSystem getFileSystem() {
      return this.fs;
    }
    
  }
  
  
  /**
   * Returns the Path to the parity file of a given file
   * 
   * @param destPathPrefix Destination prefix defined by some policy
   * @param srcPath Path to the original source file
   * @param create Boolean value telling whether a new parity file should be created
   * @return Path object representing the parity file of the source
   * @throws IOException
   */
  static private ParityFilePair getParityFile(Path destPathPrefix, Path srcPath, Configuration conf) throws IOException {
    Path srcParent = srcPath.getParent();

    FileSystem fsDest = destPathPrefix.getFileSystem(conf);
    FileSystem fsSrc = srcPath.getFileSystem(conf);
    
    if (!fsSrc.exists(srcPath)){
      return null;
    }
    FileStatus srcStatus = fsSrc.getFileStatus(srcPath);
    
    Path outDir = destPathPrefix;
    if (srcParent != null) {
      if (srcParent.getParent() == null) {
        outDir = destPathPrefix;
      } else {
        outDir = new Path(destPathPrefix, makeRelative(srcParent));
      }
    }

    
    //CASE 1: CHECK HAR - Must be checked first because har is created after
    // parity file and returning the parity file could result in error while
    // reading it.
    Path outPath =  getOriginalParityFile(destPathPrefix, srcPath);
    String harDirName = srcParent.getName() + HAR_SUFFIX; 
    Path HarPath = new Path(outDir,harDirName);
    if (fsDest.exists(HarPath)) {  
      URI HarPathUri = HarPath.toUri();
      Path inHarPath = new Path("har://",HarPathUri.getPath()+"/"+outPath.toUri().getPath());
      FileSystem fsHar = new HarFileSystem(fsDest);
      fsHar.initialize(inHarPath.toUri(), conf);
      if (fsHar.exists(inHarPath)) {
        FileStatus inHar = fsHar.getFileStatus(inHarPath);
        if (inHar.getModificationTime() == srcStatus.getModificationTime()) {
          return new ParityFilePair(inHarPath,fsHar);
        }
      }
    }
    
    //CASE 2: CHECK PARITY
    if (fsDest.exists(outPath)) {
      FileStatus outHar = fsDest.getFileStatus(outPath);
      if (outHar.getModificationTime() == srcStatus.getModificationTime()) {
        return new ParityFilePair(outPath,fsDest);
      }
    }

    return null; // NULL if no parity file
  }
  
  private ParityFilePair getParityFile(Path destPathPrefix, Path srcPath) throws IOException {
	  
	  return getParityFile(destPathPrefix, srcPath, conf);
	  
  }
  
 /**
  * Returns a list of pathnames that needs raiding.
  */
  private List<FileStatus> selectFiles(Configuration conf, Path p, String destPrefix,
                                       long modTimePeriod, short srcReplication, long now) throws IOException {
	  if(LOGDISPLAY1)  LOG.info("06.1------RaidNode.selectFiles() -----");
    List<FileStatus> returnSet = new LinkedList<FileStatus>();

    // expand destination prefix path
    Path destp = new Path(destPrefix.trim());
    FileSystem fs = FileSystem.get(destp.toUri(), conf);
    destp = destp.makeQualified(fs);
    String destpstr = destp.toString();
    if (!destpstr.endsWith(Path.SEPARATOR)) {
      destpstr += Path.SEPARATOR;
    }
    
    fs = p.getFileSystem(conf);
    if(LOGDISPLAY1)  LOG.info("------fs.getName() == "+fs.getName()+"-----");
    if(LOGDISPLAY1)  LOG.info("------fs.getUri() == "+fs.getUri()+"-----");
    FileStatus[] gpaths = fs.globStatus(p);
    if(gpaths == null)
    	if(LOGDISPLAY1)  LOG.info("------gpaths == null-----");
    
    	if(LOGDISPLAY1)  LOG.info("------gpaths.length == "+gpaths.length+"-----");
    	
    	if(LOGDISPLAY1)  LOG.info("------gpaths[0].getPath() == "+gpaths[0].getPath()+"-----");
    	
    	if(LOGDISPLAY1)  LOG.info("------gpaths[0].toString() == "+gpaths[0].toString()+"-----");
    if (gpaths != null){
      for (FileStatus onepath: gpaths) {
        String pathstr = onepath.getPath().makeQualified(fs).toString();
        if (!pathstr.endsWith(Path.SEPARATOR)) {
          pathstr += Path.SEPARATOR; 
        }
        if (pathstr.startsWith(destpstr) || destpstr.startsWith(pathstr)) {
          LOG.info("Skipping source " + pathstr + 
                   " because it conflicts with raid directory " + destpstr);
        } else {
          recurse(fs, conf, destp, onepath, returnSet, modTimePeriod, srcReplication, now);
        }
      }
    }
    if(LOGDISPLAY1)  LOG.info("06.1+++++RaidNode.selectFiles()+++++");
    return returnSet;
  }

  /**
   * Pick files that need to be RAIDed.
   */
  private void recurse(FileSystem srcFs,
                       Configuration conf,
                       Path destPathPrefix,
                       FileStatus src,
                       List<FileStatus> accept,
                       long modTimePeriod, 
                       short srcReplication, 
                       long now) throws IOException {
	  if(LOGDISPLAY)  LOG.info("07.1------RaidNode.recurse() -----");
	ArrayList<Integer> missBlocks = null;
	Map<FileStatus,ArrayList<Integer>> recoveredFileToMissBlocks= null;
    Path path = src.getPath();
    FileStatus[] files = null;
    try {
      files = srcFs.listStatus(path);
    } catch (java.io.FileNotFoundException e) {
      // ignore error because the file could have been deleted by an user
      LOG.info("FileNotFound " + path + " " + StringUtils.stringifyException(e));
    } catch (IOException e) {
      throw e;
    }
    if(LOGDISPLAY1)  LOG.info("------src.getPath() == "+src.getPath()+"-----");
    if(LOGDISPLAY1)  LOG.info("------files.length == "+files.length+"-----");
    if(LOGDISPLAY1){
	    for(FileStatus file:files){
	    	LOG.info("------file.getPath() == "+file.getPath()+"-----");
	    }
    }
    
    // If the modTime of the raid file is later than the modtime of the
    // src file and the src file has not been modified
    // recently, then that file is a candidate for RAID.
    
    if (!src.isDir()) {      // a file
    	if(LOGDISPLAY)  LOG.info("------src.getPath() == "+src.getPath()+"-----");
      // if the source file has fewer than or equal to 2 blocks, then no need to RAID
    	if(LOGDISPLAY)  LOG.info("------src.getBlockSize() == "+src.getBlockSize()+"-----");
    	
    	if(LOGDISPLAY)  LOG.info("------src.getLen() == "+src.getLen()+"-----");
      long blockSize = src.getBlockSize();
      
      if (2 * blockSize >= src.getLen()) {
        return;
      }
      if(LOGDISPLAY)  LOG.info("------src.getPath().toUri().getPath() == "+src.getPath().toUri().getPath()+"-----");   
      
      
      
      
      // check if destination path already exists. If it does and it's modification time
      // does not match the modTime of the source file, then recalculate RAID
      boolean add = false;
      try {
        ParityFilePair ppair = getParityFile(destPathPrefix, path);
        if(ppair != null){
        	if(LOGDISPLAY)  LOG.info("------ppair == "+ppair.getPath()+"-----");
        }
        long tmp = src.getModificationTime() + modTimePeriod;
        if(LOGDISPLAY)  LOG.info("------src.getModificationTime() + modTimePeriod == "+tmp+"-----");
        if(LOGDISPLAY1)  LOG.info("------src.getModificationTime()  == "+ src.getModificationTime() +"-----");
        if(LOGDISPLAY1)  LOG.info("------modTimePeriod == "+ modTimePeriod +"-----");
        if(LOGDISPLAY)  LOG.info("------now == "+now+"-----");
        if (ppair == null && src.getModificationTime() + modTimePeriod < now) {
          add = true;
         }
      } catch (java.io.FileNotFoundException e) {
        add = true; // destination file does not exist
      }
      if(LOGDISPLAY)  LOG.info("------add == "+add+"-----");
      if (add) {
        accept.add(src);
      }
      //===================================
      String args[] = new String[4];
      args[0] = src.getPath().toUri().getPath();
      args[1] = "-files";
      args[2] = "-blocks";
      args[3] = "-locations";
      try {
		missBlocks = checkMissBlock(args);
	} catch (Exception e) {
		e.printStackTrace();
	}
      if(missBlocks != null && missBlocks.isEmpty() == false){
    	  for(Iterator<Integer> it = missBlocks.iterator();it.hasNext();){
    		  if(LOGDISPLAY)  LOG.info("------it.next() == "+ it.next() +"-----");
    	  }
    	  recoveredFileToMissBlocks = new HashMap<FileStatus,ArrayList<Integer>>();
    	  recoveredFileToMissBlocks.put(src, missBlocks);
    	  if(this.queue.contains(recoveredFileToMissBlocks) != true){
    		  this.queue.add(recoveredFileToMissBlocks);
    	  }
    	  	
    	  
      }
      //===================================
      return;

    } else if (files != null) {
      for (FileStatus one:files) {
        if (!one.getPath().getName().endsWith(HAR_SUFFIX)){
          recurse(srcFs, conf, destPathPrefix, one, accept, modTimePeriod, srcReplication, now);
        }
      }
    }
    if(LOGDISPLAY)  LOG.info("07.1+++++RaidNode.recurse()+++++");
  }


  /**
   * RAID a list of files.
   */
  void doRaid(Configuration conf, PolicyInfo info, List<FileStatus> paths)
      throws IOException {
    int targetRepl = Integer.parseInt(info.getProperty("targetReplication"));
    int metaRepl = Integer.parseInt(info.getProperty("metaReplication"));
    int stripeLength = getStripeLength(conf, info);
    String destPrefix = getDestinationPath(conf, info);
    String simulate = info.getProperty("simulate");
    boolean doSimulate = simulate == null ? false : Boolean
        .parseBoolean(simulate);

    Statistics statistics = new Statistics();
    int count = 0;

    Path p = new Path(destPrefix.trim());
    FileSystem fs = FileSystem.get(p.toUri(), conf);
    p = p.makeQualified(fs);

    for (FileStatus s : paths) {
      doRaid(conf, s, p, statistics, null, doSimulate, targetRepl, metaRepl,
          stripeLength);
      if (count % 1000 == 0) {
        LOG.info("RAID statistics " + statistics.toString());
      }
      count++;
    }
    LOG.info("RAID statistics " + statistics.toString());
  }

  
  /**
   * RAID an individual file
   */

  static public void doRaid(Configuration conf, PolicyInfo info,
      FileStatus src, Statistics statistics, Reporter reporter) throws IOException {
    int targetRepl = Integer.parseInt(info.getProperty("targetReplication"));
    int metaRepl = Integer.parseInt(info.getProperty("metaReplication"));
    int stripeLength = getStripeLength(conf, info);
    String destPrefix = getDestinationPath(conf, info);
    String simulate = info.getProperty("simulate");
    boolean doSimulate = simulate == null ? false : Boolean
        .parseBoolean(simulate);

    int count = 0;

    Path p = new Path(destPrefix.trim());
    FileSystem fs = FileSystem.get(p.toUri(), conf);
    p = p.makeQualified(fs);

    doRaid(conf, src, p, statistics, reporter, doSimulate, targetRepl, metaRepl,
        stripeLength);
  }
  
  
  /**
   * RAID an individual file
   */
  static private void doRaid(Configuration conf, FileStatus stat, Path destPath,
                      Statistics statistics, Reporter reporter, boolean doSimulate,
                      int targetRepl, int metaRepl, int stripeLength) 
    throws IOException {
    Path p = stat.getPath();
    FileSystem srcFs = p.getFileSystem(conf);

    // extract block locations from File system
    BlockLocation[] locations = srcFs.getFileBlockLocations(stat, 0, stat.getLen());
    
    // if the file has fewer than 2 blocks, then nothing to do
    if (locations.length <= 2) {
      return;
    }

    // add up the raw disk space occupied by this file
    long diskSpace = 0;
    for (BlockLocation l: locations) {
      diskSpace += (l.getLength() * stat.getReplication());
    }
    statistics.numProcessedBlocks += locations.length;
    statistics.processedSize += diskSpace;

    // generate parity file
    generateParityFile(conf, stat, reporter, srcFs, destPath, locations, metaRepl, stripeLength);

    // reduce the replication factor of the source file
    if (!doSimulate) {
      if (srcFs.setReplication(p, (short)targetRepl) == false) {
        LOG.info("Error in reducing relication factor of file " + p + " to " + targetRepl);
        statistics.remainingSize += diskSpace;  // no change in disk space usage
        return;
      }
    }

    diskSpace = 0;
    for (BlockLocation l: locations) {
      diskSpace += (l.getLength() * targetRepl);
    }
    statistics.remainingSize += diskSpace;

    // the metafile will have this many number of blocks
    int numMeta = locations.length / stripeLength;
    if (locations.length % stripeLength != 0) {
      numMeta++;
    }

    // we create numMeta for every file. This metablock has metaRepl # replicas.
    // the last block of the metafile might not be completely filled up, but we
    // ignore that for now.
    statistics.numMetaBlocks += (numMeta * metaRepl);
    statistics.metaSize += (numMeta * metaRepl * stat.getBlockSize());
  }

  /**
   * Create the parity file.
   */
  static private void generateParityFile(Configuration conf, FileStatus stat,
                                  Reporter reporter,
                                  FileSystem inFs,
                                  Path destPathPrefix, BlockLocation[] locations,
                                  int metaRepl, int stripeLength) throws IOException {

    // two buffers for generating parity
    Random rand = new Random();
    int bufSize = 5 * 1024 * 1024; // 5 MB
    byte[] bufs = new byte[bufSize];
    byte[] xor = new byte[bufSize];

    Path inpath = stat.getPath();
    long blockSize = stat.getBlockSize();
    long fileSize = stat.getLen();

    // create output tmp path
    Path outpath =  getOriginalParityFile(destPathPrefix, inpath);
    FileSystem outFs = outpath.getFileSystem(conf);
   
    Path tmppath =  new Path(conf.get("fs.raid.tmpdir", "/tmp/raid") + 
                             outpath.toUri().getPath() + "." + 
                             rand.nextLong() + ".tmp");

    // if the parity file is already upto-date, then nothing to do
    try {
      FileStatus stmp = outFs.getFileStatus(outpath);
      if (stmp.getModificationTime() == stat.getModificationTime()) {
        LOG.info("Parity file for " + inpath + "(" + locations.length + ") is " + outpath +
                 " already upto-date. Nothing more to do.");
        return;
      }
    } catch (IOException e) {
      // ignore errors because the raid file might not exist yet.
    } 

    LOG.info("Parity file for " + inpath + "(" + locations.length + ") is " + outpath);
    FSDataOutputStream out = outFs.create(tmppath, 
                                          true, 
                                          conf.getInt("io.file.buffer.size", 64 * 1024), 
                                          (short)metaRepl, 
                                          blockSize);
    
    try {

      // loop once for every stripe length
      for (int startBlock = 0; startBlock < locations.length;) {

        // report progress to Map-reduce framework
        if (reporter != null) {
          reporter.progress();
        }
        int blocksLeft = locations.length - startBlock;
        int stripe = Math.min(stripeLength, blocksLeft);
        LOG.info(" startBlock " + startBlock + " stripe " + stripe);

        // open a new file descriptor for each block in this stripe.
        // make each fd point to the beginning of each block in this stripe.
        FSDataInputStream[] ins = new FSDataInputStream[stripe];
        for (int i = 0; i < stripe; i++) {
          ins[i] = inFs.open(inpath, bufSize);
          ins[i].seek(blockSize * (startBlock + i));
        }

        generateParity(ins,out,blockSize,bufs,xor, reporter);
        
        // close input file handles
        for (int i = 0; i < ins.length; i++) {
          ins[i].close();
        }

        // increment startBlock to point to the first block to be processed
        // in the next iteration
        startBlock += stripe;
      }
      out.close();
      out = null;

      // delete destination if exists
      if (outFs.exists(outpath)){
        outFs.delete(outpath, false);
      }
      // rename tmppath to the real parity filename
      outFs.mkdirs(outpath.getParent());
      if (!outFs.rename(tmppath, outpath)) {
        String msg = "Unable to rename tmp file " + tmppath + " to " + outpath;
        LOG.warn(msg);
        throw new IOException (msg);
      }
    } finally {
      // remove the tmp file if it still exists
      outFs.delete(tmppath, false);  
    }

    // set the modification time of the RAID file. This is done so that the modTime of the
    // RAID file reflects that contents of the source file that it has RAIDed. This should
    // also work for files that are being appended to. This is necessary because the time on
    // on the destination namenode may not be synchronised with the timestamp of the 
    // source namenode.
    outFs.setTimes(outpath, stat.getModificationTime(), -1);

    FileStatus outstat = outFs.getFileStatus(outpath);
    LOG.info("Source file " + inpath + " of size " + fileSize +
             " Parity file " + outpath + " of size " + outstat.getLen() +
             " src mtime " + stat.getModificationTime()  +
             " parity mtime " + outstat.getModificationTime());
  }

  private static int readInputUntilEnd(FSDataInputStream ins, byte[] bufs, int toRead) 
      throws IOException {

    int tread = 0;
    
    while (tread < toRead) {
      int read = ins.read(bufs, tread, toRead - tread);
      if (read == -1) {
        return tread;
      } else {
        tread += read;
      }
    }
    
    return tread;
  }
  
  private static void generateParity(FSDataInputStream[] ins, FSDataOutputStream fout, 
      long parityBlockSize, byte[] bufs, byte[] xor, Reporter reporter) throws IOException {
    
    int bufSize;
    if ((bufs == null) || (bufs.length == 0)){
      bufSize = 5 * 1024 * 1024; // 5 MB
      bufs = new byte[bufSize];
    } else {
      bufSize = bufs.length;
    }
    if ((xor == null) || (xor.length != bufs.length)){
      xor = new byte[bufSize];
    }

    int xorlen = 0;
      
    // this loop processes all good blocks in selected stripe
    long remaining = parityBlockSize;
    
    while (remaining > 0) {
      int toRead = (int)Math.min(remaining, bufSize);

      if (ins.length > 0) {
        xorlen = readInputUntilEnd(ins[0], xor, toRead);
      }

      // read all remaining blocks and xor them into the buffer
      for (int i = 1; i < ins.length; i++) {

        // report progress to Map-reduce framework
        if (reporter != null) {
          reporter.progress();
        }
        
        int actualRead = readInputUntilEnd(ins[i], bufs, toRead);
        
        int j;
        int xorlimit = (int) Math.min(xorlen,actualRead);
        for (j = 0; j < xorlimit; j++) {
          xor[j] ^= bufs[j];
        }
        if ( actualRead > xorlen ){
          for (; j < actualRead; j++) {
            xor[j] = bufs[j];
          }
          xorlen = actualRead;
        }
        
      }

      if (xorlen < toRead) {
        Arrays.fill(bufs, xorlen, toRead, (byte) 0);
      }
      
      
      
      // write this to the tmp file
      fout.write(xor, 0, toRead);
      remaining -= toRead;
    }
  
  }
  
  /**
   * Extract a good block from the parity block. This assumes that the corruption
   * is in the main file and the parity file is always good.
   */
  public static Path unRaid(Configuration conf, Path srcPath, Path destPathPrefix, 
                            int stripeLength, long corruptOffset) throws IOException {

    // Test if parity file exists
    ParityFilePair ppair = getParityFile(destPathPrefix, srcPath, conf); 
    if (ppair == null) {
      return null;
    }
    // open parity file fast to prevent getting a new, wrong, version
    Path parityFile = ppair.getPath();
    FileSystem parityFs = ppair.getFileSystem();
    FSDataInputStream parityInputStream = parityFs.open(parityFile);

    // extract block locations, size etc from source file
    Random rand = new Random();
    FileSystem srcFs = srcPath.getFileSystem(conf);
    FileStatus srcStat = srcFs.getFileStatus(srcPath);
    long blockSize = srcStat.getBlockSize();
    long fileSize = srcStat.getLen();

    // find the stripe number where the corrupted offset lies
    long snum = corruptOffset / (stripeLength * blockSize);
    long startOffset = snum * stripeLength * blockSize;
    long corruptBlockInStripe = (corruptOffset - startOffset)/blockSize;
    long corruptBlockSize = Math.min(blockSize, fileSize - startOffset);

    LOG.info("Start offset of relevent stripe = " + startOffset +
             " corruptBlockInStripe " + corruptBlockInStripe);

    // seek to the appropriate offset in parity file.
    LOG.info("Parity file for " + srcPath + " is " + parityFile);
    parityInputStream.seek(snum * blockSize);
    LOG.info("Parity file " + parityFile +
             " seeking to relevent block at offset " + 
             parityInputStream.getPos());
    

    // open file descriptors to read all good blocks of the file
    FSDataInputStream[] instmp = new FSDataInputStream[stripeLength];
    int  numLength = 0;
    for (int i = 0; i < stripeLength; i++) {
      if (i == corruptBlockInStripe) {
        continue;  // do not open corrupt block
      }
      if (startOffset + i * blockSize >= fileSize) {
        LOG.info("Stop offset of relevent stripe = " + 
                  startOffset + i * blockSize);
        break;
      }
      instmp[numLength] = srcFs.open(srcPath);
      instmp[numLength].seek(startOffset + i * blockSize);
      numLength++;
    }

    // create array of inputstream, allocate one extra slot for 
    // parity file. numLength could be smaller than stripeLength
    // if we are processing the last partial stripe on a file.
    numLength += 1;
    FSDataInputStream[] ins = new FSDataInputStream[numLength];
    for (int i = 0; i < numLength-1; i++) {
      ins[i] = instmp[i];
    }
    ins[numLength-1] = parityInputStream;
    LOG.info("Decompose a total of " + numLength + " blocks.");

    // create a temporary filename in the source filesystem
    // do not overwrite an existing tmp file. Make it fail for now.
    // We need to generate a unique name for this tmp file later on.
    Path tmpFile = null;
    FSDataOutputStream fout = null;
    FileSystem destFs = destPathPrefix.getFileSystem(conf);
   
    int retry = 5;
    try {
      tmpFile = new Path("/tmp/raid/" + rand.nextInt());
      fout = destFs.create(tmpFile, false);
      
    } catch (IOException e) {
      if (retry-- <= 0) {
        LOG.info("Unable to create temporary file " + tmpFile +
                 " Aborting....");
        e.printStackTrace();
        throw e; 
      }
      LOG.info("Unable to create temporary file " + tmpFile +
               "Retrying....");
    }
    LOG.info("Created recovered block file " + tmpFile);
    
    // buffers for generating parity bits
    int bufSize = 5 * 1024 * 1024; // 5 MB
    byte[] bufs = new byte[bufSize];
    byte[] xor = new byte[bufSize];
   
    generateParity(ins,fout,corruptBlockSize,bufs,xor,null);
    
    // close all files
    fout.close();
    for (int i = 0; i < ins.length; i++) {
      ins[i].close();
    }

    // Now, reopen the source file and the recovered block file
    // and copy all relevant data to new file
    final Path recoveryDestination = 
      new Path(conf.get("fs.raid.tmpdir", "/tmp/raid"));
    final Path recoveredPrefix = 
      destFs.makeQualified(new Path(recoveryDestination, makeRelative(srcPath)));
    final Path recoveredPath = 
      new Path(recoveredPrefix + "." + rand.nextLong() + ".recovered");
    LOG.info("Creating recovered file " + recoveredPath);

    FSDataInputStream sin = srcFs.open(srcPath);
    FSDataOutputStream out = destFs.create(recoveredPath, false, 
                                             conf.getInt("io.file.buffer.size", 64 * 1024),
                                             srcStat.getReplication(), 
                                             srcStat.getBlockSize());

    FSDataInputStream bin = destFs.open(tmpFile);
    long recoveredSize = 0;

    // copy all the good blocks (upto the corruption)
    // from source file to output file
    long remaining = corruptOffset / blockSize * blockSize;
    while (remaining > 0) {
      int toRead = (int)Math.min(remaining, bufSize);
      sin.readFully(bufs, 0, toRead);
      out.write(bufs, 0, toRead);
      remaining -= toRead;
      recoveredSize += toRead;
    }
    LOG.info("Copied upto " + recoveredSize + " from src file. ");

    // copy recovered block to output file
    remaining = corruptBlockSize;
    while (recoveredSize < fileSize &&
           remaining > 0) {
      int toRead = (int)Math.min(remaining, bufSize);
      bin.readFully(bufs, 0, toRead);
      out.write(bufs, 0, toRead);
      remaining -= toRead;
      recoveredSize += toRead;
    }
    LOG.info("Copied upto " + recoveredSize + " from recovered-block file. ");

    // skip bad block in src file
    if (recoveredSize < fileSize) {
      sin.seek(sin.getPos() + corruptBlockSize); 
    }

    // copy remaining good data from src file to output file
    while (recoveredSize < fileSize) {
      int toRead = (int)Math.min(fileSize - recoveredSize, bufSize);
      sin.readFully(bufs, 0, toRead);
      out.write(bufs, 0, toRead);
      recoveredSize += toRead;
    }
    out.close();
    LOG.info("Completed writing " + recoveredSize + " bytes into " +
             recoveredPath);
              
    sin.close();
    bin.close();

    // delete the temporary block file that was created.
    LOG.info("Deletting temporary file " + tmpFile);
    LOG.info("destFs.getName() " + destFs.getName());
    //LOG.info("destFs.getName() " + destFs.);
    
    
    
    
    destFs.delete(tmpFile, false);
    LOG.info("Deleted temporary file " + tmpFile);

    // copy the meta information from source path to the newly created
    // recovered path
    try {
       copyMetaInformation(destFs, srcStat, recoveredPath);
    } catch (Exception exc) {
      LOG.info("Didn't manage to copy meta information because of " + exc + 
               " Ignoring...");
    }
       
    return recoveredPath;
  }

  /**
   * Periodically delete orphaned parity files.
   */
  class PurgeMonitor implements Runnable {
    /**
     */
    public void run() {
      while (running) {
        try {
          doPurge();
        } catch (Exception e) {
          LOG.error(StringUtils.stringifyException(e));
        } finally {
          LOG.info("Purge parity files thread continuing to run...");
        }
      }
    }

    /**
     * Delete orphaned files. The reason this is done by a separate thread 
     * is to not burden the TriggerMonitor with scanning the 
     * destination directories.
     */
    private void doPurge() throws IOException, InterruptedException {
      PolicyList.CompareByPath lexi = new PolicyList.CompareByPath();

      long prevExec = 0;
      while (running) {

        // The config may be reloaded by the TriggerMonitor. 
        // This thread uses whatever config is currently active.
        while(now() < prevExec + configMgr.getPeriodicity()){
          Thread.sleep(SLEEP_TIME);
        }

        prevExec = now();
        
        // fetch all categories
        Collection<PolicyList> all = configMgr.getAllPolicies();
        
        // sort all policies by reverse lexicographical order. This is 
        // needed to make the nearest policy take precedence.
        PolicyList[] sorted = all.toArray(new PolicyList[all.size()]);
        Arrays.sort(sorted, lexi);

        // paths we have processed so far
        Set<Path> processed = new HashSet<Path>();
        
        for (PolicyList category : sorted) {
          for (PolicyInfo info: category.getAll()) {

            try {
              // expand destination prefix path
              String destinationPrefix = getDestinationPath(conf, info);
              Path destPref = new Path(destinationPrefix.trim());
              FileSystem destFs = FileSystem.get(destPref.toUri(), conf);
              destPref = destPref.makeQualified(destFs);

              //get srcPaths
              Path[] srcPaths = info.getSrcPathExpanded();
              
              if ( srcPaths != null ){
                for (Path srcPath: srcPaths) {
                  // expand destination prefix
                  Path destPath = getOriginalParityFile(destPref, srcPath);

                  // if this destination path has already been processed as part
                  // of another policy, then nothing more to do
                  if (processed.contains(destPath)) {
                    LOG.info("Obsolete parity files for policy " + 
                            info.getName() + " has already been procesed.");
                    continue;
                  }

                  FileSystem srcFs = info.getSrcPath().getFileSystem(conf);
                  FileStatus stat = null;
                  try {
                    stat = destFs.getFileStatus(destPath);
                  } catch (FileNotFoundException e) {
                    // do nothing, leave stat = null;
                  }
                  if (stat != null) {
                    LOG.info("Purging obsolete parity files for policy " + 
                              info.getName() + " " + destPath);
                    recursePurge(srcFs, destFs, destPref.toUri().getPath(), stat);
                  }

                  // this destination path has already been processed
                  processed.add(destPath);

                }
              }

            } catch (Exception e) {
              LOG.warn("Ignoring Exception while processing policy " + 
                       info.getName() + " " + 
                       StringUtils.stringifyException(e));
            }
          }
        }
      }
    }

    /**
     * The destPrefix is the absolute pathname of the destinationPath
     * specified in the policy (without the host:port)
     */ 
    private void recursePurge(FileSystem srcFs, FileSystem destFs,
                              String destPrefix, FileStatus dest) 
      throws IOException {

      Path destPath = dest.getPath(); // pathname, no host:port
      String destStr = destPath.toUri().getPath();
      LOG.debug("Checking " + destPath + " prefix " + destPrefix);

      // Verify if it is a har file
      if (destStr.endsWith(HAR_SUFFIX)) {
        String destParentStr = destPath.getParent().toUri().getPath();
        String src = destParentStr.replaceFirst(destPrefix, "");
        Path srcPath = new Path(src);
        if (!srcFs.exists(srcPath)) {
          destFs.delete(destPath, true);
        }
        return;
      }
      
      // Verify the destPrefix is a prefix of the destPath
      if (!destStr.startsWith(destPrefix)) {
        LOG.error("Destination path " + destStr + " should have " + 
                  destPrefix + " as its prefix.");
        return;
      }
      
      if (dest.isDir()) {
        FileStatus[] files = null;
        files = destFs.listStatus(destPath);
        if (files != null) {
          for (FileStatus one:files) {
            recursePurge(srcFs, destFs, destPrefix, one);
          }
        }
        files = destFs.listStatus(destPath);
        if (files == null || files.length == 0){
          boolean done = destFs.delete(destPath,true); // ideal is false, but
                  // DFSClient only deletes directories if it is recursive
          if (done) {
            LOG.info("Purged directory " + destPath );
          }
          else {
            LOG.info("Unable to purge directory " + destPath);
          }
        }
        return; // the code below does the file checking
      }
      
      String src = destStr.replaceFirst(destPrefix, "");
      
      Path srcPath = new Path(src);
      boolean should_delete = false;

      if (!srcFs.exists(srcPath)) {
        should_delete = true;
      } else {
        Path dstPath = (new Path(destPrefix.trim())).makeQualified(destFs);
        ParityFilePair ppair = getParityFile(dstPath,srcPath);
        if ( ppair == null || 
            !destFs.equals(ppair.getFileSystem()) ||
            !destPath.equals(ppair.getPath())) {
          should_delete = true;
        }
      }
      
      if (should_delete) {
        boolean done = destFs.delete(destPath, false);
        if (done) {
          LOG.info("Purged file " + destPath );
        } else {
          LOG.info("Unable to purge file " + destPath );
        }
      }
    } 
  }

  
  private void doHar() throws IOException, InterruptedException {
    
    PolicyList.CompareByPath lexi = new PolicyList.CompareByPath();

    long prevExec = 0;
    while (running) {

      // The config may be reloaded by the TriggerMonitor. 
      // This thread uses whatever config is currently active.
      while(now() < prevExec + configMgr.getPeriodicity()){
        Thread.sleep(SLEEP_TIME);
      }

      LOG.info("Started archive scan");
      prevExec = now();
      
      // fetch all categories
      Collection<PolicyList> all = configMgr.getAllPolicies();
            
      // sort all policies by reverse lexicographical order. This is 
      // needed to make the nearest policy take precedence.
      PolicyList[] sorted = all.toArray(new PolicyList[all.size()]);
      Arrays.sort(sorted, lexi);

      for (PolicyList category : sorted) {
        for (PolicyInfo info: category.getAll()) {
          String str = info.getProperty("time_before_har");
          String tmpHarPath = info.getProperty("har_tmp_dir");
          if (tmpHarPath == null) {
            tmpHarPath = "/tmp/raid_har";
          }
          if (str != null) {
            try {
              long cutoff = now() - ( Long.parseLong(str) * 24L * 3600000L );

              String destinationPrefix = getDestinationPath(conf, info);
              Path destPref = new Path(destinationPrefix.trim());
              FileSystem destFs = destPref.getFileSystem(conf); 
              destPref = destPref.makeQualified(destFs);

              //get srcPaths
              Path[] srcPaths = info.getSrcPathExpanded();
              
              if ( srcPaths != null ){
                for (Path srcPath: srcPaths) {
                  // expand destination prefix
                  Path destPath = getOriginalParityFile(destPref, srcPath);

                  FileStatus stat = null;
                  try {
                    stat = destFs.getFileStatus(destPath);
                  } catch (FileNotFoundException e) {
                    // do nothing, leave stat = null;
                  }
                  if (stat != null) {
                    LOG.info("Haring parity files for policy " + 
                        info.getName() + " " + destPath);
                    recurseHar(destFs, stat, destPref.toUri().getPath(),
                        srcPath.getFileSystem(conf), cutoff, tmpHarPath);
                  }
                }
              }
            } catch (Exception e) {
              LOG.warn("Ignoring Exception while processing policy " + 
                  info.getName() + " " + 
                  StringUtils.stringifyException(e));
            }
          }
        }
      }
    }
    return;
  }
  
  private void recurseHar(FileSystem destFs, FileStatus dest, String destPrefix,
      FileSystem srcFs, long cutoff, String tmpHarPath)
    throws IOException {

    if (!dest.isDir()) {
      return;
    }
    
    Path destPath = dest.getPath(); // pathname, no host:port
    String destStr = destPath.toUri().getPath();

    // Verify if it already contains a HAR directory
    if ( destFs.exists(new Path(destPath, destPath.getName()+HAR_SUFFIX)) ) {
      return;
    }

    FileStatus[] files = null;
    files = destFs.listStatus(destPath);
    boolean shouldHar = false;
    if (files != null) {
      shouldHar = files.length > 0;
      for (FileStatus one: files) {
        if (one.isDir()){
          recurseHar(destFs, one, destPrefix, srcFs, cutoff, tmpHarPath);
          shouldHar = false;
        } else if (one.getModificationTime() > cutoff ) {
          if (shouldHar) {
            LOG.info("Cannot archive " + destPath + 
                   " because " + one.getPath() + " was modified after cutoff");
            shouldHar = false;
          }
        }
      }
      
      if (shouldHar) {
        String src = destStr.replaceFirst(destPrefix, "");
        Path srcPath = new Path(src);
        FileStatus[] statuses = srcFs.listStatus(srcPath);
        Path destPathPrefix = new Path(destPrefix).makeQualified(destFs);
        if (statuses != null) {
          for (FileStatus status : statuses) {
            if (getParityFile(destPathPrefix, 
                              status.getPath().makeQualified(srcFs)) == null ) {
              LOG.info("Cannot archive " + destPath + 
                  " because it doesn't contain parity file for " +
                  status.getPath().makeQualified(srcFs) + " on destination " +
                  destPathPrefix);
              shouldHar = false;
              break;
            }
          }
        }
      }
      
    }
    if ( shouldHar ) {
      LOG.info("Archiving " + dest.getPath() + " to " + tmpHarPath );
      singleHar(destFs, dest, tmpHarPath);
    }
  } 

  
  private void singleHar(FileSystem destFs, FileStatus dest, String tmpHarPath) throws IOException {
    
    Random rand = new Random();
    Path root = new Path("/");
    Path qualifiedPath = dest.getPath().makeQualified(destFs);
    String harFileDst = qualifiedPath.getName() + HAR_SUFFIX;
    String harFileSrc = qualifiedPath.getName() + "-" + 
                                rand.nextLong() + "-" + HAR_SUFFIX;
    HadoopArchives har = new HadoopArchives(conf);
    String[] args = new String[6];
    args[0] = "-archiveName";
    args[1] = harFileSrc;
    args[2] = "-p"; 
    args[3] = root.makeQualified(destFs).toString();
    args[4] = qualifiedPath.toUri().getPath().substring(1);
    args[5] = tmpHarPath.toString();
    int ret = 0;
    try {
      ret = ToolRunner.run(har, args);
      if (ret == 0 && !destFs.rename(new Path(tmpHarPath+"/"+harFileSrc), 
                                     new Path(qualifiedPath, harFileDst))) {
        LOG.info("HAR rename didn't succeed from " + tmpHarPath+"/"+harFileSrc +
            " to " + qualifiedPath + "/" + harFileDst);
        ret = -2;
      }
    } catch (Exception exc) {
      throw new IOException("Error while creating archive " + ret, exc);
    }
    
    if (ret != 0){
      throw new IOException("Error while creating archive " + ret);
    }
    return;
  }
  
  /**
   * Periodically generates HAR files
   */
  class HarMonitor implements Runnable {

    public void run() {
      while (running) {
        try {
          doHar();
        } catch (Exception e) {
          LOG.error(StringUtils.stringifyException(e));
        } finally {
          LOG.info("Har parity files thread continuing to run...");
        }
      }
      LOG.info("Leaving Har thread.");
    }
    

  }  
  
  /**
   * If the config file has an entry for hdfs.raid.locations, then that overrides
   * destination path specified in the raid policy file
   */
  static private String getDestinationPath(Configuration conf, PolicyInfo info) {
    String locs = conf.get("hdfs.raid.locations"); 
    if (locs != null) {
      return locs;
    }
    locs = info.getDestinationPath();
    if (locs == null) {
      return DEFAULT_RAID_LOCATION;
    }
    return locs;
  }

  /**
   * If the config file has an entry for hdfs.raid.stripeLength, then use that
   * specified in the raid policy file
   */
  static private int getStripeLength(Configuration conf, PolicyInfo info)
    throws IOException {
    int len = conf.getInt("hdfs.raid.stripeLength", 0); 
    if (len != 0) {
      return len;
    }
    String str = info.getProperty("stripeLength");
    if (str == null) {
      String msg = "hdfs.raid.stripeLength is not defined." +
                   " Using a default " + DEFAULT_STRIPE_LENGTH;
      LOG.info(msg);
      return DEFAULT_STRIPE_LENGTH;
    }
    return Integer.parseInt(str);
  }

  /**
   * Copy the file owner, modtime, etc from srcPath to the recovered Path.
   * It is possiible that we might have to retrieve file persmissions,
   * quotas, etc too in future.
   */
  static private void copyMetaInformation(FileSystem fs, FileStatus stat, 
                                          Path recoveredPath) 
    throws IOException {
    fs.setOwner(recoveredPath, stat.getOwner(), stat.getGroup());
    fs.setPermission(recoveredPath, stat.getPermission());
    fs.setTimes(recoveredPath, stat.getModificationTime(), stat.getAccessTime());
  }

  /**
   * Returns current time.
   */
  static long now() {
    return System.currentTimeMillis();
  }

  /**                       
   * Make an absolute path relative by stripping the leading /
   */   
  static private Path makeRelative(Path path) {
    if (!path.isAbsolute()) {
      return path;
    }          
    String p = path.toUri().getPath();
    String relative = p.substring(1, p.length());
    return new Path(relative);
  } 

  private static void printUsage() {
    System.err.println("Usage: java RaidNode ");
  }

  private static StartupOption parseArguments(String args[]) {
    int argsLen = (args == null) ? 0 : args.length;
    StartupOption startOpt = StartupOption.REGULAR;
    String cmd = null;
    for(int i=0; i < argsLen; i++) {
      cmd = args[i]; // We have to parse command line args in future.
      if (cmd.equalsIgnoreCase("-log")) {
    	  System.setProperty("LOGDISPLAY", "true");
          LOGDISPLAY = true;
      } else if (cmd.equalsIgnoreCase("-log1")) {
    	  System.setProperty("LOGDISPLAY1", "true");
          LOGDISPLAY1 = true;
      } else if (cmd.equalsIgnoreCase("-log2")) {
    	  System.setProperty("LOGDISPLAY2", "true");
          LOGDISPLAY2 = true;
      } else if (cmd.equalsIgnoreCase("-log3")) {
  	      System.setProperty("LOGDISPLAY3", "true");
          LOGDISPLAY3 = true;
      } else if (cmd.equalsIgnoreCase("-log4")) {
  	      System.setProperty("LOGDISPLAY4", "true");
           LOGDISPLAY4 = true;
      }else if (cmd.equalsIgnoreCase("-log5")) {
		  System.setProperty("LOGDISPLAY5", "true");
	      LOGDISPLAY5 = true;
      } else if (cmd.equalsIgnoreCase("-log6")) {
	      System.setProperty("LOGDISPLAY6", "true");
          LOGDISPLAY6 = true;
	  } else if (cmd.equalsIgnoreCase("-log7")) {
		  System.setProperty("LOGDISPLAY7", "true");
	      LOGDISPLAY7 = true;
	  } else if (cmd.equalsIgnoreCase("-log8")) {
		  System.setProperty("LOGDISPLAY8", "true");
	      LOGDISPLAY8 = true;
	  } else if (cmd.equalsIgnoreCase("-log9")) {
		  System.setProperty("LOGDISPLAY9", "true");
	      LOGDISPLAY9 = true;	
	  } else if (cmd.equalsIgnoreCase("-log10")) {
		  System.setProperty("LOGDISPLAY10", "true");
	      LOGDISPLAY10 = true;	
      } else if (cmd.equalsIgnoreCase("-log0")) {
		  System.setProperty("LOGDISPLAY0", "true");
	      LOGDISPLAY0 = true;	
      } else if (cmd.equalsIgnoreCase("-logdebug")) {
    	  System.setProperty("LOGDEBUG", "true");
          LOGDEBUG = true;
      } 
    }
    return startOpt;
  }


  /**
   * Convert command line options to configuration parameters
   */
  private static void setStartupOption(Configuration conf, StartupOption opt) {
    conf.set("fs.raidnode.startup", opt.toString());
  }

  /**
   * Create an instance of the RaidNode 
   */
  public static RaidNode createRaidNode(String argv[],
                                        Configuration conf) throws IOException {
	LOG.info("01------RaidNode.createRaidNode()-----");
    if (conf == null) {
      conf = new Configuration();
    }
    StartupOption startOpt = parseArguments(argv);
    if (startOpt == null) {
      printUsage();
      return null;
    }
    setStartupOption(conf, startOpt);
    RaidNode node = new RaidNode(conf);
    if(LOGDISPLAY)  LOG.info("01+++++RaidNode.createRaidNode()+++++");
    return node;
  }


  /**
   */
  public static void main(String argv[]) throws Exception {
    try { 
      StringUtils.startupShutdownMessage(RaidNode.class, argv, LOG);
      LOG.info("00-----RaidNode.main()-----");
      RaidNode raid = createRaidNode(argv, null);
      if (raid != null) {
        raid.join();
      }
    } catch (Throwable e) {
      LOG.error(StringUtils.stringifyException(e));
      System.exit(-1);
    }
    if(LOGDISPLAY)  LOG.info("00+++++RaidNode.main()+++++");
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
  
  public ArrayList<Integer> checkMissBlock(String args[]) throws Exception {
	    String fsName = NetUtils.getServerAddress(conf, "dfs.info.bindAddress", 
	            "dfs.info.port", "dfs.http.address");
	    if(LOGDISPLAY)  LOG.info("fsName = "+fsName);
	    /*
	    String vipName = NetUtils.getServerAddress(conf, "dfs.info.bindAddress", 
	            "dfs.info.port", "fs.default.name");
	    if(LOGDISPLAY)  LOG.info("vipName = "+vipName);
	    //Path vipPath = new Path(vipName);
	    String[] split = vipName.split(":"); 
	    String vip = null;
	    for(String s : split){
	    	if(LOGDISPLAY)  LOG.info("s = "+s);
	    	if(s.startsWith("//")){
	    		vip = s.substring(2);
	    		if(LOGDISPLAY)  LOG.info("vip = "+vip);
	    	}
	    }
	    split = fsName.split(":"); 
	    
	    String line = null;
	    Process p;
	    p = Runtime.getRuntime().exec("ssh " + vip + " hostname");
	    BufferedReader inputBufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    String hostName = inputBufferedReader.readLine();
	    if(LOGDISPLAY)  LOG.info("hostName = "+ hostName);
	    if(LOGDISPLAY1)  LOG.info("cat /etc/hosts | grep \"" + hostName + "\" | awk '{print $1}'");
	    p = Runtime.getRuntime().exec("cat /etc/hosts | grep \"" + hostName + "\" | awk '{print $1}'");
	    inputBufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    Pattern pa = Pattern.compile(hostName);
	    while((line = inputBufferedReader.readLine()) != null){
	    	if(pa.matcher(line).find()){
	    		fsName = line;
	    	}
	    }
	    
	    String[] hostSplit = fsName.split(" "); 
	    fsName = hostName + ":" + split[1];    
	    
	    if(LOGDISPLAY)  LOG.info("fsName = "+fsName);*/
	    
	    
	    ArrayList<Integer> missBlocks = new ArrayList<Integer>();
	    StringBuffer url = new StringBuffer("http://"+fsName+"/fsck?path=");
	    String dir = "/";
	    // find top-level dir first
	    for (int idx = 0; idx < args.length; idx++) {
	      if (!args[idx].startsWith("-")) { dir = args[idx]; break; }
	    }
	    url.append(URLEncoder.encode(dir, "UTF-8"));
	    for (int idx = 0; idx < args.length; idx++) {
	      if (args[idx].equals("-move")) { url.append("&move=1"); }
	      else if (args[idx].equals("-delete")) { url.append("&delete=1"); }
	      else if (args[idx].equals("-files")) { url.append("&files=1"); }
	      else if (args[idx].equals("-openforwrite")) { url.append("&openforwrite=1"); }
	      else if (args[idx].equals("-list-corruptfiles")) { url.append("&corruptfiles=1"); }
	      else if (args[idx].equals("-blocks")) { url.append("&blocks=1"); }
	      else if (args[idx].equals("-locations")) { url.append("&locations=1"); }
	      else if (args[idx].equals("-racks")) { url.append("&racks=1"); }
	    }
	    URL path = new URL(url.toString());
	    if(LOGDISPLAY)  LOG.info("url.toString() = "+url.toString());
	    URLConnection connection = path.openConnection();
	    InputStream stream = connection.getInputStream();
	    BufferedReader input = new BufferedReader(new InputStreamReader(
	                                              stream, "UTF-8"));
	    String line = null;
	    boolean first = true;    
	    int numOfBlocks = 0;
	    try {
	    	int i = 0;
	      while ((line = input.readLine()) != null) {
	        //System.out.println(line);
	        if(first == true){
	        	numOfBlocks = Integer.valueOf(line.split(" ")[3]);        	
	        	System.out.println("numOfBlocks = "+numOfBlocks);        	
	        	first = false;
	        } 
	        if(line.split(" ")[0].equalsIgnoreCase("0."))
	        	i = 0;        
	        if(i < numOfBlocks && line.split(" ")[0].equalsIgnoreCase(String.valueOf(i)+".")){
	        	
	        	if(line.contains("MISSING!")){
	        		System.out.println("OK!!!");
	        		missBlocks.add(i);
	        	}
	        	i++;
	        }
	      }
	    } finally {
	      input.close();
	    }
	    
	    return missBlocks;
	  }
public static Path unRaidWithoutRecoverFile(Configuration conf, Path srcPath, Path destPathPrefix, 
	          int stripeLength, int corruptOffset) throws IOException {
	DFSClient dfs = new DFSClient(conf);
	// Test if parity file exists
	ParityFilePair ppair = getParityFile(destPathPrefix, srcPath, conf); 
	if (ppair == null) {
		return null;
	}
	// open parity file fast to prevent getting a new, wrong, version
	Path parityFile = ppair.getPath();
	FileSystem parityFs = ppair.getFileSystem();
	FSDataInputStream parityInputStream = parityFs.open(parityFile);
	
	// extract block locations, size etc from source file
	Random rand = new Random();
	FileSystem srcFs = srcPath.getFileSystem(conf);
	FileStatus srcStat = srcFs.getFileStatus(srcPath);
	long blockSize = srcStat.getBlockSize();
	long fileSize = srcStat.getLen();
	
	// find the stripe number where the corrupted offset lies
	long snum = corruptOffset / (stripeLength * blockSize);
	long startOffset = snum * stripeLength * blockSize;
	long corruptBlockInStripe = (corruptOffset - startOffset)/blockSize;
	long corruptBlockSize = Math.min(blockSize, fileSize - startOffset);
	
	LOG.info("Start offset of relevent stripe = " + startOffset +
	" corruptBlockInStripe " + corruptBlockInStripe);
	
	// seek to the appropriate offset in parity file.
	LOG.info("Parity file for " + srcPath + " is " + parityFile);
	parityInputStream.seek(snum * blockSize);
	LOG.info("Parity file " + parityFile +
	" seeking to relevent block at offset " + 
	parityInputStream.getPos());
	
	
	// open file descriptors to read all good blocks of the file
	FSDataInputStream[] instmp = new FSDataInputStream[stripeLength];
	int  numLength = 0;
	for (int i = 0; i < stripeLength; i++) {
		if (i == corruptBlockInStripe) {
				continue;  // do not open corrupt block
		}
		if (startOffset + i * blockSize >= fileSize) {
			LOG.info("Stop offset of relevent stripe = " + 
			startOffset + i * blockSize);
			break;
		}
		instmp[numLength] = srcFs.open(srcPath);
		instmp[numLength].seek(startOffset + i * blockSize);
		numLength++;
	}
	
	// create array of inputstream, allocate one extra slot for 
	// parity file. numLength could be smaller than stripeLength
	// if we are processing the last partial stripe on a file.
	numLength += 1;
	FSDataInputStream[] ins = new FSDataInputStream[numLength];
	for (int i = 0; i < numLength-1; i++) {
		ins[i] = instmp[i];
	}
	ins[numLength-1] = parityInputStream;
	LOG.info("Decompose a total of " + numLength + " blocks.");
	
	// create a temporary filename in the source filesystem
	// do not overwrite an existing tmp file. Make it fail for now.
	// We need to generate a unique name for this tmp file later on.
	Path tmpFile = null;
	FSDataOutputStream fout = null;
	FileSystem destFs = destPathPrefix.getFileSystem(conf);
	
	int retry = 5;
	try {
		tmpFile = new Path("/tmp/raid/" + rand.nextInt());
		fout = destFs.create(tmpFile, false);
	
	} catch (IOException e) {
	if (retry-- <= 0) {
		LOG.info("Unable to create temporary file " + tmpFile +
		" Aborting....");
		e.printStackTrace();
		throw e; 
	}
	LOG.info("Unable to create temporary file " + tmpFile +
	"Retrying....");
	}
	LOG.info("Created recovered block file " + tmpFile);
	
	// buffers for generating parity bits
	int bufSize = 5 * 1024 * 1024; // 5 MB
	byte[] bufs = new byte[bufSize];
	byte[] xor = new byte[bufSize];
	
	generateParity(ins,fout,corruptBlockSize,bufs,xor,null);
	
	// close all files
	fout.close();
	for (int i = 0; i < ins.length; i++) {
		ins[i].close();
	}
	
	dfs.changeBlockFromRecoverFileToSrcFile(srcPath.toUri().getPath(), tmpFile.toUri().getPath(), corruptOffset);
	
	//tmpFile.getFileSystem(conf).delete(tmpFile);
	
	
	/*
	// Now, reopen the source file and the recovered block file
	// and copy all relevant data to new file
	final Path recoveryDestination = 
	new Path(conf.get("fs.raid.tmpdir", "/tmp/raid"));
	final Path recoveredPrefix = 
	destFs.makeQualified(new Path(recoveryDestination, makeRelative(srcPath)));
	final Path recoveredPath = 
	new Path(recoveredPrefix + "." + rand.nextLong() + ".recovered");
	LOG.info("Creating recovered file " + recoveredPath);
	
	FSDataInputStream sin = srcFs.open(srcPath);
	FSDataOutputStream out = destFs.create(srcPath, false, 
	                           conf.getInt("io.file.buffer.size", 64 * 1024),
	                           srcStat.getReplication(), 
	                           srcStat.getBlockSize());
	
	FSDataInputStream bin = destFs.open(tmpFile);
	long recoveredSize = 0;
	
	// copy all the good blocks (upto the corruption)
	// from source file to output file
	long remaining = corruptOffset / blockSize * blockSize;
	while (remaining > 0) {
	int toRead = (int)Math.min(remaining, bufSize);
	sin.readFully(bufs, 0, toRead);
	out.write(bufs, 0, toRead);
	remaining -= toRead;
	recoveredSize += toRead;
	}
	LOG.info("Copied upto " + recoveredSize + " from src file. ");
	
	// copy recovered block to output file
	remaining = corruptBlockSize;
	while (recoveredSize < fileSize &&
	remaining > 0) {
	int toRead = (int)Math.min(remaining, bufSize);
	bin.readFully(bufs, 0, toRead);
	out.write(bufs, 0, toRead);
	remaining -= toRead;
	recoveredSize += toRead;
	}
	LOG.info("Copied upto " + recoveredSize + " from recovered-block file. ");
	
	// skip bad block in src file
	if (recoveredSize < fileSize) {
	sin.seek(sin.getPos() + corruptBlockSize); 
	
	}
	
	// copy remaining good data from src file to output file
	while (recoveredSize < fileSize) {
	int toRead = (int)Math.min(fileSize - recoveredSize, bufSize);
	sin.readFully(bufs, 0, toRead);
	out.write(bufs, 0, toRead);
	recoveredSize += toRead;
	}
	out.close();
	LOG.info("Completed writing " + recoveredSize + " bytes into " +
	recoveredPath);
	
	sin.close();
	bin.close();
	
	// delete the temporary block file that was created.
	LOG.info("Deletting temporary file " + tmpFile);
	LOG.info("destFs.getName() " + destFs.getName());
	//LOG.info("destFs.getName() " + destFs.);
	
	
	
	
	destFs.delete(tmpFile, false);
	LOG.info("Deleted temporary file " + tmpFile);
	
	// copy the meta information from source path to the newly created
	// recovered path
	try {
	copyMetaInformation(destFs, srcStat, recoveredPath);
	} catch (Exception exc) {
	LOG.info("Didn't manage to copy meta information because of " + exc + 
	" Ignoring...");
	}
	
	return recoveredPath;*/
	return tmpFile;
	}

}
