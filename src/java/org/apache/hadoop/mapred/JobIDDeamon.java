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
package org.apache.hadoop.mapred;

import java.io.IOException;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.*;
import java.nio.channels.FileChannel;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.text.SimpleDateFormat;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.io.*;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.mapred.JobClient.ZooKeeperWatcher;
import org.apache.hadoop.mapred.util.tools;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.fs.permission.*;
import org.apache.hadoop.hdfs.protocol.FSConstants;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.DatanodeID;
import org.apache.hadoop.security.UnixUserGroupInformation;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.hdfs.protocol.AvatarProtocol;
import org.apache.hadoop.hdfs.protocol.AvatarConstants.Avatar;
import org.apache.hadoop.hdfs.protocol.AvatarConstants.InstanceId;
import org.apache.hadoop.hdfs.server.namenode.AvatarNode;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.FSEditLog;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 * This class reads transaction logs from the primary's shared device
 * and feeds it to the standby NameNode.
 */

public  class JobIDDeamon implements Runnable {
  private static JobSubmissionProtocol jobSubmitClient;
  public static final Log LOG = LogFactory.getLog(JobIDDeamon.class.getName());
  static final SimpleDateFormat DATE_FORM =
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private JobTracker tracker = null;
  private JobTrackerZK jobTrackerZK = null;
  private ZooKeeper zooKeeper = null;
  public String jobIDAddressZNode  = "/jobid";
  private List<String> jobIDList= null;
  private JobConf conf = null;
  private volatile boolean running = true;
  private Map<JobID, Integer> jobRetryTime = 
	    new TreeMap<JobID, Integer>();

  final AtomicBoolean clusterHasActiveJT = new AtomicBoolean(false);
  private ZooKeeperWatcher zooKeeperWatcher;	  
  
  int timeout = 30000;
  String[] ZKHost = null;
	int ZKPort = 2181;
	
  //private ArrayList<JobID>
  
  public JobIDDeamon(JobTrackerZK jobTrackerZK,JobConf conf){
	  this.jobTrackerZK = jobTrackerZK;
	  this.zooKeeperWatcher = new ZooKeeperWatcher(this.clusterHasActiveJT);	     
	  timeout = conf.getInt("zookeeper.session.timeout", 20 * 1000);
	  this.jobIDList = new ArrayList<String>();
	  this.conf = conf;	    
	  try {		  
		  jobSubmitClient = createRPCProxy(JobTracker.getAddress(conf), conf);
		} catch (IOException e1) {
			e1.printStackTrace();
		}	  
  }
	@Override
	public void run() {
		//cleanTmpPath();
		checkjobIDZNode();	
		LOG.info("================JobIDDeamon shutdown!!!! unexpect======================");
	}

