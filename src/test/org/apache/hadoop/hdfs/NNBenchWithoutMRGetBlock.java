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

package org.apache.hadoop.hdfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
//import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.hdfs.DFSClient.DFSInputStream;
import org.apache.hadoop.hdfs.DFSClient.DFSOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.NNBenchDFSThread.RemoteFileDFSHandle;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.mapred.JobConf;

import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;


/**
 * This program executes a specified operation that applies load to 
 * the NameNode. Possible operations include create/writing files,
 * opening/reading files, renaming files, and deleting files.
 * 
 * When run simultaneously on multiple nodes, this program functions 
 * as a stress-test and benchmark for namenode, especially when 
 * the number of bytes written to each file is small.
 * 
 * This version does not use the map reduce framework
 * 
 */
public class NNBenchWithoutMRGetBlock extends Configured implements Tool {
  
  private static final Log LOG = LogFactory.getLog(
                                            "org.apache.hadoop.hdfs.NNBench");
  
  // variable initialzed from command line arguments
  private static long startTime = 0;
  private static int numFiles = 0;
  private static long bytesPerBlock = 1;
  private static long blocksPerFile = 0;
  private static long bytesPerFile = 1;
  private static long sizePerFile = 1;
  private static String hostName = null;
  //private static Path baseDir = null;
  private static long replicationFactorPerFile = 2;
  // variables initialized in main()
  //private static FileSystem fileSys = null;
  //private static Path taskDir = null;

  private static DFSClient dfsClient = null;
  private static String    dfsTaskDir = null;
  private static String    baseDir = null;
  
  private static byte[] buffer;
  private static long maxExceptionsPerFile = 2000;
  private static NNBenchDFSThread handleThread = null;    
  private static int sectionInterval = 2;
  private static int sectionNum = 2;
  private static Date execTime = null;
  private static Date endTime  = null;
  private static Random random = new Random();
  private static int minWordsInValue = 10;
  private static long totalReadBytes = 0;
  //private static int failFileCount = 0;
  /**
   * Returns when the current number of seconds from the epoch equals
   * the command line argument given by <code>-startTime</code>.
   * This allows multiple instances of this program, running on clock
   * synchronized nodes, to start at roughly the same time.
   */

  static void barrier() {
    long sleepTime;
    while ((sleepTime = startTime - System.currentTimeMillis()) > 0) {
      try {
        Thread.sleep(sleepTime);
      } catch (InterruptedException ex) {
        //This left empty on purpose
      }
    }
  }
    
  static private void handleException(String operation, Throwable e, 
                                      int singleFileExceptions) {
	System.out.println("handleexception while " + operation + ": " +
            StringUtils.stringifyException(e));  
    //LOG.warn("Exception while " + operation + ": " +
    //         StringUtils.stringifyException(e));
    if (singleFileExceptions >= maxExceptionsPerFile) {
        endTime = new Date();
        long duration = (endTime.getTime() - execTime.getTime()) /1000;

     	System.out.println("current total exceptions:"+singleFileExceptions+".Aborting"+
             " createfailfile:"+NNBenchDFSThread.createfailFileCount+" closefailfile:"+ NNBenchDFSThread.closefailFileCount+
             " spent totaltime:"+duration);
     	
      throw new RuntimeException(singleFileExceptions + 
        " exceptions for a single file exceeds threshold. Aborting");
    }
  }
  
  /**
   * Create and write to a given number of files.  Repeat each remote
   * operation until is suceeds (does not throw an exception).
   *
   * @return the number of exceptions caught
   */
  static int createWrite() {
	  
	OutputStream out = null;
    boolean success;
    int totalExceptions = 0;    
    for (int index = 0; index < numFiles; index++) {
      int singleFileExceptions = 0;
      String createFilePath = null;
      try{
    	  totalExceptions  = 5;
          do { // create file until is succeeds or max exceptions reached
              try {
            	createFilePath = dfsTaskDir+"/"+index;
            	out = (OutputStream)dfsClient.create(createFilePath, 
            			FsPermission.getDefault(),
                          false, 
                          (short)replicationFactorPerFile,
                          bytesPerBlock,
                          null,
                          4096
                          );
            	
                success = true;
              } catch (IOException ioe) { 
                success=false; 
                totalExceptions--;
                handleException("creating file #" + index, ioe,++singleFileExceptions);
              }
            } while (!success && totalExceptions > 0 );//创建文件直到成功

           if(!success || totalExceptions <= 0){
        	   NNBenchDFSThread.createfailFileCount ++;
                 System.out.println("create fail," +  NNBenchDFSThread.createfailFileCount);
                 continue;
            }else{
            }
          
            long toBeWritten = bytesPerFile;
            while (toBeWritten > 0) {
              int nbytes = (int) Math.min(buffer.length, toBeWritten);
              toBeWritten -= nbytes;
              try { // only try once
                out.write(buffer, 0, nbytes);
              } catch (IOException ioe) {
                totalExceptions++;
                handleException("writing to file #" + index, ioe,++singleFileExceptions);
              }
            }//end while
            if(index % 100 == 0){
                System.out.println(""+index+ " file"+createFilePath+" create success, and waite close ...");            	
            }
            
            RemoteFileDFSHandle fileHandle = new RemoteFileDFSHandle(createFilePath,out);
            handleThread.addHandle(fileHandle);
//            do { // close file until is succeeds
//              try {
//                out.close();
//                success = true;
//              } catch (IOException ioe) {
//                success=false; 
//                totalExceptions++;
//                handleException("closing file #" + index, ioe,
//                        ++singleFileExceptions);
//              }
//            } while (!success);
              if(index % sectionNum  == 0){
                  try{
                  	Thread.sleep(sectionInterval*1000);
                  }catch(Exception er){
                  	System.err.println(er);
                  }
              	
              }
              if(index % 50 == 0){
              	System.gc();
              }
    	  
      }catch(OutOfMemoryError ooMIe){
    	  System.out.println("++++ outofmemory ++++++");
    	  System.out.println(ooMIe);
    	  System.gc();        	
    	  try{
    		  Thread.sleep(10*1000);  
    	  }catch(Exception er){}
    	  
    	  System.out.println("--- System gc complete ------");    	  
      }
    }//end for
    return totalExceptions;
  }//end func
  
