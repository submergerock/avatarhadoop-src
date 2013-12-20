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
package org.apache.hadoop.hdfs.job;

import java.io.IOException;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.text.SimpleDateFormat;
import java.lang.Thread;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.InetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.io.*;
import org.apache.hadoop.ipc.*;
import org.apache.hadoop.job.tools.getHostIP;
import org.apache.hadoop.job.tools.tools;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.permission.*;
//import org.apache.hadoop.hdfs.job.DoINDEX1;
//import org.apache.hadoop.hdfs.job.DoINDEX2;
//import org.apache.hadoop.hdfs.job.DoNNDIR;
//import org.apache.hadoop.hdfs.job.DoSEARCH;
import org.apache.hadoop.hdfs.job.Job;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.hdfs.protocol.ClientDatanodeProtocol;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.FSConstants;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.DatanodeID;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.hdfs.protocol.AvatarProtocol;
import org.apache.hadoop.hdfs.protocol.AvatarConstants.Avatar;
import org.apache.hadoop.hdfs.protocol.AvatarConstants.InstanceId;
import org.apache.hadoop.hdfs.protocol.FSConstants.DatanodeReportType;
import org.apache.hadoop.hdfs.server.protocol.NamenodeProtocol;
import org.apache.hadoop.hdfs.server.namenode.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.FSEditLog;
import org.apache.hadoop.hdfs.server.namenode.AvatarNode;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.http.HttpServer;

/**
 * This class drives the ingest of transaciton logs from primary.
 * It also implements periodic checkpointing of the  primary namenode.
 */

public class MyJob  implements Runnable{

  public static final Log LOG = LogFactory.getLog(MyJob.class.getName());
  private ArrayBlockingQueue<Job> jobQueue = new ArrayBlockingQueue<Job>(50);
   
  private Configuration confg; // configuration of local standby namenode
  private NameNode nameNode = null;
  private DataNode dataNode = null;
  volatile private boolean running;
  private int ThreadIndex = 0 ;
  private int ThreadTotal = 0 ;
  //private HashMap<String,HashMap<Class<? extends JobProtocol>,RunJob>> jobIdtoRunJob 
  			//= new HashMap<String,HashMap<Class<? extends JobProtocol>,RunJob>>();
  private HashMap<Class<? extends JobProtocol>,RunJob> jobClasstoRunJob 
	= new HashMap<Class<? extends JobProtocol>,RunJob> ();
  
  
  public MyJob(NameNode nameNode, Configuration conf) {
	    this(nameNode, null,conf,new ArrayBlockingQueue<Job>(conf.getInt("hdfs.job.tatol", 20)),true);
  }
  
  public MyJob(DataNode dataNode, Configuration conf) {
	    this(null, dataNode,conf,new ArrayBlockingQueue<Job>(conf.getInt("hdfs.job.tatol", 20)),true);
	  }
  public MyJob(NameNode nameNode, 
		       DataNode dataNode,
		       Configuration conf,
		       ArrayBlockingQueue<Job> jobQueue,
		       boolean running){	    
	    this.nameNode = nameNode;
	    this.dataNode = dataNode;
	    this.confg = conf;      
	    this.jobQueue = jobQueue;
	    this.running = running;
	   // this.jobIdtoRunJob = new HashMap<String,HashMap<Class<? extends JobProtocol>,RunJob>>();
	    this.jobClasstoRunJob = new HashMap<Class<? extends JobProtocol>,RunJob> ();
  }

  public void run() {
	  LOG.info("MyJob thread startup!!!");
    while (running) {
    	runJob(takeJob());
    	tools.sleep(1000);
    	LOG.info("MyJob is running!!!");
    }
  }
  private class RunJob  implements Runnable{
	  private JobProtocol job = null;
	  private Job j = null;
	  private int ThreadIndex ;
	  private ArrayBlockingQueue<Job> jobQueue = new ArrayBlockingQueue<Job>(50);
	  volatile private boolean running;
	  private boolean isNN = true;
	  ArrayList<HashMap<String,StringBuffer>> DNstoJobsParamArrayList 
	          = new ArrayList<HashMap<String,StringBuffer>>();
	  
