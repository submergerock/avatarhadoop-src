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
//import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.hdfs.DFSClient.DFSOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;

public class NNBenchDFSThread extends Thread{
	  static DFSClient dfsThreadClient = null;
	  static HashMap<String,Integer> dnBlockHash = new HashMap<String,Integer>();
	  static int createfailFileCount = 0;
	  static int closefailFileCount = 0;
	  private boolean isEnd = true;
	  private static Queue<RemoteFileDFSHandle> remoteFileHandleQueue 
		= new ConcurrentLinkedQueue<RemoteFileDFSHandle>();

		static class RemoteFileDFSHandle{
			private String remoteFileName = "";
			private OutputStream remoteStreamHandle = null;
			
			public RemoteFileDFSHandle(String remoteFileName,
					OutputStream remoteStreamHandle) {
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
			public OutputStream getRemoteStreamHandle() {
				return remoteStreamHandle;
			}
			public void setRemoteStreamHandle(DFSOutputStream remoteStreamHandle) {
				this.remoteStreamHandle = remoteStreamHandle;
			}
		}
	  
    public NNBenchDFSThread(){
    	try{
            JobConf jobConf = new JobConf(new Configuration(), NNBenchDFSThread.class);    	
        	dfsThreadClient = new DFSClient(jobConf);
        	isEnd = false; 
    	}catch(Exception er){}
    
    }
    public void setEnd(boolean bEnd){
    	isEnd = bEnd;
    }
	public void addHandle(RemoteFileDFSHandle outHanle){
		remoteFileHandleQueue.offer(outHanle);
	}
	
	protected void batchCloseRemoteFile() throws IOException {
		RemoteFileDFSHandle outHanle = null;
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
			            if(totalClose % 10 == 0){
				            System.out.println("--------- "+remoteTmpFileName +" close success!!!");			            	
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
				           try{
			                	//查询文件的block分配在那些机器上
				        	   BlockLocation[] blks= NNBenchDFSThread.dfsThreadClient.getBlockLocations(remoteTmpFileName, 0, 0);
				        	   //System.out.println(" #####len="+blks.length);
		                       Integer dnCount=0;
				        	   for(BlockLocation blk:blks){
		                    	    String host = blk.getHosts()[0];
		                    	    
		                    	    if(( dnCount = (Integer)dnBlockHash.get(host))==null ){
		                    	    	dnBlockHash.put(host, 1);
		                    	    }else{
		                    	    	dnCount++;
		                    	    	dnBlockHash.put(host, dnCount);
		                    	    }
		                       }//end for
		                       
			            	}catch(IOException er){}
		            	
		            }
			      
			      totalClose++;
			}
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
		  try{
			  batchCloseRemoteFile();			  
		  }catch(Exception er){
			  System.out.println(er);
		  }

	   }//end while
	   System.out.println("thread close!!! hashsize="+dnBlockHash.size());
	   Iterator it = dnBlockHash.entrySet().iterator();
	   while(it.hasNext()){
		   Map.Entry entry = (Map.Entry)it.next();
		   String key   = (String)entry.getKey();
		   Integer value = (Integer)entry.getValue();
		   System.out.println("host:"+key+" count:"+value);
	   }//end while
    }//end while
	
}