  /**
   * Open and read a given number of files.
   *
   * @return the number of exceptions caught
   */
  static int openRead() {
    int totalExceptions = 0;
    InputStream in;
    for (int index = 0; index < numFiles; index++) {
      int singleFileExceptions = 0;
      try {
    	String inPath = dfsTaskDir+ "/" + index;
    	in =(InputStream)dfsClient.open(inPath);
        //in = fileSys.open(new Path(taskDir, "" + index), 512);
        long toBeRead = bytesPerFile;
        while (toBeRead > 0) {
          int nbytes = (int) Math.min(buffer.length, toBeRead);
          toBeRead -= nbytes;
          try { // only try once && we don't care about a number of bytes read
            in.read(buffer, 0, nbytes);
            totalReadBytes += nbytes;
          } catch (IOException ioe) {
            totalExceptions++;
            handleException("reading from file #" + index, ioe,
                    ++singleFileExceptions);
          }
        }//end while
        in.close();
      } catch (IOException ioe) { 
        totalExceptions++;
        handleException("opening file #" + index, ioe, ++singleFileExceptions);
      }
    }
    return totalExceptions;
  }
    
  /**
   * Rename a given number of files.  Repeat each remote
   * operation until is suceeds (does not throw an exception).
   *
   * @return the number of exceptions caught
   */
  static int rename() {
    int totalExceptions = 0;
    boolean success;
    for (int index = 0; index < numFiles; index++) {
      int singleFileExceptions = 0;
      do { // rename file until is succeeds
        try {
          // Possible result of this operation is at no interest to us for it
          // can return false only if the namesystem
          // could rename the path from the name
          // space (e.g. no Exception has been thrown)
          //fileSys.rename(new Path(taskDir, "" + index),
           //   new Path(taskDir, "A" + index));
          dfsClient.rename(dfsTaskDir+"/" + index, dfsTaskDir+"/A" + index);
          success = true;
        } catch (IOException ioe) { 
          success=false; 
          totalExceptions++;
          handleException("creating file #" + index, ioe, ++singleFileExceptions);
       }
      } while (!success);
    }
    return totalExceptions;
  }
    
  /**
   * Delete a given number of files.  Repeat each remote
   * operation until is suceeds (does not throw an exception).
   *
   * @return the number of exceptions caught
   */
  static int delete() {
    int totalExceptions = 0;
    boolean success;
    for (int index = 0; index < numFiles; index++) {
      int singleFileExceptions = 0;
      do { // delete file until is succeeds
        try {
          // Possible result of this operation is at no interest to us for it
          // can return false only if namesystem
          // delete could remove the path from the name
          // space (e.g. no Exception has been thrown)
          //fileSys.delete(new Path(taskDir, "A" + index), true);
          dfsClient.delete(dfsTaskDir+"/A" + index, true);
          success = true;
        } catch (IOException ioe) { 
          success=false; 
          totalExceptions++;
          handleException("creating file #" + index, ioe, ++singleFileExceptions);
        }
      } while (!success);
    }
    return totalExceptions;
  }
    