	  RunJob(Job j,int ThreadIndex,boolean isNN){
		  this.j = j;
		  this.ThreadIndex = ThreadIndex;
		  this.running = true;
		  this.isNN = isNN;
		  this.putRunningJob(j);
	  }
	
	@Override
	public void run() {	
		LOG.info("------RunJob.run()------");
		ThreadTotal++;
	    LOG.info("Start a new job in Thread " + this.ThreadIndex + ",ThreadTotal = " + ThreadTotal);
	    HashMap<String,StringBuffer> DNstoJobsParam = null;
//	    int loop = 0;
//	    
//	    if(this.isNN){
//	    	loop = 1;
//	    } else {    
//		 if(this.j.getConf().getInt("hdfs.job.loop",0) == 0){
//			 loop = 1;
//			this.j.getConf().setInt("hdfs.job.loop",this.j.getConf().getInt("hdfs.job.DNTotal",0));
//		  } else {
//			loop = this.j.getConf().getInt("hdfs.job.loop",0);
//		  }
//	    }
		 
//	    int i = 0;
		while(this.running){
			DNstoJobsParam = handle();
			if(DNstoJobsParam != null){
				reduce(DNstoJobsParam);
			} else {
				continue;
			}
//			i++;
//			if(isNN && i == 1 || !isNN && i == loop){
//				reduce(DNstoJobsParamArrayList);
//				i = 0;
//			}
		}    	
    	ThreadTotal--;
    	LOG.info("+++++RunJob.run()+++++");
	}
	public void run2() {	
		LOG.info("------RunJob.run()------");
		ThreadTotal++;
	    LOG.info("Start a new job in Thread " + this.ThreadIndex + ",ThreadTotal = " + ThreadTotal);
	    
	    int loop = 0;
	    
	    if(this.isNN){
	    	loop = 1;
	    } else {    
		 if(this.j.getConf().getInt("hdfs.job.loop",0) == 0){
			 loop = 1;
			this.j.getConf().setInt("hdfs.job.loop",this.j.getConf().getInt("hdfs.job.DNTotal",0));
		  } else {
			loop = this.j.getConf().getInt("hdfs.job.loop",0);
		  }
	    }
		 
	    int i = 0;
		while(this.running){
			handle();
			i++;
			if(isNN && i == 1 || !isNN && i == loop){
				reduce2(DNstoJobsParamArrayList);
				i = 0;
			}
		}    	
    	ThreadTotal--;
    	LOG.info("+++++RunJob.run()+++++");
	}
	public HashMap<String,StringBuffer> handle(){
		 LOG.info("------RunJob.handle()------");
		 HashMap<String,StringBuffer> DNstoJobsParam = null;
		 
		 this.j = takeRunningJob();
	     if(this.running == false)
	    	 return null;
		 Class<? extends JobProtocol> jobClass
        = this.j.getConf().getClass("hdfs.job.class",null, JobProtocol.class);    
		
   	 if(jobClass == null){
   	 	 return null;
   	 } else {
   		 LOG.info("jobClass.getName() : " + jobClass.getName());
   	 }
   	
   	 job = (JobProtocol) ReflectionUtils.newInstance(jobClass,confg);
	   
   	 try{
   	 job.setJob(this.j);
   	 job.setConfiguration(confg);
   	 job.setNameNode(nameNode);
   	 job.setDataNode(dataNode);    	
   	 DNstoJobsParam = job.handle();     	 	    	  	
   	 } catch (Exception e1) {
   		 e1.printStackTrace();
   	 }
   	 LOG.info("+++++RunJob.handle()+++++");
   	 return DNstoJobsParam;
	}
	
	
	public void handle2(){
		 LOG.info("------RunJob.handle()------");
		 HashMap<String,StringBuffer> DNstoJobsParam = null;
		 
		 this.j = takeRunningJob();
	     if(this.running == false)
	    	 return;
		 Class<? extends JobProtocol> jobClass
         = this.j.getConf().getClass("hdfs.job.class",null, JobProtocol.class);    
		
    	 if(jobClass == null){
    	 	 return ;
    	 } else {
    		 LOG.info("jobClass.getName() : " + jobClass.getName());
    	 }
    	
    	 job = (JobProtocol) ReflectionUtils.newInstance(jobClass,confg);
	   
    	 try{
    	 job.setJob(this.j);
    	 job.setConfiguration(confg);
    	 job.setNameNode(nameNode);
    	 job.setDataNode(dataNode);    	
    	 DNstoJobsParam = job.handle();  
    	 if(DNstoJobsParam != null){
    		 DNstoJobsParamArrayList.add(DNstoJobsParam); 
    	 }		    	  	
    	 } catch (Exception e1) {
    		 e1.printStackTrace();
    	 }
			
		 LOG.info("+++++RunJob.handle()+++++");
	}
	
