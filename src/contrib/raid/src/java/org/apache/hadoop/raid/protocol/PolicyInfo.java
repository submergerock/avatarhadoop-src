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

package org.apache.hadoop.raid.protocol;

import java.io.IOException;
import java.io.DataInput;
import java.io.DataOutput;
import java.util.Properties;
import java.util.Enumeration;
import java.lang.Math;
import java.text.SimpleDateFormat;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableFactories;
import org.apache.hadoop.io.WritableFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;

/**
 * Maintains information about one policy
 */
public class PolicyInfo implements Writable {
  public static final Log LOG = LogFactory.getLog(
    "org.apache.hadoop.raid.protocol.PolicyInfo");
  protected static final SimpleDateFormat dateFormat =
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private Path srcPath;            // the specified src path
  private String policyName;       // name of policy
  private String destinationPath;  // A destination path for this policy
  private String description;      // A verbose description of this policy
  private Configuration conf;      // Hadoop configuration

  private Properties properties;   // Policy-dependent properties

  private ReentrantReadWriteLock plock; // protects policy operations.
  
  /**
   * Create the empty object
   */
  public PolicyInfo() {
    this.conf = null;
    this.policyName = "";
    this.description = "";
    this.srcPath = null;
    this.properties = new Properties();
    this.plock = new ReentrantReadWriteLock();
  }

  /**
   * Create the metadata that describes a policy
   */
  public PolicyInfo(String  policyName, Configuration conf) {
    this.conf = conf;
    this.policyName = policyName;
    this.description = "";
    this.srcPath = null;
    this.properties = new Properties();
    this.plock = new ReentrantReadWriteLock();
  }

  /**
   * Sets the input path on which this policy has to be applied
   */
  public void setSrcPath(String in) throws IOException {
    srcPath = new Path(in);
    srcPath = srcPath.makeQualified(srcPath.getFileSystem(conf));
  }

  /**
   * Set the destination path of this policy.
   */
  public void setDestinationPath(String des) {
    this.destinationPath = des;
  }

  /**
   * Set the description of this policy.
   */
  public void setDescription(String des) {
    this.description = des;
  }

  /**
   * Sets an internal property.
   * @param name property name.
   * @param value property value.
   */
  public void setProperty(String name, String value) {
    properties.setProperty(name, value);
  }
  
  /**
   * Returns the value of an internal property.
   * @param name property name.
   */
  public String getProperty(String name) {
    return properties.getProperty(name);
  }
  
  /**
   * Get the name of this policy.
   */
  public String getName() {
    return this.policyName;
  }

  /**
   * Get the destination path of this policy.
   */
  public String getDestinationPath() {
    return this.destinationPath;
  }

  /**
   * Get the srcPath
   */
  public Path getSrcPath() throws IOException {
    return srcPath;
  }

  /**
   * Get the expanded (unglobbed) forms of the srcPaths
   */
  public Path[] getSrcPathExpanded() throws IOException {
    FileSystem fs = srcPath.getFileSystem(conf);

    // globbing on srcPath
    FileStatus[] gpaths = fs.globStatus(srcPath);
    if (gpaths == null) {
      return null;
    }
    Path[] values = new Path[gpaths.length];
    for (int i = 0; i < gpaths.length; i++) {
      Path p = gpaths[i].getPath();
      values[i] = p.makeQualified(fs);
    }
    return values;
  }

