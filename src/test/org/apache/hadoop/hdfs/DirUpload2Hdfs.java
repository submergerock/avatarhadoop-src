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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
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
public class DirUpload2Hdfs extends Configured implements Tool {
  
  static class DirStruct{
	  public DirStruct(String inLocalDir,String outCloudDir){
		  this.inLocalDir = inLocalDir;
		  this.outCloudDir = outCloudDir;
	  }
	  public String  inLocalDir = null;
	  public String  outCloudDir = null;
  }
  private static ArrayList<DirStruct> outCloudDirArray = null;
  private static final Log LOG = LogFactory.getLog(
                                            "org.apache.hadoop.hdfs.NNBench");
  
  //private static String operation = null;
  private static String inbaseDir = null;
  private static String dataDir = null;
  private static String outCloudbaseDir = null;
  //private static int    blockSize = 1;
  private static int    numFiles  = -1;

  // variable initialzed from command line arguments
  private static long startTime = 0;
  private static long bytesPerBlock = 1;
  //private static long blocksPerFile = 0;
  private static long bytesPerFile = 1;
  //private static long sizePerFile = 1;
  //private static String hostName = null;
  //private static Path baseDir = null;
  private static long replicationFactorPerFile = 2;
  // variables initialized in main()
  //private static FileSystem fileSys = null;
  //private static Path taskDir = null;

  private static DFSClient dfsClient = null;
  //private static String    dfsTaskDir = null;
  private static String    baseDir = null;
  
  //private static byte[] buffer;
  private static long maxExceptionsPerFile = 2000;
  private static NNBenchDFSThread handleThread = null;    
  //private static int sectionInterval = 2;
  //private static int sectionNum = 2;
  private static Date execTime = null;
  private static Date endTime  = null;
  private static Random random = new Random();
  //private static int minWordsInValue = 10;
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
  
  static ArrayList<String> ayl = new ArrayList<String>();
  