	 public void NNrun(){
		    LOG.info("------RunJob.NNrun()------");
		    ArrayList<HashMap<String,StringBuffer>> DNstoJobsParamArrayList = new ArrayList<HashMap<String,StringBuffer>>();
		    HashMap<String,StringBuffer> DNstoJobsParam = null;
		    Job j = takeRunningJob();
	    	try{
	    	job.setJob(j);
	    	job.setConfiguration(confg);
	    	job.setNameNode(nameNode);
	    	job.setDataNode(dataNode);    	
	    	DNstoJobsParam = job.handle();    
	    	DNstoJobsParamArrayList.add(DNstoJobsParam);
	    	} catch (Exception e1) {
	    		e1.printStackTrace();
	    	}
	    	reduce2(DNstoJobsParamArrayList);
	    	LOG.info("+++++RunJob.NNrun()+++++");
	 }
	 public void DNrun(){
		 LOG.info("------RunJob.DNrun()------");
		 int loop = 0;
		 if(this.j.getConf().getInt("hdfs.job.loop",0) == 0){
			loop = 1;
			this.j.getConf().setInt("hdfs.job.loop",this.j.getConf().getInt("hdfs.job.DNTotal",0));
		  } else {
			loop = this.j.getConf().getInt("hdfs.job.loop",0);
		  }

		 int i = 0;
		 ArrayList<HashMap<String,StringBuffer>> DNstoJobsParamArrayList = new ArrayList<HashMap<String,StringBuffer>>();
		 HashMap<String,StringBuffer> DNstoJobsParam = null;
		 
		 //LOG.info("loop = " + loop);
		 while (true) {
			    Job j = takeRunningJob();
			    if(this.running == false)
			    	break;
		    	try{
		    	job.setJob(j);
		    	job.setConfiguration(confg);
		    	job.setNameNode(nameNode);
		    	job.setDataNode(dataNode);    	
		    	DNstoJobsParam = job.handle();  
		    	if(DNstoJobsParam != null){
		    		DNstoJobsParamArrayList.add(DNstoJobsParam); 
		    	}		    	  	
		    	} catch (Exception e1) {
		    		e1.printStackTrace();
		    	}
		    	i++;
			}
		 reduce2(DNstoJobsParamArrayList);
		 LOG.info("+++++RunJob.DNrun()+++++");
	 }
	 
