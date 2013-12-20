package org.apache.hadoop.hdfs;

import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DFSClient.DFSInputStream;
import org.apache.hadoop.hdfs.NNBenchDFSThread.RemoteFileDFSHandle;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.StringUtils;

public class MetadataLock extends Thread{

	  private static long startTime = 0;
	  private static int numFiles = 2000;
	  private static long bytesPerBlock = 1024;
	  private static long blocksPerFile = 1024;
	  private static long bytesPerFile = 1;
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
	  private static String selectFuncNo = null;  
	  private static int SLEEP_TIME = 2;
	  private static String hostName = null;
	  /**
	   * Returns when the current number of seconds from the epoch equals
	   * the command line argument given by <code>-startTime</code>.
	   * This allows multiple instances of this program, running on clock
	   * synchronized nodes, to start at roughly the same time.
	   */
    
	  static private void handleException(String operation, Throwable e, 
	                                      int singleFileExceptions) {
		System.out.println("handleexception while " + operation + ": " +
	            StringUtils.stringifyException(e));  
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
	 * @throws InterruptedException 
	   */
	  private int createWrite() throws InterruptedException {
		OutputStream out = null;
	    boolean success;
	    int totalExceptions = 0;    
	    for (int index = 0; index < numFiles; index++) {
	      int singleFileExceptions = 0;
    	  totalExceptions  = 5;
	      String createFilePath = null;
	      try{
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
	                handleException("creating file #" + index+" totalexception="+totalExceptions, ioe,++singleFileExceptions);
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
	              if(index % sectionNum  == 0){
	                  try{
	                  	Thread.sleep(sectionInterval*1000);
	                  }catch(Exception er){
	                  	System.err.println(er);
	                  }
	              	
	              }
	              if(index % 500 == 0){
	              	System.gc();
	              }
	    	      Thread.sleep(SLEEP_TIME);
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
	 * @throws InterruptedException 
	   */
	  private int openRead() throws InterruptedException {
	    int totalExceptions = 0;
	    DFSInputStream in;
	    for (int index = 0; index < numFiles; index++) {
	      int singleFileExceptions = 0;
	      try {
	    	String inPath = dfsTaskDir+ "/" + index;
	    	if(!dfsClient.exists(inPath)){
	    		index--;
	    		continue;
	    	}
	    		
	    	in =(DFSInputStream)dfsClient.open(inPath);
	        //in = fileSys.open(new Path(taskDir, "" + index), 512);
	        long toBeRead = bytesPerFile;
	        while (toBeRead > 0) {
	          int nbytes = (int) Math.min(buffer.length, toBeRead);
	          toBeRead -= nbytes;
	          try { // only try once && we don't care about a number of bytes read
	            in.read(buffer, 0, nbytes);
	          } catch (IOException ioe) {
	            totalExceptions++;
	            handleException("reading from file #" + index, ioe,
	                    ++singleFileExceptions);
	          }
	        }//end while
	        in.close();
	        Thread.sleep(SLEEP_TIME);
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
	 * @throws InterruptedException 
	   */
	  private int rename() throws InterruptedException {
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
	          String srcPath = dfsTaskDir+"/" + index;
	          dfsClient.rename(dfsTaskDir+"/" + index, dfsTaskDir+"/A" + index);
	          success = true;
	        } catch (IOException ioe) { 
	          success=false; 
	          totalExceptions++;
	          handleException("creating file #" + index, ioe, ++singleFileExceptions);
	       }
	      } while (!success);
	      Thread.sleep(SLEEP_TIME);
	    }
	    return totalExceptions;
	  }
	    
	  /**
	   * Delete a given number of files.  Repeat each remote
	   * operation until is suceeds (does not throw an exception).
	   *
	   * @return the number of exceptions caught
	 * @throws InterruptedException 
	   */
	  private int delete() throws InterruptedException {
	    int totalExceptions = 5;
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
	          dfsClient.delete(dfsTaskDir +"/"+ index, true);
	          success = true;
	        } catch (IOException ioe) { 
	          success=false; 
	          totalExceptions--;
	          handleException("creating file #" + index, ioe, ++singleFileExceptions);
	        }
	      } while (!success && totalExceptions > 0);
	      Thread.sleep(100);
	    }
	    return totalExceptions;
	  }
	  
	  //mkdir
	  private int mkdir() throws InterruptedException{
		    int totalExceptions = 0;
		    boolean success = false;
	        for(int index=0;index < numFiles;index++){
	        	int singleFileExceptions = 0;
	        	do{
	        		try{
	        			dfsClient.mkdirs(dfsTaskDir+"/LOCK" + index);
	        			success = true;
	        		}catch(IOException ioe){
	        	          success=false; 
	        	          totalExceptions++;
	        	          handleException("creating file #" + index, ioe, ++singleFileExceptions);
	        		}
	        	}while(!success);
	        	if(index % 100 == 0){
	        		System.out.println("mkdir success #"+index);
	        	}
	        	Thread.sleep(50);
	        }//end for
	        return totalExceptions;
	  }

	  private int lsdir() throws InterruptedException{
		    int totalExceptions = 0;
		    int singleFileExceptions = 0;
		    boolean success = false;
		    for(int index=1;index < numFiles;index++){
			    try{
					FileStatus[] fsArray = dfsClient.listPaths(dfsTaskDir);
					for(FileStatus fs:fsArray){
						FileStatus fsl = dfsClient.getFileInfo(fs.getPath()+"");
						//System.out.println("ls file: "+fsl.toString());
					}
					if(index % 100 == 0){
						System.out.println("lsdir #"+index);
					}
			    }catch(IOException ioe){
			    	totalExceptions++;
			          handleException("ls file error", ioe, ++singleFileExceptions);
			    }
			    Thread.sleep(500);
		    }//end for
	       return totalExceptions;
	}
	  
    public MetadataLock(String selectFuncNo,String baseDir,int numFiles,String hostName) throws IOException{
    	bytesPerFile = blocksPerFile;
        buffer = new byte[(int) Math.min(bytesPerFile, 32768L)];
        for(int k=0;k<buffer.length;k++){
        	int tmpasc = k - '0';
        	buffer[k]=(byte)tmpasc;
        }//end for
   	
    	this.selectFuncNo = selectFuncNo;
        String uniqueId = java.net.InetAddress.getLocalHost().getHostName();
        //taskDir = new Path(baseDir, uniqueId);
        this.numFiles = numFiles;
        this.baseDir = baseDir;
        //this.dfsTaskDir = baseDir+"/"+uniqueId;
        this.dfsTaskDir = baseDir+"/"+hostName;
        JobConf jobConf = new JobConf(new Configuration(), MetadataLock.class);
        dfsClient = new DFSClient(jobConf);
        if(!dfsClient.mkdirs(dfsTaskDir)){
            throw new IOException("Mkdirs failed to create " + dfsTaskDir);    	
        }
                
        handleThread = new NNBenchDFSThread();
        handleThread.start();
    }
    public void stopCloseThread(){
        handleThread.setEnd(true);
        try{
            handleThread.join();    	
        }catch(Exception er){
        	System.err.println(er);
        }
        
    }
    
	public void run(){
		Date execTime = new Date();
		try{
	        if(this.selectFuncNo.equals("createwrite")){
	        	this.createWrite();
	        }else if(this.selectFuncNo.equals("rename")){
	        	this.rename();
	        }else if(this.selectFuncNo.equals("openRead")){
	        	this.openRead();
	        }else if(this.selectFuncNo.equals("mkdir")){
	        	this.mkdir();
	        }else if(this.selectFuncNo.equals("lsdir")){
	        	this.lsdir();
	        }else if(this.selectFuncNo.equals("delete")){
	        	this.delete();
	        }
	        stopCloseThread();
	        endTime = new Date();
	        System.out.println("Job ended: " + endTime);
	        long duration = (endTime.getTime() - execTime.getTime()) /1000;
	        System.out.println("The " + selectFuncNo + " job took " + duration + " seconds.");	        
		}catch(Exception er){
			
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