	public void checkjobIDZNode(){
		LOG.info("================JobIDDeamon.checkjobIDZNode()======================");
		ArrayList<JobID> jobIDs= new ArrayList<JobID>();
		JobStatus jobStatus = null;
		
		int clear = 1;
		int hostNO = 0;
	    this.zooKeeper = getZK(conf);;
			
		while(running){
			LOG.info("================JobIDDeamon is running!!!!!!!!!!!!===========000===========");
			tools.sleep(60000);
			
			try {
				this.jobIDList = this.zooKeeper.getChildren(this.jobIDAddressZNode, false);
				if(this.jobIDList == null){
					LOG.info("   this.jobIDList =0= null   !!!!!");	
				} else if(this.jobIDList != null && this.jobIDList.isEmpty()){
					LOG.info("   this.jobIDList =1= null   !!!!!");	
				} else {
					LOG.info("   this.jobIDList != null   !!!!!");
				}
			} catch (KeeperException e) {
				LOG.info("================KeeperException======================");				
				//e.printStackTrace();
				hostNO = clear % this.ZKHost.length;
				try {
					this.zooKeeper = new ZooKeeper(ZKHost[hostNO]+":"+ZKPort,this.timeout, this.zooKeeperWatcher);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				continue;
			} catch (InterruptedException e) {
				LOG.info("================InterruptedException======================");
				//e.printStackTrace();
				continue;
			}
				
			if(clear++ % 10 == 0){
				cleanTmpPath(jobIDs);
			}
			
			
			if(this.jobIDList != null && this.jobIDList.isEmpty() == false){
				for(String jobIDString : this.jobIDList){			
					tools.sleep(10000);
					JobID jobID = JobID.forName(jobIDString);
					jobIDs.add(jobID);
					LOG.info("znode in "+this.jobIDAddressZNode+" is " + jobIDString);					

						try {
							jobStatus = this.jobSubmitClient.getJobStatus(jobID);
							//LOG.info("this.jobSubmitClient.getFilesystemName() == " + this.jobSubmitClient.getFilesystemName());
							//LOG.info("this.jobSubmitClient.getSystemDir() == " + this.jobSubmitClient.getSystemDir());
							//LOG.info("this.jobSubmitClient.versionID == " + this.jobSubmitClient.versionID);
							//LOG.info("this.jobSubmitClient.toString() == " + this.jobSubmitClient.toString());
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						if(jobStatus == null){
							LOG.info("*****     jobStatus == null     *****");							
							rerunJob(jobID,"Interupt");
							continue;
						}
						//LOG.info("The "+this.jobIDAddressZNode+"/" + jobIDString + "`s  jobStatus is " + jobStatus.getRunState());

						
						LOG.info("**********");
						switch(jobStatus.getRunState()){
						case 1:LOG.info("The "+this.jobIDAddressZNode+"/" + jobIDString + " is RUNNING!!!");
								break;						
						case 2:LOG.info("The "+this.jobIDAddressZNode+"/" + jobIDString + " is SUCCEEDED!!!");
								delZnodeOfCompletedJob(jobID);
								break;
						case 3:LOG.info("The "+this.jobIDAddressZNode+"/" + jobIDString + " is FAILED!!!");
								rerunJob(jobID,"FAILED");
								break;
						case 4:LOG.info("The "+this.jobIDAddressZNode+"/" + jobIDString + " is PREP!!!");
								rerunJob(jobID,"PREP");
								break;
						case 5:LOG.info("The "+this.jobIDAddressZNode+"/" + jobIDString + " is KILLED!!!");
						        delZnodeOfJob(jobID);
								break;
						}					
						LOG.info("**********");					
					}
				}
			
		}
	}

	
	public void stop(){
		this.running = false;
	}
	private static JobSubmissionProtocol createRPCProxy(InetSocketAddress addr,
		      Configuration conf) throws IOException {
		    return (JobSubmissionProtocol) RPC.getProxy(JobSubmissionProtocol.class,
		        JobSubmissionProtocol.versionID, addr, getUGI(conf), conf,
		        NetUtils.getSocketFactory(conf, JobSubmissionProtocol.class));
		  }
	private static UnixUserGroupInformation getUGI(Configuration job) throws IOException {
	    UnixUserGroupInformation ugi = null;
	    try {
	      ugi = UnixUserGroupInformation.login(job, true);
	    } catch (LoginException e) {
	      throw (IOException)(new IOException(
	          "Failed to get the current user's information.").initCause(e));
	    }
	    return ugi;
	  }
	private boolean rerunJob(JobID jobID,String jobStatus){		
		if(jobRetryTime.get(jobID) == null){
			jobRetryTime.put(jobID, 1);
		} else {
			int time = jobRetryTime.get(jobID);
			if(time > 3){
				delZnodeOfJob(jobID);
				return false;
			}			
			time++;
			jobRetryTime.put(jobID, time);	
		}	
		
		LOG.info("rerun the "+ jobStatus +" job " + jobID.toString());
		FileSystem fs = null;
		Path submitTmpJobDir = null;
		submitTmpJobDir = new Path(new Path(this.jobSubmitClient.getSystemDir()).getParent(), jobID.toString());
		Path sysDir  = new Path(this.jobSubmitClient.getSystemDir(),jobID.toString());
		
		Path submitTmpJarFile = new Path(submitTmpJobDir, "job.jar");
		Path submitTmpSplitFile = new Path(submitTmpJobDir, "job.split");
		Path submitTmpXmlFile = new Path(submitTmpJobDir, "job.xml");
		
		//JobConf jobConf = new JobConf(submitTmpXmlFile);
		//String jarFile = jobConf.get("mapred.jar");
		//String splitFile = jobConf.get("mapred.job.split.file");
		//String address = jobConf.get("dfs.http.address");
		
		//String hostName = this.conf.get("fs.default.name");
		
		//Path jarFilePath = new Path(jarFile);
		
		//Path jarFilePath = new Path(jarFile);
				
		Path submitJarFile = new Path(sysDir, "job.jar");
		Path submitSplitFile = new Path(sysDir, "job.split");
		Path submitXmlFile = new Path(sysDir, "job.xml");
		
		try {
			fs = sysDir.getFileSystem(this.conf);
		if(fs.exists(submitTmpJarFile) && fs.exists(submitTmpSplitFile) && fs.exists(submitTmpXmlFile) ){
			fs.copyToLocalFile(submitTmpJarFile, new Path("/tmp/job.jar"));
		    fs.copyFromLocalFile(true, true, new Path("/tmp/job.jar"), submitJarFile);
		    
		    fs.copyToLocalFile(submitTmpSplitFile, new Path("/tmp/job.split"));
		    fs.copyFromLocalFile(true, true, new Path("/tmp/job.split"), submitSplitFile);
		    
		    fs.copyToLocalFile(submitTmpXmlFile, new Path("/tmp/job.xml"));
		    fs.copyFromLocalFile(true, true, new Path("/tmp/job.xml"), submitXmlFile);
		} else {
			return false;
		}
		
			fs = sysDir.getFileSystem(this.conf);
		} catch (IOException e1) {
			e1.printStackTrace();
		}			
		
		if(this.jobSubmitClient != null){
			try {
				this.jobSubmitClient.submitJob(jobID);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return true;
			
	}
	private void delZnodeOfCompletedJob(JobID jobID){
		
		LOG.info("The "+this.jobIDAddressZNode+"/" + jobID.toString() + " is complete!!!");
		LOG.info("Delete znode and tmp file!!!");
		delZnodeOfJob(jobID);
								
	}
	private void delZnodeOfJob(JobID jobID){
		FileSystem fs = null;
		Path submitTmpJobDir = null;
		try {
			this.zooKeeper.delete(this.jobIDAddressZNode + "/" +jobID.toString(), -1);
			submitTmpJobDir = new Path(new Path(this.jobSubmitClient.getSystemDir()).getParent(), jobID.toString());
			Path sysDir  = new Path(this.jobSubmitClient.getSystemDir());
			fs = sysDir.getFileSystem(this.conf);	
			if(fs.exists(submitTmpJobDir)){
				fs.delete(submitTmpJobDir, true);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			//break;
		} catch (KeeperException e) {
			e.printStackTrace();
			//break;
		} catch (IOException e) {
			e.printStackTrace();
			//break;
		}						
	}
	private void cleanTmpPath(ArrayList<JobID> jobIDs) {
		LOG.info("======JobIDDeamon.cleanTmpPath()======");
		ArrayList<Path> jobIDsTmpPath = new ArrayList<Path>();
		FileSystem fs = null;
		Path sysDir  = new Path(this.jobSubmitClient.getSystemDir());
		//LOG.info("=====sysDir  ==" + sysDir + "======");
		//for(JobID jobID : jobIDs){
			
		//	jobIDsTmpPath.add(new Path(new Path(this.jobSubmitClient.getSystemDir()).getParent(), jobID.toString()));
		//}
		

		Path sysParentDir  = sysDir.getParent();
		try {
			fs = sysParentDir.getFileSystem(this.conf);			
			FileStatus[] files = fs.listStatus(sysParentDir);
			
			for(FileStatus file : files){	
				if(	checkFile(jobIDs,file) == true || file.getPath().equals(sysDir)  == true){
					continue;					
				} else {
					LOG.info("=====delete " + file.getPath() + "======");
					fs.delete(file.getPath(), true);
				}				
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}	
		
		jobIDs.clear();
	}	
	public boolean checkFile(ArrayList<JobID> jobIDs,FileStatus file){
		Path sysDir  = new Path(this.jobSubmitClient.getSystemDir());
		
		for(JobID jobID : jobIDs){
			//LOG.info("=====file.getPath()  ==" + file.getPath() + "======");
			//LOG.info("======jobID.toString() ==" + jobID.toString() + "======");
			if(file.getPath().toString().contains(jobID.toString()) == true || file.getPath().equals(sysDir)  == true ){
				return true;
			}											
		}
		return false;
	}
	public ZooKeeper getZK(Configuration conf){
		 ZooKeeper zooKeeper = null;
		 String[] ZKHost = conf.getStrings("zookeeper.quorum");
		 int  ZKPort = conf.getInt("zookeeper.property.clientPort", 2181);    
		 int timeout = conf.getInt("zookeeper.session.timeout", 20 * 1000);
		 int HostNo = 0;
		 for(String h:ZKHost){
			 LOG.info("=========host = " + h + "=======" );
		 }
		 LOG.info("=========ZKPort = " + ZKPort + "=======" );
		 LOG.info("=========timeout = " + timeout + "=======" );
		 LOG.info("=========" + ZKHost[0] + ":" +ZKPort+ "=======" );
		 int i = 0;
		 while(true){
			 HostNo = i++ % ZKHost.length;
			try {				
				zooKeeper = new ZooKeeper( ZKHost[HostNo] +":"+ZKPort, timeout, this.zooKeeperWatcher);
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
			break;
		 }
		 return zooKeeper;
	 } 
	  public class ZooKeeperWatcher implements Watcher{

			private  AtomicBoolean clusterHasActiveJT;
			public ZooKeeperWatcher(AtomicBoolean clusterHasActiveJT){
				this.clusterHasActiveJT = clusterHasActiveJT;
			}
			@Override
			public void process(WatchedEvent event) {
				switch(event.getType()) {

			      // If event type is NONE, this is a connection status change
			      case None: {
			        break;
			      }

			      case NodeCreated: {
			        break;
			      }

			      case NodeDeleted: {
			    	  //this.clusterHasActiveJT.set(false);
			        break;
			      }

			      case NodeDataChanged: {
			        break;
			      }

			      case NodeChildrenChanged: {
			        break;
			      }
			    }
				
			}
			
		}
}
