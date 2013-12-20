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

package org.apache.hadoop.hdfs;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.net.InetSocketAddress;
import javax.security.auth.login.LoginException;

import org.apache.hadoop.ipc.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.hadoop.io.retry.RetryPolicies;
import org.apache.hadoop.io.retry.RetryProxy;
import org.apache.hadoop.security.UnixUserGroupInformation;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.AvatarProtocol;
import org.apache.hadoop.hdfs.protocol.AvatarConstants.Avatar;
import org.apache.hadoop.hdfs.server.namenode.AvatarNode;

/**
 * A {@link AvatarShell} that allows browsing configured avatar policies.
 */
public class AvatarShell extends Configured implements Tool {
  public static final Log LOG = LogFactory.getLog( "org.apache.hadoop.AvatarShell");

  public AvatarProtocol avatarnode;
  final AvatarProtocol rpcAvatarnode;
  private UnixUserGroupInformation ugi;
  volatile boolean clientRunning = true;
  private Configuration conf;

  /**
   * Start AvatarShell.
   * <p>
   * The AvatarShell connects to the specified AvatarNode and performs basic
   * configuration options.
   * @throws IOException
   */
  public AvatarShell() throws IOException {
    this(new Configuration());
  }

  /**
   * The AvatarShell connects to the specified AvatarNode and performs basic
   * configuration options.
   * @param conf The Hadoop configuration
   * @throws IOException
   */
  public AvatarShell(Configuration conf) throws IOException {
    super(conf);
    try {
      this.ugi = UnixUserGroupInformation.login(conf, true);
    } catch (LoginException e) {
      throw (IOException)(new IOException().initCause(e));
    }
//--------------------------
    this.rpcAvatarnode = createRPCAvatarnode(AvatarNode.getAvatarShellAddress(conf), conf, ugi);
//--------------------------
    this.avatarnode = createAvatarnode(rpcAvatarnode);
    this.conf = conf;
  }

  public static AvatarProtocol createAvatarnode(Configuration conf) throws IOException {
    return createAvatarnode(AvatarNode.getAddress(conf), conf);
  }

  public static AvatarProtocol createAvatarnode(InetSocketAddress avatarNodeAddr,
      Configuration conf) throws IOException {
    try {
      return createAvatarnode(createRPCAvatarnode(avatarNodeAddr, conf,
        UnixUserGroupInformation.login(conf, true)));
    } catch (LoginException e) {
      throw (IOException)(new IOException().initCause(e));
    }
  }

  private static AvatarProtocol createRPCAvatarnode(InetSocketAddress avatarNodeAddr,
      Configuration conf, UnixUserGroupInformation ugi)
    throws IOException {
    LOG.info("AvatarShell connecting to " + avatarNodeAddr);
    return (AvatarProtocol)RPC.getProxy(AvatarProtocol.class,
        AvatarProtocol.versionID, avatarNodeAddr, ugi, conf,
        NetUtils.getSocketFactory(conf, AvatarProtocol.class));
  }

  private static AvatarProtocol createAvatarnode(AvatarProtocol rpcAvatarnode)
    throws IOException {
    RetryPolicy createPolicy = RetryPolicies.retryUpToMaximumCountWithFixedSleep(
        5, 5000, TimeUnit.MILLISECONDS);

    Map<Class<? extends Exception>,RetryPolicy> remoteExceptionToPolicyMap =
      new HashMap<Class<? extends Exception>, RetryPolicy>();

    Map<Class<? extends Exception>,RetryPolicy> exceptionToPolicyMap =
      new HashMap<Class<? extends Exception>, RetryPolicy>();
    exceptionToPolicyMap.put(RemoteException.class,
        RetryPolicies.retryByRemoteException(
            RetryPolicies.TRY_ONCE_THEN_FAIL, remoteExceptionToPolicyMap));
    RetryPolicy methodPolicy = RetryPolicies.retryByException(
        RetryPolicies.TRY_ONCE_THEN_FAIL, exceptionToPolicyMap);
    Map<String,RetryPolicy> methodNameToPolicyMap = new HashMap<String,RetryPolicy>();

    methodNameToPolicyMap.put("create", methodPolicy);

    return (AvatarProtocol) RetryProxy.create(AvatarProtocol.class,
        rpcAvatarnode, methodNameToPolicyMap);
  }

  private void checkOpen() throws IOException {
    if (!clientRunning) {
      IOException result = new IOException("AvatarNode closed");
      throw result;
    }
  }

  /**
   * Close the connection to the avatarNode.
   */
  public synchronized void close() throws IOException {
    if(clientRunning) {
      clientRunning = false;
      RPC.stopProxy(rpcAvatarnode);
    }
  }

  /**
   * Displays format of commands.
   */
  private static void printUsage(String cmd) {
    String prefix = "Usage: java " + AvatarShell.class.getSimpleName();
    if ("-showAvatar".equals(cmd)) {
      System.err.println("Usage: java AvatarShell" + 
                         " [-showAvatar]"); 
    } else if ("-setAvatar".equals(cmd)) {
      System.err.println("Usage: java AvatarShell" +
                         " [-setAvatar {primary|standby}]");
    } else {
      System.err.println("Usage: java AvatarShell");
      System.err.println("           [-showAvatar ]");
      System.err.println("           [-setAvatar {primary|standby}]");
      System.err.println("           [-addEditLogAndFSImagePath EditLogPath FSImagePath]");
      System.err.println();
      ToolRunner.printGenericCommandUsage(System.err);
    }
  }