  /**
   * This launches a given namenode operation (<code>-operation</code>),
   * starting at a given time (<code>-startTime</code>).  The files used
   * by the openRead, rename, and delete operations are the same files
   * created by the createWrite operation.  Typically, the program
   * would be run four times, once for each operation in this order:
   * createWrite, openRead, rename, delete.
   *
   * <pre>
   * Usage: nnbench 
   *          -operation <one of createWrite, openRead, rename, or delete>
   *          -baseDir <base output/input DFS path>
   *          -startTime <time to start, given in seconds from the epoch>
   *          -numFiles <number of files to create, read, rename, or delete>
   *          -blocksPerFile <number of blocks to create per file>
   *         [-bytesPerBlock <number of bytes to write to each block, default is 1>]
   *         [-bytesPerChecksum <value for io.bytes.per.checksum>]
   * </pre>
   *
   * @param args is an array of the program command line arguments
   * @throws IOException indicates a problem with test startup
   */
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new NNBenchWithoutMRGetBlock(), args);
    System.exit(res);
  }

  @Override
  public int run(String[] args) throws Exception {

    String version = "NameNodeBenchmark.0.3";
    System.out.println(version);
    long bytesPerChecksum = -1;
    
    String usage =
      "Usage: NNBenchWithoutMR " +
      "  -operation <one of createWrite, openRead, rename, or delete> " +
      "  -baseDir <base output/input DFS path> " +
      "  -startTime <time to start, given in seconds from the epoch> " +
      "  -numFiles <number of files to create> " +
      "  -blocksPerFile <number of blocks to create per file> " +
      "  -replicationFactorPerFile < replication factor>" +
      "  -sectionInterval <second>" +
      "  -sectionNum <collection num>" +
      "  [-bytesPerBlock <number of bytes to write to each block, default is 1>] " +
      "  [-bytesPerChecksum <value for io.bytes.per.checksum>]" +
      "  [-host <vmbd04>]"+
      "Note: bytesPerBlock MUST be a multiple of bytesPerChecksum";
    
    String operation = null;
    for (int i = 0; i < args.length; i++) { // parse command line
      if (args[i].equals("-baseDir")) {
        baseDir = args[++i];
      } else if (args[i].equals("-numFiles")) {
        numFiles = Integer.parseInt(args[++i]);
      } else if (args[i].equals("-blocksPerFile")) {
        blocksPerFile = Long.parseLong(args[++i]);
      }else if(args[i].equals("-bytesPerFile")){
    	  bytesPerFile = Long.parseLong(args[++i]);
      }else if (args[i].equals("-bytesPerBlock")) {
        bytesPerBlock = Long.parseLong(args[++i]);
      } else if (args[i].equals("-bytesPerChecksum")) {
        bytesPerChecksum = Long.parseLong(args[++i]);        
      } else if (args[i].equals("-startTime")) {
        startTime = Long.parseLong(args[++i]) * 1000;
        if(startTime <= 0){
        	startTime = System.currentTimeMillis()/1000;
        }
      } else if (args[i].equals("-operation")) {
        operation = args[++i];
      }else if(args[i].equals("-replicationFactorPerFile")){
    	  replicationFactorPerFile = Integer.parseInt(args[++i]);  
      }else if(args[i].equals("-sectionInterval")){
    	  sectionInterval = Integer.parseInt(args[++i]);
      }else if(args[i].equals("-sectionNum")){
    	  sectionNum = Integer.parseInt(args[++i]);  
      }else if(args[i].equals("-host")){
    	  hostName = args[++i];
    	  hostName = hostName.trim();
      }
      else {
        System.out.println(usage);
        for(int j = 0; j < args.length; j++){
        	System.out.println("args["+args[j]+"]");
        }
        return -1;
      }
    }
    //1024*2
    //bytesPerFile = bytesPerBlock * blocksPerFile;
    if(bytesPerFile > bytesPerBlock * blocksPerFile){
    	bytesPerFile = bytesPerBlock * blocksPerFile;
    }

    
    JobConf jobConf = new JobConf(new Configuration(), NNBench.class);
    
    if ( bytesPerChecksum < 0 ) { // if it is not set in cmdline
      bytesPerChecksum = jobConf.getLong("io.bytes.per.checksum", 512);
    }
    jobConf.set("io.bytes.per.checksum", Long.toString(bytesPerChecksum));
    minWordsInValue = 
            jobConf.getInt("test.randomtextwrite.min_words_value", 1000);
    
    
    System.out.println("Inputs: ");
    System.out.println("   operation: " + operation);
    System.out.println("   baseDir: " + baseDir);
    System.out.println("   startTime: " + startTime);
    System.out.println("   numFiles: " + numFiles);
    System.out.println("   blocksPerFile: " + blocksPerFile);
    System.out.println("   bytesPerBlock: " + bytesPerBlock);
    System.out.println("   bytesPerChecksum: " + bytesPerChecksum);
    System.out.println("   replicationFactorPerFile: " + replicationFactorPerFile);    
    System.out.println("   sectionInterval: "+ sectionInterval);
    System.out.println("   sectionNum: "+ sectionNum);
    
    if (operation == null ||  // verify args
        baseDir == null ||
        numFiles < 1 ||
        blocksPerFile < 1 ||
        bytesPerBlock < 0 ||
        bytesPerBlock % bytesPerChecksum != 0)
      {
        System.err.println(usage);
        System.out.println("args:operation="+operation+" baseDir="+baseDir+" numFiles="+numFiles+
        		" blocksPerFile="+blocksPerFile+" bytesPerBlock="+bytesPerBlock+
        		" bytesPerBlock % bytesPerChecksum="+(bytesPerBlock % bytesPerChecksum));
        return -1;
      }
    
    //fileSys = FileSystem.get(jobConf);
    dfsClient = new DFSClient(jobConf);
    String uniqueId = null;
    if(hostName != null){
    	uniqueId = hostName;
    }else{
    	uniqueId = java.net.InetAddress.getLocalHost().getHostName();
    }
    
    //taskDir = new Path(baseDir, uniqueId);
    dfsTaskDir = baseDir+"/"+uniqueId;
    // initialize buffer used for writing/reading file
    buffer = new byte[(int) Math.min(bytesPerFile, 32768L)];
    //for(int k=0;k<buffer.length;k++){
    //	int tmpasc = k - '0';
    //	buffer[k]=(byte)tmpasc;
    //}//end for
    if(operation.equals("createWrite")){
        StringBuffer sentence = new StringBuffer();
        String space = " ";
        //for (int i=0; i < minWordsInValue; ++i) {
        //  sentence.append(words[random.nextInt(words.length)]);
        //  sentence.append(space);
        //}
        do{
            sentence.append(words[random.nextInt(words.length)]);
            sentence.append(space);
        }while(sentence.length() <= buffer.length);
        
        System.out.println("perFileLen="+bytesPerFile+" buffer len="+buffer.length+" sentence size="+sentence.length());
        sentence.toString().getBytes(0, buffer.length-1, buffer, 0);
   }
    handleThread = new NNBenchDFSThread();
    handleThread.start();
    
    //Date execTime;
    //Date endTime;
    long duration;
    int exceptions = 0;
    barrier(); // wait for coordinated start time
    execTime = new Date();
    System.out.println("Job started: " + startTime);
    if (operation.equals("createWrite")) {
    if(!dfsClient.mkdirs(dfsTaskDir)){
        throw new IOException("Mkdirs failed to create " + dfsTaskDir);    	
    }
    //  if (!fileSys.mkdirs(taskDir)) {
    //    throw new IOException("Mkdirs failed to create " + taskDir.toString());
    //  }
      exceptions = createWrite();
    } else if (operation.equals("openRead")) {
      exceptions = openRead();
    } else if (operation.equals("rename")) {
      exceptions = rename();
    } else if (operation.equals("delete")) {
      exceptions = delete();
    } else {
      System.err.println(usage);
      return -1;
    }
    endTime = new Date();
    System.out.println("Job ended: " + endTime);
    duration = (endTime.getTime() - execTime.getTime()) /1000;
    System.out.println("The " + operation + " job took " + duration + " seconds.");
    System.out.println("The job recorded " + exceptions + " exceptions.");
    System.out.println("Fail create file count is "+NNBenchDFSThread.createfailFileCount);
    System.out.println("Fail close  file count is "+NNBenchDFSThread.closefailFileCount);
    System.out.println("The read filebytes "+totalReadBytes);
    handleThread.setEnd(true);
    try{
        handleThread.join();    	
    }catch(Exception er){
    	System.err.println(er);
    }

    return 0;
  }
  /**
   * A random list of 100 words from /usr/share/dict/words
   */
  private static String[] words = {
                                   "DIURNALNESS", "Homoiousian",
                                   "spiranthic", "tetragynian",
                                   "silverhead", "ungreat",
                                   "lithograph", "exploiter",
                                   "physiologian", "by",
                                   "hellbender", "Filipendula",
                                   "undeterring", "antiscolic",
                                   "pentagamist", "hypoid",
                                   "cacuminal", "sertularian",
                                   "schoolmasterism", "nonuple",
                                   "gallybeggar", "phytonic",
                                   "swearingly", "nebular",
                                   "Confervales", "thermochemically",
                                   "characinoid", "cocksuredom",
                                   "fallacious", "feasibleness",
                                   "debromination", "playfellowship",
                                   "tramplike", "testa",
                                   "participatingly", "unaccessible",
                                   "bromate", "experientialist",
                                   "roughcast", "docimastical",
                                   "choralcelo", "blightbird",
                                   "peptonate", "sombreroed",
                                   "unschematized", "antiabolitionist",
                                   "besagne", "mastication",
                                   "bromic", "sviatonosite",
                                   "cattimandoo", "metaphrastical",
                                   "endotheliomyoma", "hysterolysis",
                                   "unfulminated", "Hester",
                                   "oblongly", "blurredness",
                                   "authorling", "chasmy",
                                   "Scorpaenidae", "toxihaemia",
                                   "Dictograph", "Quakerishly",
                                   "deaf", "timbermonger",
                                   "strammel", "Thraupidae",
                                   "seditious", "plerome",
                                   "Arneb", "eristically",
                                   "serpentinic", "glaumrie",
                                   "socioromantic", "apocalypst",
                                   "tartrous", "Bassaris",
                                   "angiolymphoma", "horsefly",
                                   "kenno", "astronomize",
                                   "euphemious", "arsenide",
                                   "untongued", "parabolicness",
                                   "uvanite", "helpless",
                                   "gemmeous", "stormy",
                                   "templar", "erythrodextrin",
                                   "comism", "interfraternal",
                                   "preparative", "parastas",
                                   "frontoorbital", "Ophiosaurus",
                                   "diopside", "serosanguineous",
                                   "ununiformly", "karyological",
                                   "collegian", "allotropic",
                                   "depravity", "amylogenesis",
                                   "reformatory", "epidymides",
                                   "pleurotropous", "trillium",
                                   "dastardliness", "coadvice",
                                   "embryotic", "benthonic",
                                   "pomiferous", "figureheadship",
                                   "Megaluridae", "Harpa",
                                   "frenal", "commotion",
                                   "abthainry", "cobeliever",
                                   "manilla", "spiciferous",
                                   "nativeness", "obispo",
                                   "monilioid", "biopsic",
                                   "valvula", "enterostomy",
                                   "planosubulate", "pterostigma",
                                   "lifter", "triradiated",
                                   "venialness", "tum",
                                   "archistome", "tautness",
                                   "unswanlike", "antivenin",
                                   "Lentibulariaceae", "Triphora",
                                   "angiopathy", "anta",
                                   "Dawsonia", "becomma",
                                   "Yannigan", "winterproof",
                                   "antalgol", "harr",
                                   "underogating", "ineunt",
                                   "cornberry", "flippantness",
                                   "scyphostoma", "approbation",
                                   "Ghent", "Macraucheniidae",
                                   "scabbiness", "unanatomized",
                                   "photoelasticity", "eurythermal",
                                   "enation", "prepavement",
                                   "flushgate", "subsequentially",
                                   "Edo", "antihero",
                                   "Isokontae", "unforkedness",
                                   "porriginous", "daytime",
                                   "nonexecutive", "trisilicic",
                                   "morphiomania", "paranephros",
                                   "botchedly", "impugnation",
                                   "Dodecatheon", "obolus",
                                   "unburnt", "provedore",
                                   "Aktistetae", "superindifference",
                                   "Alethea", "Joachimite",
                                   "cyanophilous", "chorograph",
                                   "brooky", "figured",
                                   "periclitation", "quintette",
                                   "hondo", "ornithodelphous",
                                   "unefficient", "pondside",
                                   "bogydom", "laurinoxylon",
                                   "Shiah", "unharmed",
                                   "cartful", "noncrystallized",
                                   "abusiveness", "cromlech",
                                   "japanned", "rizzomed",
                                   "underskin", "adscendent",
                                   "allectory", "gelatinousness",
                                   "volcano", "uncompromisingly",
                                   "cubit", "idiotize",
                                   "unfurbelowed", "undinted",
                                   "magnetooptics", "Savitar",
                                   "diwata", "ramosopalmate",
                                   "Pishquow", "tomorn",
                                   "apopenptic", "Haversian",
                                   "Hysterocarpus", "ten",
                                   "outhue", "Bertat",
                                   "mechanist", "asparaginic",
                                   "velaric", "tonsure",
                                   "bubble", "Pyrales",
                                   "regardful", "glyphography",
                                   "calabazilla", "shellworker",
                                   "stradametrical", "havoc",
                                   "theologicopolitical", "sawdust",
                                   "diatomaceous", "jajman",
                                   "temporomastoid", "Serrifera",
                                   "Ochnaceae", "aspersor",
                                   "trailmaking", "Bishareen",
                                   "digitule", "octogynous",
                                   "epididymitis", "smokefarthings",
                                   "bacillite", "overcrown",
                                   "mangonism", "sirrah",
                                   "undecorated", "psychofugal",
                                   "bismuthiferous", "rechar",
                                   "Lemuridae", "frameable",
                                   "thiodiazole", "Scanic",
                                   "sportswomanship", "interruptedness",
                                   "admissory", "osteopaedion",
                                   "tingly", "tomorrowness",
                                   "ethnocracy", "trabecular",
                                   "vitally", "fossilism",
                                   "adz", "metopon",
                                   "prefatorial", "expiscate",
                                   "diathermacy", "chronist",
                                   "nigh", "generalizable",
                                   "hysterogen", "aurothiosulphuric",
                                   "whitlowwort", "downthrust",
                                   "Protestantize", "monander",
                                   "Itea", "chronographic",
                                   "silicize", "Dunlop",
                                   "eer", "componental",
                                   "spot", "pamphlet",
                                   "antineuritic", "paradisean",
                                   "interruptor", "debellator",
                                   "overcultured", "Florissant",
                                   "hyocholic", "pneumatotherapy",
                                   "tailoress", "rave",
                                   "unpeople", "Sebastian",
                                   "thermanesthesia", "Coniferae",
                                   "swacking", "posterishness",
                                   "ethmopalatal", "whittle",
                                   "analgize", "scabbardless",
                                   "naught", "symbiogenetically",
                                   "trip", "parodist",
                                   "columniform", "trunnel",
                                   "yawler", "goodwill",
                                   "pseudohalogen", "swangy",
                                   "cervisial", "mediateness",
                                   "genii", "imprescribable",
                                   "pony", "consumptional",
                                   "carposporangial", "poleax",
                                   "bestill", "subfebrile",
                                   "sapphiric", "arrowworm",
                                   "qualminess", "ultraobscure",
                                   "thorite", "Fouquieria",
                                   "Bermudian", "prescriber",
                                   "elemicin", "warlike",
                                   "semiangle", "rotular",
                                   "misthread", "returnability",
                                   "seraphism", "precostal",
                                   "quarried", "Babylonism",
                                   "sangaree", "seelful",
                                   "placatory", "pachydermous",
                                   "bozal", "galbulus",
                                   "spermaphyte", "cumbrousness",
                                   "pope", "signifier",
                                   "Endomycetaceae", "shallowish",
                                   "sequacity", "periarthritis",
                                   "bathysphere", "pentosuria",
                                   "Dadaism", "spookdom",
                                   "Consolamentum", "afterpressure",
                                   "mutter", "louse",
                                   "ovoviviparous", "corbel",
                                   "metastoma", "biventer",
                                   "Hydrangea", "hogmace",
                                   "seizing", "nonsuppressed",
                                   "oratorize", "uncarefully",
                                   "benzothiofuran", "penult",
                                   "balanocele", "macropterous",
                                   "dishpan", "marten",
                                   "absvolt", "jirble",
                                   "parmelioid", "airfreighter",
                                   "acocotl", "archesporial",
                                   "hypoplastral", "preoral",
                                   "quailberry", "cinque",
                                   "terrestrially", "stroking",
                                   "limpet", "moodishness",
                                   "canicule", "archididascalian",
                                   "pompiloid", "overstaid",
                                   "introducer", "Italical",
                                   "Christianopaganism", "prescriptible",
                                   "subofficer", "danseuse",
                                   "cloy", "saguran",
                                   "frictionlessly", "deindividualization",
                                   "Bulanda", "ventricous",
                                   "subfoliar", "basto",
                                   "scapuloradial", "suspend",
                                   "stiffish", "Sphenodontidae",
                                   "eternal", "verbid",
                                   "mammonish", "upcushion",
                                   "barkometer", "concretion",
                                   "preagitate", "incomprehensible",
                                   "tristich", "visceral",
                                   "hemimelus", "patroller",
                                   "stentorophonic", "pinulus",
                                   "kerykeion", "brutism",
                                   "monstership", "merciful",
                                   "overinstruct", "defensibly",
                                   "bettermost", "splenauxe",
                                   "Mormyrus", "unreprimanded",
                                   "taver", "ell",
                                   "proacquittal", "infestation",
                                   "overwoven", "Lincolnlike",
                                   "chacona", "Tamil",
                                   "classificational", "lebensraum",
                                   "reeveland", "intuition",
                                   "Whilkut", "focaloid",
                                   "Eleusinian", "micromembrane",
                                   "byroad", "nonrepetition",
                                   "bacterioblast", "brag",
                                   "ribaldrous", "phytoma",
                                   "counteralliance", "pelvimetry",
                                   "pelf", "relaster",
                                   "thermoresistant", "aneurism",
                                   "molossic", "euphonym",
                                   "upswell", "ladhood",
                                   "phallaceous", "inertly",
                                   "gunshop", "stereotypography",
                                   "laryngic", "refasten",
                                   "twinling", "oflete",
                                   "hepatorrhaphy", "electrotechnics",
                                   "cockal", "guitarist",
                                   "topsail", "Cimmerianism",
                                   "larklike", "Llandovery",
                                   "pyrocatechol", "immatchable",
                                   "chooser", "metrocratic",
                                   "craglike", "quadrennial",
                                   "nonpoisonous", "undercolored",
                                   "knob", "ultratense",
                                   "balladmonger", "slait",
                                   "sialadenitis", "bucketer",
                                   "magnificently", "unstipulated",
                                   "unscourged", "unsupercilious",
                                   "packsack", "pansophism",
                                   "soorkee", "percent",
                                   "subirrigate", "champer",
                                   "metapolitics", "spherulitic",
                                   "involatile", "metaphonical",
                                   "stachyuraceous", "speckedness",
                                   "bespin", "proboscidiform",
                                   "gul", "squit",
                                   "yeelaman", "peristeropode",
                                   "opacousness", "shibuichi",
                                   "retinize", "yote",
                                   "misexposition", "devilwise",
                                   "pumpkinification", "vinny",
                                   "bonze", "glossing",
                                   "decardinalize", "transcortical",
                                   "serphoid", "deepmost",
                                   "guanajuatite", "wemless",
                                   "arval", "lammy",
                                   "Effie", "Saponaria",
                                   "tetrahedral", "prolificy",
                                   "excerpt", "dunkadoo",
                                   "Spencerism", "insatiately",
                                   "Gilaki", "oratorship",
                                   "arduousness", "unbashfulness",
                                   "Pithecolobium", "unisexuality",
                                   "veterinarian", "detractive",
                                   "liquidity", "acidophile",
                                   "proauction", "sural",
                                   "totaquina", "Vichyite",
                                   "uninhabitedness", "allegedly",
                                   "Gothish", "manny",
                                   "Inger", "flutist",
                                   "ticktick", "Ludgatian",
                                   "homotransplant", "orthopedical",
                                   "diminutively", "monogoneutic",
                                   "Kenipsim", "sarcologist",
                                   "drome", "stronghearted",
                                   "Fameuse", "Swaziland",
                                   "alen", "chilblain",
                                   "beatable", "agglomeratic",
                                   "constitutor", "tendomucoid",
                                   "porencephalous", "arteriasis",
                                   "boser", "tantivy",
                                   "rede", "lineamental",
                                   "uncontradictableness", "homeotypical",
                                   "masa", "folious",
                                   "dosseret", "neurodegenerative",
                                   "subtransverse", "Chiasmodontidae",
                                   "palaeotheriodont", "unstressedly",
                                   "chalcites", "piquantness",
                                   "lampyrine", "Aplacentalia",
                                   "projecting", "elastivity",
                                   "isopelletierin", "bladderwort",
                                   "strander", "almud",
                                   "iniquitously", "theologal",
                                   "bugre", "chargeably",
                                   "imperceptivity", "meriquinoidal",
                                   "mesophyte", "divinator",
                                   "perfunctory", "counterappellant",
                                   "synovial", "charioteer",
                                   "crystallographical", "comprovincial",
                                   "infrastapedial", "pleasurehood",
                                   "inventurous", "ultrasystematic",
                                   "subangulated", "supraoesophageal",
                                   "Vaishnavism", "transude",
                                   "chrysochrous", "ungrave",
                                   "reconciliable", "uninterpleaded",
                                   "erlking", "wherefrom",
                                   "aprosopia", "antiadiaphorist",
                                   "metoxazine", "incalculable",
                                   "umbellic", "predebit",
                                   "foursquare", "unimmortal",
                                   "nonmanufacture", "slangy",
                                   "predisputant", "familist",
                                   "preaffiliate", "friarhood",
                                   "corelysis", "zoonitic",
                                   "halloo", "paunchy",
                                   "neuromimesis", "aconitine",
                                   "hackneyed", "unfeeble",
                                   "cubby", "autoschediastical",
                                   "naprapath", "lyrebird",
                                   "inexistency", "leucophoenicite",
                                   "ferrogoslarite", "reperuse",
                                   "uncombable", "tambo",
                                   "propodiale", "diplomatize",
                                   "Russifier", "clanned",
                                   "corona", "michigan",
                                   "nonutilitarian", "transcorporeal",
                                   "bought", "Cercosporella",
                                   "stapedius", "glandularly",
                                   "pictorially", "weism",
                                   "disilane", "rainproof",
                                   "Caphtor", "scrubbed",
                                   "oinomancy", "pseudoxanthine",
                                   "nonlustrous", "redesertion",
                                   "Oryzorictinae", "gala",
                                   "Mycogone", "reappreciate",
                                   "cyanoguanidine", "seeingness",
                                   "breadwinner", "noreast",
                                   "furacious", "epauliere",
                                   "omniscribent", "Passiflorales",
                                   "uninductive", "inductivity",
                                   "Orbitolina", "Semecarpus",
                                   "migrainoid", "steprelationship",
                                   "phlogisticate", "mesymnion",
                                   "sloped", "edificator",
                                   "beneficent", "culm",
                                   "paleornithology", "unurban",
                                   "throbless", "amplexifoliate",
                                   "sesquiquintile", "sapience",
                                   "astucious", "dithery",
                                   "boor", "ambitus",
                                   "scotching", "uloid",
                                   "uncompromisingness", "hoove",
                                   "waird", "marshiness",
                                   "Jerusalem", "mericarp",
                                   "unevoked", "benzoperoxide",
                                   "outguess", "pyxie",
                                   "hymnic", "euphemize",
                                   "mendacity", "erythremia",
                                   "rosaniline", "unchatteled",
                                   "lienteria", "Bushongo",
                                   "dialoguer", "unrepealably",
                                   "rivethead", "antideflation",
                                   "vinegarish", "manganosiderite",
                                   "doubtingness", "ovopyriform",
                                   "Cephalodiscus", "Muscicapa",
                                   "Animalivora", "angina",
                                   "planispheric", "ipomoein",
                                   "cuproiodargyrite", "sandbox",
                                   "scrat", "Munnopsidae",
                                   "shola", "pentafid",
                                   "overstudiousness", "times",
                                   "nonprofession", "appetible",
                                   "valvulotomy", "goladar",
                                   "uniarticular", "oxyterpene",
                                   "unlapsing", "omega",
                                   "trophonema", "seminonflammable",
                                   "circumzenithal", "starer",
                                   "depthwise", "liberatress",
                                   "unleavened", "unrevolting",
                                   "groundneedle", "topline",
                                   "wandoo", "umangite",
                                   "ordinant", "unachievable",
                                   "oversand", "snare",
                                   "avengeful", "unexplicit",
                                   "mustafina", "sonable",
                                   "rehabilitative", "eulogization",
                                   "papery", "technopsychology",
                                   "impressor", "cresylite",
                                   "entame", "transudatory",
                                   "scotale", "pachydermatoid",
                                   "imaginary", "yeat",
                                   "slipped", "stewardship",
                                   "adatom", "cockstone",
                                   "skyshine", "heavenful",
                                   "comparability", "exprobratory",
                                   "dermorhynchous", "parquet",
                                   "cretaceous", "vesperal",
                                   "raphis", "undangered",
                                   "Glecoma", "engrain",
                                   "counteractively", "Zuludom",
                                   "orchiocatabasis", "Auriculariales",
                                   "warriorwise", "extraorganismal",
                                   "overbuilt", "alveolite",
                                   "tetchy", "terrificness",
                                   "widdle", "unpremonished",
                                   "rebilling", "sequestrum",
                                   "equiconvex", "heliocentricism",
                                   "catabaptist", "okonite",
                                   "propheticism", "helminthagogic",
                                   "calycular", "giantly",
                                   "wingable", "golem",
                                   "unprovided", "commandingness",
                                   "greave", "haply",
                                   "doina", "depressingly",
                                   "subdentate", "impairment",
                                   "decidable", "neurotrophic",
                                   "unpredict", "bicorporeal",
                                   "pendulant", "flatman",
                                   "intrabred", "toplike",
                                   "Prosobranchiata", "farrantly",
                                   "toxoplasmosis", "gorilloid",
                                   "dipsomaniacal", "aquiline",
                                   "atlantite", "ascitic",
                                   "perculsive", "prospectiveness",
                                   "saponaceous", "centrifugalization",
                                   "dinical", "infravaginal",
                                   "beadroll", "affaite",
                                   "Helvidian", "tickleproof",
                                   "abstractionism", "enhedge",
                                   "outwealth", "overcontribute",
                                   "coldfinch", "gymnastic",
                                   "Pincian", "Munychian",
                                   "codisjunct", "quad",
                                   "coracomandibular", "phoenicochroite",
                                   "amender", "selectivity",
                                   "putative", "semantician",
                                   "lophotrichic", "Spatangoidea",
                                   "saccharogenic", "inferent",
                                   "Triconodonta", "arrendation",
                                   "sheepskin", "taurocolla",
                                   "bunghole", "Machiavel",
                                   "triakistetrahedral", "dehairer",
                                   "prezygapophysial", "cylindric",
                                   "pneumonalgia", "sleigher",
                                   "emir", "Socraticism",
                                   "licitness", "massedly",
                                   "instructiveness", "sturdied",
                                   "redecrease", "starosta",
                                   "evictor", "orgiastic",
                                   "squdge", "meloplasty",
                                   "Tsonecan", "repealableness",
                                   "swoony", "myesthesia",
                                   "molecule", "autobiographist",
                                   "reciprocation", "refective",
                                   "unobservantness", "tricae",
                                   "ungouged", "floatability",
                                   "Mesua", "fetlocked",
                                   "chordacentrum", "sedentariness",
                                   "various", "laubanite",
                                   "nectopod", "zenick",
                                   "sequentially", "analgic",
                                   "biodynamics", "posttraumatic",
                                   "nummi", "pyroacetic",
                                   "bot", "redescend",
                                   "dispermy", "undiffusive",
                                   "circular", "trillion",
                                   "Uraniidae", "ploration",
                                   "discipular", "potentness",
                                   "sud", "Hu",
                                   "Eryon", "plugger",
                                   "subdrainage", "jharal",
                                   "abscission", "supermarket",
                                   "countergabion", "glacierist",
                                   "lithotresis", "minniebush",
                                   "zanyism", "eucalypteol",
                                   "sterilely", "unrealize",
                                   "unpatched", "hypochondriacism",
                                   "critically", "cheesecutter",
                                  };
  
}