  static void treeDir(String path,String tab){
      File file=new File(path); 
      File[] childFiles=file.listFiles();//找出所有子目录 
      for(int i=0; childFiles != null && i<childFiles.length; i++){ 
          //System.out.println(tab+childFiles[i].getName()); 
          if(childFiles[i].isDirectory()){//如果是目录的话，则调用自身 
        	  treeDir(childFiles[i].getPath(), tab+"\t"); 
          }else{
        	  String filePath = childFiles[i].getPath();
        	  if(filePath.endsWith(".cdr") ){
            	  ayl.add(filePath);        		  
        	  }
          }
      } 
  }
  /**
   * Create and write to a given number of files.  Repeat each remote
   * operation until is suceeds (does not throw an exception).
   *
   * @return the number of exceptions caught
   */
  static void createWrite() {
	  
	OutputStream out = null;
    boolean success = true;
    byte[] fileBuffer = new byte[64*1024];
    //首先递归扫描本地文件路径
    for(int i=0; i<outCloudDirArray.size(); i++){
    	DirStruct dirStruct = outCloudDirArray.get(i);
        treeDir(dirStruct.inLocalDir,"\t");
    }//end for
    //在for 中读取写入
    int subLength = inbaseDir.length();
    for(int j=0;j< numFiles && j< ayl.size();j++){
    	//System.out.println("numFiles="+numFiles+" j="+j);
    	int totalExceptions = 5;
    	try{
        	int singleFileExceptions = 0;
        	String localFullPath = ayl.get(j);
        	String cloudFullPath = localFullPath.substring(subLength, localFullPath.length());
        	System.out.println("cloudpath="+cloudFullPath);
        	do{//create file until is succeeds or max exceptions reached
                try {
              	out = (OutputStream)dfsClient.create(cloudFullPath, 
              			FsPermission.getDefault(),
                            false, 
                            (short)replicationFactorPerFile,
                            bytesPerBlock,
                            null,
                            4096
                            );
                  success = true;
                  //System.out.println("create file "+cloudFullPath+" success!");
                } catch (IOException ioe) { 
                  success=false; 
                  totalExceptions--;
                  //handleException("creating file #" + j, ioe,++singleFileExceptions);
                  System.out.println("creating file #" + j+" "+ioe);
                }
        	}while(!success && totalExceptions > 0);
        	
        	singleFileExceptions = 0;
            if(!success || totalExceptions <= 0){
         	   NNBenchDFSThread.createfailFileCount ++;
                  System.out.println("create fail," +  NNBenchDFSThread.createfailFileCount);
                  continue;
             }else{}
             
            //write to hdfs 
            try{
                File localFile = new File(localFullPath);
                FileInputStream fileStream = new FileInputStream(localFile);
                int bytesRead = fileStream.read(fileBuffer);
                totalReadBytes +=bytesRead;
                while(bytesRead >= 0){
                	  try{
            	    	  out.write(fileBuffer, 0, bytesRead);  
            	    	  //System.out.println("totalReadBytes="+totalReadBytes);
                	  }catch(IOException ioe){
                          //totalExceptions--;
                          //handleException("writing to file #" + j, ioe,++singleFileExceptions);
                          System.out.println("writing to file #" + j+" "+ioe);
                	  }
          	    	  bytesRead = fileStream.read(fileBuffer);
          	    	  totalReadBytes += bytesRead;
               }//end while
            }catch(IOException ioe){
                 System.out.println("read local file error"+ioe);	
            }
           RemoteFileDFSHandle fileHandle = new RemoteFileDFSHandle(cloudFullPath,out);
           handleThread.addHandle(fileHandle);
           if(j % 50 == 0){
        	    try{
        	    	System.gc();
        	    	Thread.sleep(1000*2);
        	    }catch(Exception er){
        	    	System.out.println(""+er);
        	    }
             	
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
    //return totalExceptions;
  }//end func
  
  /**
   * This launches a given namenode operation (<code>-operation</code>),
   * starting at a given time (<code>-startTime</code>).  The files used
   * by the openRead, rename, and delete operations are the same files
   * created by the createWrite operation.  Typically, the program
   * would be run four times, once for each operation in this order:
   * createWrite, openRead, rename, delete.
   *
   * @param args is an array of the program command line arguments
   * @throws IOException indicates a problem with test startup
   */
  //--目录上传到HDFS
  // /home/hadoop/app/avatar-hadoop/bin/hadoop jar /home/hadoop/app/avatar-hadoop/hadoop-0.20.1-dev-test.jar dirupload2hdfs "-inbaseDir /data02/cdrdata -dataDir /smp/cdr/ -bytesPerBlock 67108864 -replicationFactorPerFile 2 -numFiles 10000"
  
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new DirUpload2Hdfs(), args);
    System.exit(res);
  }

  @Override
  public int run(String[] args) throws Exception {

    String version = "Dirupload2hdfs 1.0";
    System.out.println(version);
    long bytesPerChecksum = -1;
    
    String usage =
      "Usage: dirupload2hdfs " +
      "  -inbaseDir <local base dir> " +
      "  -dataDir <local relation dir , split> " +
      "  -outbaseDir <HDFS basedir> " +
      "  -bytesPerBlock  <HDFS block size>"+
      "  -replicationFactorPerFile  <HDFS replication>"+
      "  -numFiles <number of files to create> ";
    
    for (int i = 0; i < args.length; i++) { // parse command line
      if (args[i].equalsIgnoreCase("-inbaseDir")) {
        this.inbaseDir = args[++i].trim();
      }else if(args[i].equalsIgnoreCase("-dataDir")){
    	  this.dataDir = args[++i].trim();
      }else if(args[i].equalsIgnoreCase("-outbaseDir")){
    	  this.outCloudbaseDir = args[++i].trim();
      }else if(args[i].equalsIgnoreCase("-bytesPerBlock")){
    	  this.bytesPerBlock = Integer.parseInt(args[++i].trim());
      }else if(args[i].equalsIgnoreCase("-replicationFactorPerFile")){
    	  this.replicationFactorPerFile = Integer.parseInt(args[++i].trim());
      }else if(args[i].equalsIgnoreCase("-numFiles")){
    	  this.numFiles = Integer.parseInt(args[++i].trim());
      }else {
        System.out.println(usage);
        for(int j = 0; j < args.length; j++){
        	System.out.println("args["+args[j]+"]");
        }
        return -1;
      }
    }
    
    JobConf jobConf = new JobConf(new Configuration(), DirUpload2Hdfs.class);
    
    System.out.println("   inbaseDir: " + this.inbaseDir);
    System.out.println("   dataDir: "+this.dataDir);
    System.out.println("   outCloudbaseDir: " + this.outCloudbaseDir);
    System.out.println("   bytesPerBlock: "+ this.bytesPerBlock);
    System.out.println("   replicationFactorPerFile: "+ this.replicationFactorPerFile);
    System.out.println("   numFiles: " + this.numFiles);
    
    if (this.inbaseDir == null || this.dataDir == null){
        System.err.println(usage);
       return -1;
    }
    dfsClient = new DFSClient(jobConf);
    //整理输出目录
    outCloudDirArray = new ArrayList<DirStruct>(); 
    String[] dataDirArray = this.dataDir.split(",");
    if(this.outCloudbaseDir == null){
    	for(String tempDir:dataDirArray){
    		DirStruct dirStruct = new DirStruct(this.inbaseDir + tempDir,tempDir);
    		outCloudDirArray.add(dirStruct);
    	}
    }else{
    	for(String tempDir:dataDirArray){
    		DirStruct dirStruct = new DirStruct(this.inbaseDir + tempDir,this.outCloudbaseDir+tempDir);
    		outCloudDirArray.add(dirStruct);
    	}
    	
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
    //create write
    createWrite();
    //..........................
    endTime = new Date();
    System.out.println("Job ended: " + endTime);
    duration = (endTime.getTime() - execTime.getTime()) /1000;
    System.out.println("The  job took " + duration + " seconds.");
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
  
}
