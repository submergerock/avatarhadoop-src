package org.apache.hadoop.mapred;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
//import org.apache.hadoop.hbase.zookeeper.ZKUtil;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.UnixUserGroupInformation;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class ZooKeeperWatcher implements Watcher{
	public static final Log LOG = LogFactory.getLog(ZooKeeperWatcher.class.getName());
	private  AtomicBoolean clusterHasActiveJT;
	private JobTracker tracker = null;
	private JobTrackerZK jobTrackerZK = null;
	private ZooKeeper zooKeeper = null;
	public String jobIDAddressZNode  = "/mapred/jobid";
	private List<String> jobIDList= null;
	private JobConf conf = null;
	private static JobSubmissionProtocol jobSubmitClient;
	
	public ZooKeeperWatcher(AtomicBoolean clusterHasActiveJT,JobTracker tracker,JobTrackerZK jobTrackerZK,ZooKeeper zooKeeper,JobConf conf){
		this.clusterHasActiveJT = clusterHasActiveJT;
		this.tracker = tracker;
		this.jobTrackerZK = jobTrackerZK;
		this.zooKeeper = zooKeeper;
		this.jobIDList = new ArrayList<String>();
		this.conf = conf;
		
	}
	public ZooKeeperWatcher(){
		
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
	    	      synchronized(clusterHasActiveJT) {	    	       
	    	          LOG.info("No master available. Notifying waiting threads");
	    	          clusterHasActiveJT.set(false);
	    	          // Notify any thread waiting to become the active master
	    	          clusterHasActiveJT.notifyAll();	    	        
	    	      }    	    
	    	  
	    	 // doJob();
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
	private void doJob(){
		
		try {
			jobSubmitClient = createRPCProxy(JobTracker.getAddress(conf), conf);
		} catch (IOException e1) {
			return;
		}
		
		try {
			this.jobIDList = this.zooKeeper.getChildren(this.jobIDAddressZNode, true);
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
		for(String jobIDString : this.jobIDList){
			JobID jobID = JobID.forName(jobIDString);
			rerunJob(jobID,"Interupt");				
		}	
	}
	
	private boolean rerunJob(JobID jobID,String jobStatus){		
		LOG.info("rerun the "+ jobStatus +" job " + jobID.toString());
		FileSystem fs = null;
		Path submitTmpJobDir = null;
		submitTmpJobDir = new Path(new Path(this.jobSubmitClient.getSystemDir()).getParent(), jobID.toString());
		Path sysDir  = new Path(this.jobSubmitClient.getSystemDir(),jobID.toString());
		
		Path submitTmpJarFile = new Path(submitTmpJobDir, "job.jar");
		Path submitTmpSplitFile = new Path(submitTmpJobDir, "job.split");
		Path submitTmpXmlFile = new Path(submitTmpJobDir, "job.xml");
		
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
	
}