package com.dinglicom.com.hdfs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FastLog  extends Thread{
	public static final Log auditLog = LogFactory.getLog(
		       "mymanaged.class.FSNamesystem.audit");
	private Queue<String> auditLogQueue 
			   = new ConcurrentLinkedQueue<String>();
	static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static int getCount = 0;
	private boolean threadRunning = true;
	public FastLog(){}
    public void addAuditLog(String log){
    	if(log != null){
        	auditLogQueue.add(getNowDate()+":  "+log);//log 插入队列尾部    		
    	}
    }
    
    public String getAuditLog(){
    	String retLog = null;
    	synchronized(auditLog){
    		if(!auditLogQueue.isEmpty()){
    	    	return auditLogQueue.poll();    			
    		}else{
    			return null;
    		}
    	}
    }
    
    public void writeLog2Disk() throws InterruptedException {
    	String	retLog = this.getAuditLog();
    	if(retLog == null){
    		Thread.sleep(10);
    	}else{
    		if( (++getCount) % 10000000 == 0 ){
    		   auditLog.info(retLog+" auditLogQueue currentsize:"+auditLogQueue.size());
    		}else{
                auditLog.info(retLog);    			
    		}
        		
    	}
    }//end writeLog2Disk
    
    public void setLogThreadExit(){
    	this.threadRunning = false;
    }
    
    public void run(){
    	while(threadRunning){
    		try{
            	writeLog2Disk();    			
    		}catch(InterruptedException er){
    			//...
    		}
    	}
    }//end run
    public static String getNowDate(){
  	  Date currentTime = new Date();
  	  String dateString = formatter.format(currentTime);
  	  return dateString;    	
    }
    
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
	}

}
