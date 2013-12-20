package org.apache.hadoop.mapred;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hadoop.conf.Configuration;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class JobClientZK extends JobClient{

	final AtomicBoolean clusterHasActiveJT = new AtomicBoolean(false);
	private ZooKeeper zooKeeper;
	private ZooKeeperWatcher zooKeeperWatcher;
	private RunningJob runningJob = null;
	 
	
	public JobClientZK(JobConf conf) throws IOException{
		super(conf);
		initZK(conf) ;
	}
	public void initZK(JobConf conf) throws IOException {	           
	    int timeout = conf.getInt("zookeeper.session.timeout", 10 * 1000);
		this.zooKeeperWatcher = new ZooKeeperWatcher(this.clusterHasActiveJT);
	    this.zooKeeper = new ZooKeeper("192.168.1.19:2181", timeout, this.zooKeeperWatcher);
	  }
	public void submitToZK(){
		//submitJobInternal(this.getConf());
	}
	
}
