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
import java.util.Date;
import java.util.Collection;
import java.text.SimpleDateFormat;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.io.*;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.RPC.VersionMismatch;
import org.apache.hadoop.fs.permission.*;
import org.apache.hadoop.hdfs.protocol.FSConstants;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.DatanodeID;
import org.apache.hadoop.security.AccessControlException;
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

/**
 * This class reads transaction logs from the primary's shared device
 * and feeds it to the standby NameNode.
 */

public  class JTDeamon implements Runnable {

  public static final Log LOG = LogFactory.getLog(JTDeamon.class.getName());
  static final SimpleDateFormat DATE_FORM =
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private JobTracker tracker = null;
  private JobTrackerZK jobTrackerZK;
  
  
  
  public JTDeamon(JobTrackerZK jobTrackerZK){
	  this.jobTrackerZK = jobTrackerZK;
  }
	@Override
	public void run() {
		LOG.info("==============JTDeamon.run()=====================" );
		startJT();
	}
	private void startJT(){
		LOG.info("==============JTDeamon.startJT()=====================" );
	  try {
	      this.tracker = startTracker(new JobConf(),(new SimpleDateFormat("yyyyMMddHHmm")).format(new Date()));
	      if(this.jobTrackerZK != null && this.tracker != null){
	    	  this.jobTrackerZK.setJT(this.tracker);
	      }
	      tracker.offerService();
	    } catch (Throwable e) {
	      LOG.fatal(StringUtils.stringifyException(e));
	      System.exit(-1);
	    }
	}
	public  JobTracker startTracker(JobConf conf, String identifier) throws IOException, InterruptedException {
	  JobTracker result = null;
	  LOG.info("==============JTDeamon.startTracker()=====================" );
	  while (true) {
	    try {
	      result = new JobTracker(conf, identifier);
	      result.getTaskScheduler().setTaskTrackerManager(result);
	      break;
	    } catch (VersionMismatch e) {
	      throw e;
	    } catch (BindException e) {
	      throw e;
	    } catch (UnknownHostException e) {
	      throw e;
	    } catch (AccessControlException ace) {
	      // in case of jobtracker not having right access
	      // bail out
	      throw ace;
	    } catch (IOException e) {
	      LOG.warn("Error starting tracker: " + 
	               StringUtils.stringifyException(e));
	    }
	    Thread.sleep(1000);
	  }
	  if (result != null) {
	    JobEndNotifier.startNotifier();
	  }
	  return result;
	}


}
