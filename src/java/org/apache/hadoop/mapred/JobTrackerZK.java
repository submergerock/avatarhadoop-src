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
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.http.HttpServer;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.hadoop.ipc.Server;
import org.apache.hadoop.ipc.RPC.VersionMismatch;
import org.apache.hadoop.mapred.JobHistory.Keys;
import org.apache.hadoop.mapred.JobHistory.Listener;
import org.apache.hadoop.mapred.JobHistory.Values;
import org.apache.hadoop.mapred.JobInProgress.KillInterruptedException;
import org.apache.hadoop.mapred.JobStatusChangeEvent.EventType;
import org.apache.hadoop.mapred.util.Sleeper;
import org.apache.hadoop.mapred.util.getHostIP;
import org.apache.hadoop.net.DNSToSwitchMapping;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.net.NetworkTopology;
import org.apache.hadoop.net.Node;
import org.apache.hadoop.net.NodeBase;
import org.apache.hadoop.net.ScriptBasedMapping;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.security.authorize.ConfiguredPolicy;
import org.apache.hadoop.security.authorize.PolicyProvider;
import org.apache.hadoop.security.authorize.RefreshAuthorizationPolicyProtocol;
import org.apache.hadoop.security.authorize.ServiceAuthorizationManager;
import org.apache.hadoop.util.HostsFileReader;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.util.VersionInfo;
import org.apache.zookeeper.*;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.WatchedEvent;
import org.apache.hadoop.mapred.util.Bytes;


public class JobTrackerZK extends Thread implements Stoppable{
	
	private static final Log LOG = LogFactory.getLog(JobTrackerZK.class);
	private JobConf conf = null;
	private volatile boolean stopped = false;
	// zookeeper connection
	private ZooKeeper zooKeeper;
	private ZooKeeperWatcher zooKeeperWatcher;
	final AtomicBoolean clusterHasActiveJT = new AtomicBoolean(false);
	String address = null;
	public String jtAddressZNode  ;
	private JobTracker tracker = null;
	private JTDeamon jtDeamon = null;
	private Thread jtDeamonThread = null;  
	private JobIDDeamon jobIDDeamon = null;
	private Thread jobIDDeamonThread = null; 
	String[] ZKHost = null;
	int ZKPort = 2181;
	