  /**
   * run
   */
  public int run(String argv[]) throws Exception {

    if (argv.length < 1) {
      printUsage("");
      return -1;
    }

    int exitCode = -1;
    int i = 0;
    String cmd = argv[i++];
    //
    // verify that we have enough command line parameters
    //
    if ("-showAvatar".equals(cmd)) {
      if (argv.length < 1) {
        printUsage(cmd);
        return exitCode;
      }
    } else if ("-setAvatar".equals(cmd)) {
      if (argv.length < 2) {
        printUsage(cmd);
        return exitCode;
      }
    //added by wanglei --2011.11.16 --start
    } else if ("-addEditLogAndFSImagePath".equals(cmd)) {
      if (argv.length < 3) {
        printUsage(cmd);
        return exitCode;
      }
    }
  //added by wanglei --2011.11.16 --end
    try {
      if ("-showAvatar".equals(cmd)) {
        exitCode = showAvatar(cmd, argv, i);
      } else if ("-setAvatar".equals(cmd)) {
        exitCode = setAvatar(cmd, argv, i);
      //added by wanglei --2011.11.16 --start
      }  else if ("-addEditLogAndFSImagePath".equals(cmd)) {
        exitCode = addEditLogAndFSImagePath(cmd, argv, i);
      //added by wanglei --2011.11.16 --end
      }  else {
        exitCode = -1;
        System.err.println(cmd.substring(1) + ": Unknown command");
        printUsage("");
      }
    } catch (IllegalArgumentException arge) {
      exitCode = -1;
      System.err.println(cmd.substring(1) + ": " + arge.getLocalizedMessage());
      printUsage(cmd);
    } catch (RemoteException e) {
      //
      // This is a error returned by avatarnode server. Print
      // out the first line of the error mesage, ignore the stack trace.
      exitCode = -1;
      try {
        String[] content;
        content = e.getLocalizedMessage().split("\n");
        System.err.println(cmd.substring(1) + ": " +
                           content[0]);
      } catch (Exception ex) {
        System.err.println(cmd.substring(1) + ": " +
                           ex.getLocalizedMessage());
      }
    } catch (IOException e) {
      //
      // IO exception encountered locally.
      // 
      exitCode = -1;
      System.err.println(cmd.substring(1) + ": " +
                         e.getLocalizedMessage());
    } catch (Exception re) {
      exitCode = -1;
      System.err.println(cmd.substring(1) + ": " + re.getLocalizedMessage());
    } finally {
    }
    return exitCode;
  }

  /**
   * Apply operation specified by 'cmd' on all parameters
   * starting from argv[startindex].
   */
  private int showAvatar(String cmd, String argv[], int startindex) throws IOException {
    int exitCode = 0;
    Avatar avatar = avatarnode.getAvatar();
    System.out.println("The current avatar of " + 
                        AvatarNode.getAddress(conf) + " is " +
                        avatar);
    return exitCode;
  }

  /**
   * Sets the avatar to the specified value
   */
  public int setAvatar(String cmd, String argv[], int startindex)
  throws IOException {
  int exitCode = 0;
  String input = argv[startindex];
  Avatar dest;
  if (Avatar.ACTIVE.toString().equalsIgnoreCase(input)) {
    dest = Avatar.ACTIVE;
  } else if (Avatar.STANDBY.toString().equalsIgnoreCase(input)) {
    dest = Avatar.STANDBY;
  } else {
    throw new IOException("Unknown avatar type " + input);
  }
  Avatar current = avatarnode.getAvatar();
  if (current == dest) {
    System.out.println("This instance is already in " + current + " avatar.");
  } else {
    avatarnode.setAvatar(dest);
  }
  return 0;
}
  

  /**
   * main() has some simple utility methods
   */
  public static void main(String argv[]) throws Exception {
    AvatarShell shell = null;
    try {
      shell = new AvatarShell();
    } catch (RPC.VersionMismatch v) {
      System.err.println("Version Mismatch between client and server" +
                         "... command aborted.");
      System.exit(-1);
    } catch (IOException e) {
      System.err.println("Bad connection to AvatarNode. command aborted.");
      System.exit(-1);
    }

    int res;
    try {
      res = ToolRunner.run(shell, argv);
    } finally {
      shell.close();
    }
    System.exit(res);
  }
//added by wanglei --2011.11.16 --start
  public int addEditLogAndFSImagePath(String cmd, String argv[], int startindex)
    throws IOException {
    
    String NFSEditPath  = argv[startindex++];    
    
    String NFSImagePath = argv[startindex];    
  
    if(avatarnode.AddEditLogAndFSImagePath(NFSEditPath,NFSImagePath)){
    	LOG.info("add path successfully!!!"); 
    } else {
    	LOG.info("failed to add path!!!"); 
    }

    return 0;
  }
//added by wanglei --2011.11.16 --start
}
