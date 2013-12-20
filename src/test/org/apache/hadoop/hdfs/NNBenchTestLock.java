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
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
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
public class NNBenchTestLock extends Configured implements Tool {
  
  private static final Log LOG = LogFactory.getLog(
                                            "org.apache.hadoop.hdfs.NNBench");
  
  // variable initialzed from command line arguments
  private static long startTime = 0;
  private static int numFiles = 2000;
  private static long bytesPerBlock = 1024;
  private static long blocksPerFile = 1024;
  private static long bytesPerFile = 1;
  private static int bytesPerChecksum = 1024;
  //private static Path baseDir = null;
  private static long replicationFactorPerFile = 2;
  // variables initialized in main()

  private static DFSClient dfsClient = null;
  private static String    dfsTaskDir = null;
  private static String    baseDir = "/test";
  
  private static byte[] buffer;
  private static long maxExceptionsPerFile = 2000;
  private static NNBenchDFSThread handleThread = null;    
  private static int sectionInterval = 5;
  private static int sectionNum = 100;
  private static Date execTime = new Date();
  private static Date endTime  = new Date();
  private static String hostName = null;
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
    int res = ToolRunner.run(new NNBenchTestLock(), args);
    System.exit(res);
  }

  @Override
  public int run(String[] args) throws Exception {

    String version = "NameNodeBenchmark.0.3";
    System.out.println(version);
    //int bytesPerChecksum = -1;
    
    String usage =
      "Usage: NNBenchLock " +
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
      "Note: bytesPerBlock MUST be a multiple of bytesPerChecksum";
    
    String operation = null;
    String[] operationArray = null;
    HashMap<String,String> opHash = new HashMap<String,String>();
    for (int i = 0; i < args.length; i++) { // parse command line
      if (args[i].equals("-baseDir")) {
        baseDir = args[++i];
      } else if (args[i].equals("-numFiles")) {
        numFiles = Integer.parseInt(args[++i]);
      } else if (args[i].equals("-blocksPerFile")) {
        blocksPerFile = Integer.parseInt(args[++i]);
      } else if (args[i].equals("-bytesPerBlock")) {
        bytesPerBlock = Long.parseLong(args[++i]);
      } else if (args[i].equals("-bytesPerChecksum")) {
        bytesPerChecksum = Integer.parseInt(args[++i]);        
      } else if (args[i].equals("-startTime")) {
        startTime = Long.parseLong(args[++i]) * 1000;
        if(startTime <= 0){
        	startTime = System.currentTimeMillis()/1000;
        }
      } else if (args[i].equals("-operation")) {
        operation = args[++i];
        operationArray = operation.split(",");
        for(String tmp:operationArray){
        	String[] tmp2= tmp.split(":");
        	opHash.put(tmp2[0], tmp2[1]);
        }
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
    bytesPerFile = bytesPerBlock * blocksPerFile;
    
    JobConf jobConf = new JobConf(new Configuration(), NNBench.class);
    
    if ( bytesPerChecksum < 0 ) { // if it is not set in cmdline
      bytesPerChecksum = jobConf.getInt("io.bytes.per.checksum", 512);
    }
    jobConf.set("io.bytes.per.checksum", Integer.toString(bytesPerChecksum));
    
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
        numFiles < 1 )
      {
        System.err.println(usage);
        System.out.println("args:operation="+operation+" baseDir="+baseDir+" numFiles="+numFiles+
        		" blocksPerFile="+blocksPerFile+" bytesPerBlock="+bytesPerBlock+
        		" bytesPerBlock % bytesPerChecksum="+(bytesPerBlock % bytesPerChecksum));
        return -1;
      }
    String uniqueId = null;
    if(hostName != null){
    	uniqueId = hostName;
    }else{
    	uniqueId = java.net.InetAddress.getLocalHost().getHostName();
    }
    
    //taskDir = new Path(baseDir, uniqueId);
    //dfsTaskDir = baseDir+"/"+uniqueId;
    
     //add exec code .................
    int index=0;
    MetadataLock[] mdLockArray= new MetadataLock[opHash.size()];
    Iterator iter = opHash.entrySet().iterator();
    while(iter.hasNext()){
    	Map.Entry entry = (Map.Entry)iter.next();
    	String opCode = (String)entry.getKey();
    	String opDir = (String)entry.getValue();
        if(opCode.equals("createwrite")){
        	mdLockArray[index] = new MetadataLock("createwrite",opDir,numFiles,uniqueId);
        	mdLockArray[index].start();
        }else if(opCode.equals("mkdir")){
        	mdLockArray[index] = new MetadataLock("mkdir",opDir,numFiles,uniqueId);
        	mdLockArray[index].start();
        }else if(opCode.equals("lsdir")){
        	mdLockArray[index] = new MetadataLock("lsdir",opDir,numFiles,uniqueId);
        	mdLockArray[index].start();
        }else if(opCode.equals("openRead")){
        	mdLockArray[index] = new MetadataLock("openRead",opDir,numFiles,uniqueId);
        	mdLockArray[index].start();
        }else if(opCode.equals("delete")){
        	mdLockArray[index] = new MetadataLock("delete",opDir,numFiles,uniqueId);
        	mdLockArray[index].start();
       }else if(opCode.equals("rename")){
       	mdLockArray[index] = new MetadataLock("rename",opDir,numFiles,dfsTaskDir);
       	mdLockArray[index].start();
       }
        
        index++;
        Thread.sleep(100);    	
    	
    }
    
    
    while(true){
    	Thread.sleep(1000);
    }
    //for(;index<0;index--){
    //	mdLockArray[index].join();
    //}
    //return 0;
  }
}