	public JobTrackerZK(JobConf conf){
		this.conf = conf;
		int timeout = conf.getInt("zookeeper.session.timeout", 20 * 1000);
		this.zooKeeperWatcher = new ZooKeeperWatcher(this.clusterHasActiveJT,this.tracker,this,this.zooKeeper,this.conf);
		this.address = getHostIP.getLocalIP();
		this.jtAddressZNode = "/jt";
		LOG.info("=========timeout = " + timeout + "=======" );
		ZKHost = conf.getStrings("zookeeper.quorum");
	    ZKPort = conf.getInt("zookeeper.property.clientPort", 2181);    
	    for(String h:ZKHost){
			 LOG.info("=========host = " + h + "=======" );
		 }
		 LOG.info("=========ZKPort = " + ZKPort + "=======" );
		 
		 LOG.info("=========" + ZKHost[0] + ":" +ZKPort+ "=======" );
	    
	    
		try {
			this.zooKeeper = new ZooKeeper( ZKHost[0] +":"+2181, timeout, this.zooKeeperWatcher);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	
	
	public void run() {
		try {
		      /*
		       * Block on becoming the active master.
		       *
		       * We race with other masters to write our address into ZooKeeper.  If we
		       * succeed, we are the primary/active master and finish initialization.
		       *
		       * If we do not succeed, there is another active master and we should
		       * now wait until it dies to try and become the next active master.  If we
		       * do not succeed on our first attempt, this is no longer a cluster startup.
		       */
			LOG.info("JTZK running!!!!" );
		      blockUntilBecomingActiveJT();
		      // We are either the active master or we were asked to shutdown
		      if (!this.stopped) {
		    	  LOG.info("start JTDeamon!!!!" );
		    	this.jtDeamon = new JTDeamon(this);
		    	this.jtDeamonThread = new Thread(this.jtDeamon);
		    	this.jtDeamonThread.start();
		    	  LOG.info("start JobIDDeamon!!!!" );
		    	this.jobIDDeamon = new JobIDDeamon(this,this.conf);
		    	this.jobIDDeamonThread = new Thread(this.jobIDDeamon);
		    	this.jobIDDeamonThread.start();
		        loop();
		      }
		    } catch (Throwable t) {
		      LOG.info("Unhandled exception. Starting shutdown.", t);
		    } finally {
		      this.stopped = true;
		      try {
				this.tracker.stopTracker();				
				this.zooKeeper.close();
				this.jtDeamonThread.stop();
				this.jobIDDeamonThread.stop();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}		      
		    }
		    LOG.info("JobTrackerZK main thread exiting");
	  }

	  private void loop() {
	    // Check if we should stop every second.
	    Sleeper sleeper = new Sleeper(1000, this);
	    while (!this.stopped) {
	      sleeper.sleep();
	    }
	  }


	@Override
	public boolean isStopped() {
		// TODO Auto-generated method stub
		return this.stopped;
	}


	@Override
	public void stop(String why) {
		// TODO Auto-generated method stub
		
	}
	boolean blockUntilBecomingActiveJT() {
	    boolean cleanSetOfActiveJT = true;
	    
	    // Try to become the active master, watch if there is another master
	    LOG.info("Master=" + this.address);
	    LOG.info("===========111============");
	    try {
	    	//Stat s = this.zooKeeper.exists(this.jtAddressZNode, this.zooKeeperWatcher);
	    	if(createEphemeralNodeAndWatch(this.zooKeeperWatcher,this.jtAddressZNode,Bytes.toBytes(this.address))){	    
	        // We are the master, return
	        this.clusterHasActiveJT.set(true);
	        LOG.info("Master=" + this.address);
	        return cleanSetOfActiveJT;
	      }
	    	cleanSetOfActiveJT = false;
	    	LOG.info("===========222============");
	      // There is another active master running elsewhere or this is a restart
	      // and the master ephemeral node has not expired yet.
	      this.clusterHasActiveJT.set(true);
	      String currentMaster =  getDataAsAddress(this.zooKeeperWatcher, this.jtAddressZNode);
	      LOG.info("===========333============");
	      if (currentMaster != null && currentMaster.equals(this.address)) {
	        LOG.info("Current jt has this jt's address, " + currentMaster +
	          "; jt was restarted?  Waiting on znode to expire...");
	        // Hurry along the expiration of the znode.
	        this.zooKeeper.delete(this.jtAddressZNode, -1);
	      } else {
	        LOG.info("Another jt is the active jt, " + currentMaster +
	          "; waiting to become the next active jt");
	      }
	    } catch (KeeperException ke) {
	      LOG.info("Received an unexpected KeeperException, aborting");
	      ke.printStackTrace();
	      return false;
	    } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    
	    synchronized (this.clusterHasActiveJT) {
	      while (this.clusterHasActiveJT.get() && !isStopped()) {
	        try {
	          this.clusterHasActiveJT.wait();
	        } catch (InterruptedException e) {
	          // We expect to be interrupted when a master dies, will fall out if so
	          LOG.debug("Interrupted waiting for master to die", e);
	        }
	      }
	      if (isStopped()) {
	        return cleanSetOfActiveJT;
	      }
	      // Try to become active master again now that there is no active master
	      blockUntilBecomingActiveJT();
	    }
	    return cleanSetOfActiveJT;
	  }
	
	public String getDataAsAddress(ZooKeeperWatcher zkw,
		      String znode)
		  throws KeeperException {
		    byte [] data = getDataAndWatch(zkw, znode);
		    if(data == null) {
		      return null;
		    }
		    String addrString = Bytes.toString(data);
		    return addrString;
		  }
	
	public  byte [] getDataAndWatch(ZooKeeperWatcher zkw, String znode)
	  throws KeeperException {
	    try {
	      byte [] data = zooKeeper.getData(znode, zkw, null);
	      return data;
	    } catch (KeeperException.NoNodeException e) {
	      LOG.info("Unable to get data of znode " + znode + " " +
	        "because node does not exist (not an error)");
	      return null;
	    } catch (KeeperException e) {
	      LOG.info("Unable to get data of znode " + znode);
	      return null;
	    } catch (InterruptedException e) {
	      LOG.info("Unable to get data of znode " + znode);
	      return null;
	    }
	}
	  
	/**
	   *
	   * Set the specified znode to be an ephemeral node carrying the specified
	   * data.
	   *
	   * If the node is created successfully, a watcher is also set on the node.
	   *
	   * If the node is not created successfully because it already exists, this
	   * method will also set a watcher on the node.
	   *
	   * If there is another problem, a KeeperException will be thrown.
	   *
	   * @param zkw zk reference
	   * @param znode path of node
	   * @param data data of node
	   * @return true if node created, false if not, watch set in both cases
	   * @throws KeeperException if unexpected zookeeper exception
	   */
	 public boolean createEphemeralNodeAndWatch(ZooKeeperWatcher zkw,
		      String znode, byte [] data)
		  throws KeeperException {
		    try {
		    	LOG.info("==========createEphemeralNodeAndWatch=========000============");
		      this.zooKeeper.create(znode, data, Ids.OPEN_ACL_UNSAFE,
		          CreateMode.EPHEMERAL);
		      LOG.info("==========createEphemeralNodeAndWatch=========111============");
		    } catch (KeeperException.NodeExistsException nee) {
		      if(!watchAndCheckExists(zkw, znode)) {
		        // It did exist but now it doesn't, try again
		        return createEphemeralNodeAndWatch(zkw, znode, data);
		      }
		      return false;
		    } catch (InterruptedException e) {
		      LOG.info("Interrupted", e);
		      Thread.currentThread().interrupt();
		    }
		    return true;
		  }
	
	 /**
	   * Watch the specified znode for delete/create/change events.  The watcher is
	   * set whether or not the node exists.  If the node already exists, the method
	   * returns true.  If the node does not exist, the method returns false.
	   *
	   * @param zkw zk reference
	   * @param znode path of node to watch
	   * @return true if znode exists, false if does not exist or error
	   * @throws KeeperException if unexpected zookeeper exception
	   */
	  public boolean watchAndCheckExists(ZooKeeperWatcher zkw, String znode)
	  throws KeeperException {
	    try {
	      Stat s = this.zooKeeper.exists(znode, zkw);
	      LOG.info("Set watcher on existing znode " + znode);
	      return s != null ? true : false;
	    } catch (KeeperException e) {
	      LOG.info("Unable to set watcher on znode " + znode);
	      return false;
	    } catch (InterruptedException e) {
	      LOG.info("Unable to set watcher on znode " + znode);
	      return false;
	    }
	  }
	
	 public void setJT(JobTracker jt){
		 this.tracker = jt;
	 }
	 public JobTracker getJT(){
		 
		 return this.tracker;
	 }
	 public ZooKeeper getZK(Configuration conf){
		 String[] ZKHost = conf.getStrings("zookeeper.quorum");
		 int  ZKPort = conf.getInt("zookeeper.property.clientPort", 2181);    
		 int timeout = conf.getInt("zookeeper.session.timeout", 20 * 1000);
		 
		 for(String h:ZKHost){
			 LOG.info("=========host = " + h + "=======" );
		 }
		 LOG.info("=========ZKPort = " + ZKPort + "=======" );
		 LOG.info("=========timeout = " + timeout + "=======" );
		 LOG.info("=========" + ZKHost[0] + ":" +ZKPort+ "=======" );
		 
		 
			try {
				ZooKeeper zooKeeper = new ZooKeeper( ZKHost[0] +":"+ZKPort, timeout, this.zooKeeperWatcher);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 return zooKeeper;
	 }
	  ////////////////////////////////////////////////////////////
	  // main()
	  ////////////////////////////////////////////////////////////

	  /**
	   * Start the JobTracker process.  This is used only for debugging.  As a rule,
	   * JobTracker should be run as part of the DFS Namenode process.
	   */
	  public static void main(String argv[]
	                          ) throws IOException, InterruptedException {
	    StringUtils.startupShutdownMessage(JobTracker.class, argv, LOG);
	    if (argv.length != 0) {
	      System.out.println("usage: JobTrackerZK");
	      System.exit(-1);
	    }
	    LOG.info("start JTZK!!!!" );
	    try {
	    LOG.info("start JTZK!!!!=========000=======" );
	      JobConf conf = new JobConf();
	      conf.addResource("core-site.xml");
	      conf.addResource("hdfs-site.xml");
	      LOG.info("start JTZK!!!!=========111=======" );
	      JobTrackerZK jtzk = new JobTrackerZK(conf);
	      LOG.info("start JTZK!!!!=========222=======" );
	      jtzk.run();
	    } catch (Throwable e) {
	      LOG.fatal(StringUtils.stringifyException(e));
	      System.exit(-1);
	    }
	  }

	
}