  /**
   * Convert this policy into a printable form
   */
  public String toString() {
    StringBuffer buff = new StringBuffer();
    buff.append("Policy Name:\t" + policyName + " --------------------\n");
    buff.append("Source Path:\t" + srcPath + "\n");
    buff.append("Dest Path:\t" + destinationPath + "\n");
    for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
      String name = (String) e.nextElement(); 
      buff.append( name + ":\t" + properties.getProperty(name) + "\n");
    }
    if (description.length() > 0) {
      int len = Math.min(description.length(), 80);
      String sub = description.substring(0, len).trim();
      sub = sub.replaceAll("\n", " ");
      buff.append("Description:\t" + sub + "...\n");
    }
    return buff.toString();
  }

  //////////////////////////////////////////////////
  // Writable
  //////////////////////////////////////////////////
  static {                                      // register a ctor
    WritableFactories.setFactory
      (PolicyInfo.class,
       new WritableFactory() {
         public Writable newInstance() { return new PolicyInfo(); }
       });
  }

  public void write(DataOutput out) throws IOException {
	  if(LOGDISPLAY1)  LOG.info("srcPath.toString() == " + srcPath.toString());
      if(LOGDISPLAY1)  LOG.info("policyName == " + policyName);
      if(LOGDISPLAY1)  LOG.info("destinationPath == " + destinationPath);
      if(LOGDISPLAY1)  LOG.info("description == " + description);
      if(LOGDISPLAY1)  LOG.info("properties.size() == " + properties.size());
    Text.writeString(out, srcPath.toString());
    Text.writeString(out, policyName);
    Text.writeString(out, destinationPath);
    Text.writeString(out, description);
    out.writeInt(properties.size());
    for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
      String name = (String) e.nextElement(); 
      Text.writeString(out, name);
      Text.writeString(out, properties.getProperty(name));
      if(LOGDISPLAY1)  LOG.info("name == " + name);
      if(LOGDISPLAY1)  LOG.info("properties.getProperty(name) == " + properties.getProperty(name));
    }
  }

  public void readFields(DataInput in) throws IOException {
    this.srcPath = new Path(Text.readString(in));
    this.policyName = Text.readString(in);
    this.destinationPath = Text.readString(in);
    this.description = Text.readString(in);
    for (int n = in.readInt(); n>0; n--) {
      String name = Text.readString(in);
      String value = Text.readString(in);
      properties.setProperty(name,value);
    }
  }
  public static  boolean LOGDISPLAY=false;
  public static  boolean LOGDISPLAY1=false;
  public static  boolean LOGDISPLAY2=false;
  public static  boolean LOGDISPLAY3=false;
  public static  boolean LOGDISPLAY4=false;
  public static  boolean LOGDISPLAY5=false;
  public static  boolean LOGDISPLAY6=false;
  public static  boolean LOGDISPLAY7=false;
  public static  boolean LOGDISPLAY8=false;
  public static  boolean LOGDISPLAY9=false;
  public static  boolean LOGDISPLAY10=false;
  public static  boolean LOGDISPLAY0=false;
  public static  boolean LOGDEBUG=false;
  static
  {
	   LOGDISPLAY=System.getProperty("LOGDISPLAY")!=null&&System.getProperty("LOGDISPLAY").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY1=System.getProperty("LOGDISPLAY1")!=null&&System.getProperty("LOGDISPLAY1").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY2=System.getProperty("LOGDISPLAY2")!=null&&System.getProperty("LOGDISPLAY2").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY3=System.getProperty("LOGDISPLAY3")!=null&&System.getProperty("LOGDISPLAY3").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY4=System.getProperty("LOGDISPLAY4")!=null&&System.getProperty("LOGDISPLAY4").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY5=System.getProperty("LOGDISPLAY5")!=null&&System.getProperty("LOGDISPLAY5").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY6=System.getProperty("LOGDISPLAY6")!=null&&System.getProperty("LOGDISPLAY6").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY7=System.getProperty("LOGDISPLAY7")!=null&&System.getProperty("LOGDISPLAY7").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY8=System.getProperty("LOGDISPLAY8")!=null&&System.getProperty("LOGDISPLAY8").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY9=System.getProperty("LOGDISPLAY9")!=null&&System.getProperty("LOGDISPLAY9").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY10=System.getProperty("LOGDISPLAY10")!=null&&System.getProperty("LOGDISPLAY10").equalsIgnoreCase("true")?true:false;
	   LOGDISPLAY0=System.getProperty("LOGDISPLAY0")!=null&&System.getProperty("LOGDISPLAY0").equalsIgnoreCase("true")?true:false;
	   LOGDEBUG=System.getProperty("LOGDEBUG")!=null&&System.getProperty("LOGDEBUG").equalsIgnoreCase("true")?true:false;
  }
}