	 public void reduce(HashMap<String,StringBuffer> DNtoJobsParam){		 
		 LOG.info("-----RunJob.reduce()-----");
		 ClientDatanodeProtocol datanode = null;
		 InetSocketAddress dataNodeAddr = null;		
		
		 Set<String> DNs = DNtoJobsParam.keySet();
		 
		 		 
		 for(String DN : DNs){
			 dataNodeAddr = NetUtils.createSocketAddr(DN);
				try {
					datanode = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
							ClientDatanodeProtocol.versionID, dataNodeAddr, confg,
					        NetUtils.getSocketFactory(confg, ClientDatanodeProtocol.class));
					LOG.info("DN : " + DN);
					//LOG.info("DNtoJobsParam.get(DN) : " + DNtoJobsParam.get(DN));
					
					this.j.getConf().set("hdfs.job.param.system.param", DNtoJobsParam.get(DN).toString());
					if(this.isNN){
						this.j.getConf().set("hdfs.job.NN.ip", getHostIP.getLocalIP() + ":" + nameNode.getNameNodeAddress().getPort());
					}
					//LOG.info("hdfs.job.param.system.param : " + this.j.getConf().get("hdfs.job.param.system.param"));
					datanode.submitJob(j);
				} catch (IOException e) {
					e.printStackTrace();
				}
		 }
		 LOG.info("+++++RunJob.reduce()+++++");
	 }
	 public void reduce2(ArrayList<HashMap<String,StringBuffer>> DNstoJobsParamArrayList){		 
		 LOG.info("-----RunJob.reduce()-----");
		 
		 HashMap<String,StringBuffer> DNtoJobsParam = new HashMap<String,StringBuffer>();
		 ClientDatanodeProtocol datanode = null;
		 InetSocketAddress dataNodeAddr = null;			
			
		 if(DNstoJobsParamArrayList.size() <= 0)
			 return;
		 for(HashMap<String,StringBuffer> DNstoJobsParam : DNstoJobsParamArrayList){
			 Set<String> DNs = DNstoJobsParam.keySet();
			 for(String DN : DNs){
				 if(DNtoJobsParam.get(DN) == null){
					 DNtoJobsParam.put(DN, DNstoJobsParam.get(DN));
				 } else {
					 DNtoJobsParam.put(DN, DNtoJobsParam.get(DN).append(",").append(DNstoJobsParam.get(DN)));
				 }
			 }
		 }
		 Set<String> DNs = DNtoJobsParam.keySet();
		 
		 for(String DN : DNs){
			 dataNodeAddr = NetUtils.createSocketAddr(DN);
				try {
					datanode = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
							ClientDatanodeProtocol.versionID, dataNodeAddr, confg,
					        NetUtils.getSocketFactory(confg, ClientDatanodeProtocol.class));
					LOG.info("DN : " + DN);
					//LOG.info("DNtoJobsParam.get(DN) : " + DNtoJobsParam.get(DN));
					
					this.j.getConf().set("hdfs.job.param.system.param", DNtoJobsParam.get(DN).toString());
					if(this.isNN){
						this.j.getConf().set("hdfs.job.NN.ip", getHostIP.getLocalIP() + ":" + nameNode.getNameNodeAddress().getPort());
					}
						
					datanode.submitJob(j);
				} catch (IOException e) {
					e.printStackTrace();
				}
		 }
		 LOG.info("+++++RunJob.reduce()+++++");
	 }
	
	 public void stop(){		 
		 this.running = false;
		 putRunningJob(this.j);
		 job.stop();
	  }
