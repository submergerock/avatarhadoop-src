package org.apache.hadoop.hdfs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;

public class NNBenchThread extends Thread{
	  static int createfailFileCount = 0;
	  static int closefailFileCount = 0;
	  private boolean isEnd = true;
	  private static Queue<RemoteFileHandle> remoteFileHandleQueue 
		= new ConcurrentLinkedQueue<RemoteFileHandle>();

		static class RemoteFileHandle{
			private String remoteFileName = "";
			private FSDataOutputStream remoteStreamHandle = null;
			
			public RemoteFileHandle(String remoteFileName,
					FSDataOutputStream remoteStreamHandle) {
				super();
				this.remoteFileName = remoteFileName;
				this.remoteStreamHandle = remoteStreamHandle;
			}
			
			public String getRemoteFileName() {
				return remoteFileName;
			}
			public void setRemoteFileName(String remoteFileName) {
				this.remoteFileName = remoteFileName;
			}
			public FSDataOutputStream getRemoteStreamHandle() {
				return remoteStreamHandle;
			}
			public void setRemoteStreamHandle(FSDataOutputStream remoteStreamHandle) {
				this.remoteStreamHandle = remoteStreamHandle;
			}
		}
	  
    public NNBenchThread(){
        	isEnd = false; 
    }
    public void setEnd(boolean bEnd){
    	isEnd = bEnd;
    }
	public void addHandle(RemoteFileHandle outHanle){
		remoteFileHandleQueue.offer(outHanle);
	}
	
	protected void batchCloseRemoteFile() throws IOException {
		RemoteFileHandle outHanle = null;
	    boolean success;
	    int totalClose = 0;
		while ( (outHanle = remoteFileHandleQueue.poll()) != null ) {
			String remoteTmpFileName = outHanle.getRemoteFileName();
			OutputStream remoteHandleOp = outHanle.getRemoteStreamHandle();
			int totalExceptions=3;
			/*close the remote file*/
			if (null != remoteHandleOp) {
			      do { // close file until is succeeds
			          try {
						remoteHandleOp.close();
			            success = true;
			            if(totalClose % 100 == 0){
				            //System.out.println("--------- "+remoteTmpFileName +" close success!!!");			            	
			            }

			          } catch (IOException ioe) {
			            success=false; 
			            totalExceptions--;
			            System.out.println("closing file " + remoteTmpFileName+"\n"+ioe);
			          }catch(OutOfMemoryError ooMIe){
				            success=false; 
				            totalExceptions--;
				            System.out.println("System gc " + remoteTmpFileName+"\n"+ooMIe);
				            System.gc();
				            try{
				            	Thread.sleep(10*1000);
				            }catch(Exception er){}
			          }
			          
			        } while (!success && totalExceptions > 0);

		           if(!success || totalExceptions <= 0){
		        	   closefailFileCount ++;
		                 System.out.println("close fail," +  closefailFileCount); 	
		            }else{
		            }//end else
			      totalClose++;
			}//end fi
		}//end while

	}//end func
		
	public void run(){
	   while (!isEnd) {
		  try{
		    batchCloseRemoteFile();
		    Thread.sleep(100);
	      }catch(Exception er){
		    System.out.println(er);
	      }
		  
	   }//end while
	   System.out.println("thread close!!!");
    }//end run
	
}