//	 private void removeJobClass(){
//		 HashMap<Class<? extends JobProtocol>,RunJob> jobClasstoRunJob = jobIdtoRunJob.get(this.j.getJobId());
//		 if(jobClasstoRunJob != null){
//			 jobClasstoRunJob.remove(this.j.getConf().getClass("hdfs.job.class",null, JobProtocol.class));
//		 }
//	 }
	 public void putRunningJob(Job e){
		   try {
			this.jobQueue.put(e);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	   }
	 public Job takeRunningJob(){
		   Job e = null;
	    try {
			e = this.jobQueue.take();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		return e;
	   }
  }
  private void runJob(Job j){
	  LOG.info("-----MyJob.runJob(Job j)-----");
	  Thread myJobThread;
	  RunJob runJob;
	  boolean isNN = false;
	  //in NN,find all DNIP and set them in conf
	  if(this.nameNode != null && this.dataNode == null){
		  ArrayList<DatanodeDescriptor> datanodeDescriptors 
			= this.nameNode.namesystem.getDatanodeListForReport(DatanodeReportType.ALL);
		  j.getConf().setInt("hdfs.job.DNTotal", datanodeDescriptors.size());
		  
		  j.getConf().set("hdfs.job.NNIP", getHostIP.getLocalIP());
		 
		  String DNIPs = "";
		  for(DatanodeDescriptor datanodeDescriptor : datanodeDescriptors){
			  if(DNIPs == ""){
				  DNIPs = DNIPs + datanodeDescriptor.getHost() + ":" + datanodeDescriptor.getIpcPort();
			  } else {
				  DNIPs = DNIPs + "," + datanodeDescriptor.getHost() + ":" + datanodeDescriptor.getIpcPort();
			  }
		  }
		  j.getConf().set("hdfs.job.DNIPs",DNIPs);
		  LOG.info("datanodeDescriptors.size() : " + datanodeDescriptors.size());
		  LOG.info("DNIPs : " + DNIPs);
		  isNN = true;
	  }
	  
//	  while(this.ThreadTotal > 16){
//		  tools.sleep(3000);
//	  }
	  LOG.info("j.getJobName() : " + j.getJobName());
	  LOG.info("j.getJobId() : " + j.getJobId());  	
	  
	  if(this.jobClasstoRunJob.get(j.getConf().getClass("hdfs.job.class",null, JobProtocol.class)) == null){
		  //LOG.info("+++++MyJob.runJob(Job j)+++++333");
		  runJob = new RunJob(j,this.ThreadIndex++,isNN);
	  	  myJobThread = new Thread(runJob);
	  	  myJobThread.start();
	  	this.jobClasstoRunJob.put(j.getConf().getClass("hdfs.job.class",null, JobProtocol.class), runJob); 
	  } else {
		  //LOG.info("+++++MyJob.runJob(Job j)+++++444");
		  runJob = this.jobClasstoRunJob.get(j.getConf().getClass("hdfs.job.class",null, JobProtocol.class));
		  runJob.putRunningJob(j);
	  }
	
	 LOG.info("+++++MyJob.runJob(Job j)+++++");
  }
//  
//  public void stopJob(Job j) {
//	    LongWritable jobId = j.getJobId();
//		LOG.info("stop job : " + jobId);
//		HashMap<Class<? extends JobProtocol>,RunJob>  runJobs = this.jobIdtoRunJob.remove(jobId);
//		if(runJobs == null){
//			LOG.info("job with id " + jobId + "has stoped!!!");
//			return;
//		} else {
//			for(RunJob runJob: runJobs.values()){
//				runJob.stop();	
//			}			
//			if(this.nameNode != null && this.dataNode == null){
//				  Thread stopJobThread;
//				  StopJob stopJob = new StopJob(j,this.nameNode,this.confg);
//				  stopJobThread = new Thread(stopJob);
//				  stopJobThread.start();
//			}
//			
//		}
//	}
  private class StopJob  implements Runnable{
	  private Job j;
	  private NameNode nameNode = null;
	  private Configuration confg = null;
	  StopJob(Job j,NameNode nameNode,Configuration confg){
		  this.j = j;
		  this.nameNode = nameNode;
		  this.confg = confg;
	  }
	
	@Override
	public void run() {
		ClientDatanodeProtocol datanode = null;
		InetSocketAddress dataNodeAddr = null;
		if(this.nameNode == null)
			return;
		ArrayList<DatanodeDescriptor> datanodeDescriptors 
			= this.nameNode.namesystem.getDatanodeListForReport(DatanodeReportType.ALL);
		for(DatanodeDescriptor datanodeDescriptor: datanodeDescriptors){
			dataNodeAddr = NetUtils.createSocketAddr(datanodeDescriptor.getHost(), datanodeDescriptor.getIpcPort());
			try {
				datanode = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
						ClientDatanodeProtocol.versionID, dataNodeAddr, this.confg,
				        NetUtils.getSocketFactory(this.confg, ClientDatanodeProtocol.class));
				datanode.stopJob(j);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	 
	  
  }
  public void stop(){
	  this.running = false;
  }
  
   public void putJob(Job e){
	   try {
		this.jobQueue.put(e);
	} catch (InterruptedException e1) {
		e1.printStackTrace();
	}
   }
   public Job takeJob(){
	   Job e = null;
	   try {
		e = this.jobQueue.take();
	} catch (InterruptedException e1) {
		e1.printStackTrace();
	}
	return e;
   }
   //----------------------------------bak-----------------------------------
//   private void runJob2(Job j){
//		  LOG.info("-----MyJob.runJob(Job j)-----");
//		  Thread myJobThread;
//		  RunJob runJob;
//		  boolean isNN = false;
//		  if(this.nameNode != null && this.dataNode == null){
//			  ArrayList<DatanodeDescriptor> datanodeDescriptors 
//				= this.nameNode.namesystem.getDatanodeListForReport(DatanodeReportType.ALL);
//			  j.getConf().setInt("hdfs.job.DNTotal", datanodeDescriptors.size());
//			  
//			  j.getConf().set("hdfs.job.NNIP", getHostIP.getLocalIP());
//			 
//			  String DNIPs = "";
//			  for(DatanodeDescriptor datanodeDescriptor : datanodeDescriptors){
//				  if(DNIPs == ""){
//					  DNIPs = DNIPs + datanodeDescriptor.getHost() + ":" + datanodeDescriptor.getIpcPort();
//				  } else {
//					  DNIPs = DNIPs + "," + datanodeDescriptor.getHost() + ":" + datanodeDescriptor.getIpcPort();
//				  }
//			  }
//			  j.getConf().set("hdfs.job.DNIPs",DNIPs);
//			  LOG.info("datanodeDescriptors.size() : " + datanodeDescriptors.size());
//			  LOG.info("DNIPs : " + DNIPs);
//			  isNN = true;
//		  }
//		  while(this.ThreadTotal > 16){
//			  tools.sleep(3000);
//		  }
//		  LOG.info("j.getJobId() : " + j.getJobId());
//	  		if(this.jobIdtoRunJob.get(j.getJobId()) == null){
//	  			//LOG.info("+++++MyJob.runJob(Job j)+++++111");
//	  			
//	  		  HashMap<Class<? extends JobProtocol>,RunJob> jobClasstoRunJob = 
//		  			new HashMap<Class<? extends JobProtocol>,RunJob>();
//		  		
//		  	  Class<? extends JobProtocol> jobClass
//		        = j.getConf().getClass("hdfs.job.class",null, JobProtocol.class);
//	  		  
//	  	  	  runJob = new RunJob(j,this.ThreadIndex++,isNN);
//	  	  	  myJobThread = new Thread(runJob);
//	  	  	  myJobThread.start();
//	  	  	  
//	  	  	  jobClasstoRunJob.put(jobClass, runJob);  	  	  
//	  	  	  
//	  	  	 // this.jobIdtoRunJob.put(j.getJobId(), jobClasstoRunJob);
//	  		} else {  
//	  		  //LOG.info("+++++MyJob.runJob(Job j)+++++222");
//	  		  HashMap<Class<? extends JobProtocol>,RunJob> jobClasstoRunJob = jobIdtoRunJob.get(j.getJobId());
//	  		  
//	  		  if(jobClasstoRunJob.get(j.getConf().getClass("hdfs.job.class",null, JobProtocol.class)) == null){
//	  			  //LOG.info("+++++MyJob.runJob(Job j)+++++333");
//	  			  runJob = new RunJob(j,this.ThreadIndex++,isNN);
//	    	  	  myJobThread = new Thread(runJob);
//	    	  	  myJobThread.start();
//	    	  	  jobClasstoRunJob.put(j.getConf().getClass("hdfs.job.class",null, JobProtocol.class), runJob); 
//	  		  } else {
//	  			  //LOG.info("+++++MyJob.runJob(Job j)+++++444");
//	  			  runJob = jobClasstoRunJob.get(j.getConf().getClass("hdfs.job.class",null, JobProtocol.class));
//	  			  runJob.putRunningJob(j);
//	  		  }
//	  		}
//	  		 LOG.info("+++++MyJob.runJob(Job j)+++++");
//	  }
}
