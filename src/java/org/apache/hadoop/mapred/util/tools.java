package org.apache.hadoop.mapred.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.mapred.JobConf;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class tools {
	private static final Log LOG = LogFactory.getLog(tools.class);
	private static Set<String> finalParameters = new HashSet<String>();
	final private static FsPermission JOB_FILE_PERMISSION = 
	    FsPermission.createImmutable((short) 0644); // rw-r--r--
	
	public static void sleep(int milis){
		try {
			Thread.sleep(milis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}			
	}
	
	public static void hdfsXmlFileParse(Path srcHdfsFile,Path desHdfsFile,String attribute,String value,Configuration conf){
	  FileSystem fs = null;
	  InputStream in = null;	
	  Document doc = null;
	  Element root = null;
	  DocumentBuilder builder = null;
	  FSDataOutputStream out = null;
	  
	  
	  
	  
	  Properties properties = new Properties();
	  DocumentBuilderFactory docBuilderFactory 
        = DocumentBuilderFactory.newInstance();
      //ignore all comments inside the xml file
      docBuilderFactory.setIgnoringComments(true);
      //allow includes in the xml file
      docBuilderFactory.setNamespaceAware(true);
      
      docBuilderFactory.setXIncludeAware(true); 
      
      try {	             
	        builder = docBuilderFactory.newDocumentBuilder();		    	    
			fs = srcHdfsFile.getFileSystem(conf);
			in = new BufferedInputStream(fs.open(srcHdfsFile));
			doc = builder.parse(in);
			 if (root == null) {
			        root = doc.getDocumentElement();
			      }
	      if (!"configuration".equals(root.getTagName()))
	        LOG.fatal("bad conf file: top-level element not <configuration>");
	      
	      
	      NodeList props = root.getChildNodes();
	      int r = 1;
	      for (int i = 0; i < props.getLength(); i++) {
	        Node propNode = props.item(i);
	        if (!(propNode instanceof Element))
	          continue;
	        Element prop = (Element)propNode;
	        if ("configuration".equals(prop.getTagName())) {
	          loadResource(properties, prop, false);
	          continue;
	        }
	        if (!"property".equals(prop.getTagName()))
	          LOG.warn("bad conf file: element not <property>");
	        NodeList fields = prop.getChildNodes();
	        String attr = null;
	        String valueTmp = null;
	        boolean finalParameter = false;
	        for (int j = 0; j < fields.getLength(); j++) {
	          Node fieldNode = fields.item(j);
	          if (!(fieldNode instanceof Element))
	            continue;
	          Element field = (Element)fieldNode;
	          if ("name".equals(field.getTagName()) && field.hasChildNodes())
	            attr = ((Text)field.getFirstChild()).getData().trim();
	          if ("value".equals(field.getTagName()) && field.hasChildNodes())
	        	  valueTmp = ((Text)field.getFirstChild()).getData();
	          if ("final".equals(field.getTagName()) && field.hasChildNodes())
	            finalParameter = "true".equals(((Text)field.getFirstChild()).getData());
	        }
	        
	        //LOG.info("=================" + i + "====================");
	        LOG.info("*****" + r + "*****" + attr  + " == " +  valueTmp);
	        r++;
	        //LOG.info("valueTmp == " + valueTmp);
	        
	        // Ignore this parameter if it has already been marked as 'final'
	        if (attr != null && valueTmp != null) {
	         // if (!finalParameters.contains(attr)) {
	            properties.setProperty(attr, valueTmp);
	            //if (finalParameter)
	           //   finalParameters.add(attr);
	         // } else {
	          //  LOG.warn(srcHdfsFile +":a attempt to override final parameter: "+attr+";  Ignoring.");
	         // }
	        }
	      }
			
	      
	      out = FileSystem.create(fs, desHdfsFile,
                  new FsPermission(JOB_FILE_PERMISSION));
	      writeXml(out,properties);	 
	      
		} catch (IOException e) {
			e.printStackTrace();
		} catch (UnsupportedOperationException e) {
	        LOG.error("Failed to set setXIncludeAware(true) for parser "
	                + docBuilderFactory
	                + ":" + e,
	                e);
	    } catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} finally {
            try {
				 in.close();
				 out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
	}

private static void loadResource(Properties properties, Object name, boolean quiet) {
    try {
      DocumentBuilderFactory docBuilderFactory 
        = DocumentBuilderFactory.newInstance();
      //ignore all comments inside the xml file
      docBuilderFactory.setIgnoringComments(true);

      //allow includes in the xml file
      docBuilderFactory.setNamespaceAware(true);
      try {
          docBuilderFactory.setXIncludeAware(true);
      } catch (UnsupportedOperationException e) {
        LOG.error("Failed to set setXIncludeAware(true) for parser "
                + docBuilderFactory
                + ":" + e,
                e);
      }
      DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
      Document doc = null;
      Element root = null;

      if (name instanceof URL) {                  // an URL resource
        URL url = (URL)name;
        if (url != null) {
          if (!quiet) {
            LOG.info("parsing " + url);
          }
          doc = builder.parse(url.toString());
        }
      } else if (name instanceof Path) {          // a file resource
        // Can't use FileSystem API or we get an infinite loop
        // since FileSystem uses Configuration API.  Use java.io.File instead.
        File file = new File(((Path)name).toUri().getPath())
          .getAbsoluteFile();
        if (file.exists()) {
          if (!quiet) {
            LOG.info("parsing " + file);
          }
          InputStream in = new BufferedInputStream(new FileInputStream(file));
          try {
            doc = builder.parse(in);
          } finally {
            in.close();
          }
        }
      } else if (name instanceof InputStream) {
        try {
          doc = builder.parse((InputStream)name);
        } finally {
          ((InputStream)name).close();
        }
      } else if (name instanceof Element) {
        root = (Element)name;
      }

      if (doc == null && root == null) {
        if (quiet)
          return;
        throw new RuntimeException(name + " not found");
      }

      if (root == null) {
        root = doc.getDocumentElement();
      }
      if (!"configuration".equals(root.getTagName()))
        LOG.fatal("bad conf file: top-level element not <configuration>");
      NodeList props = root.getChildNodes();
      for (int i = 0; i < props.getLength(); i++) {
        Node propNode = props.item(i);
        if (!(propNode instanceof Element))
          continue;
        Element prop = (Element)propNode;
        if ("configuration".equals(prop.getTagName())) {
          loadResource(properties, prop, quiet);
          continue;
        }
        if (!"property".equals(prop.getTagName()))
          LOG.warn("bad conf file: element not <property>");
        NodeList fields = prop.getChildNodes();
        String attr = null;
        String value = null;
        boolean finalParameter = false;
        for (int j = 0; j < fields.getLength(); j++) {
          Node fieldNode = fields.item(j);
          if (!(fieldNode instanceof Element))
            continue;
          Element field = (Element)fieldNode;
          if ("name".equals(field.getTagName()) && field.hasChildNodes())
            attr = ((Text)field.getFirstChild()).getData().trim();
          if ("value".equals(field.getTagName()) && field.hasChildNodes())
            value = ((Text)field.getFirstChild()).getData();
          if ("final".equals(field.getTagName()) && field.hasChildNodes())
            finalParameter = "true".equals(((Text)field.getFirstChild()).getData());
        }
        
        // Ignore this parameter if it has already been marked as 'final'
        if (attr != null && value != null) {
          if (!finalParameters.contains(attr)) {
            properties.setProperty(attr, value);
            if (finalParameter)
              finalParameters.add(attr);
          } else {
            LOG.warn(name+":a attempt to override final parameter: "+attr
                     +";  Ignoring.");
          }
        }
      }
        
    } catch (IOException e) {
      LOG.fatal("error parsing conf file: " + e);
      throw new RuntimeException(e);
    } catch (DOMException e) {
      LOG.fatal("error parsing conf file: " + e);
      throw new RuntimeException(e);
    } catch (SAXException e) {
      LOG.fatal("error parsing conf file: " + e);
      throw new RuntimeException(e);
    } catch (ParserConfigurationException e) {
      LOG.fatal("error parsing conf file: " + e);
      throw new RuntimeException(e);
    }
  }
/** 
 * Write out the non-default properties in this configuration to the give
 * {@link OutputStream}.
 * 
 * @param out the output stream to write to.
 */
	public static void writeXml(OutputStream out,Properties properties) throws IOException {
	 // Properties properties = getProps();
	  try {
	    Document doc =
	      DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
	    Element conf = doc.createElement("configuration");
	    doc.appendChild(conf);
	    conf.appendChild(doc.createTextNode("\n"));
	    for (Enumeration e = properties.keys(); e.hasMoreElements();) {
	      String name = (String)e.nextElement();
	      Object object = properties.get(name);
	      String value = null;
	      if (object instanceof String) {
	        value = (String) object;
	      }else {
	        continue;
	      }
	      Element propNode = doc.createElement("property");
	      conf.appendChild(propNode);
	    
	      Element nameNode = doc.createElement("name");
	      nameNode.appendChild(doc.createTextNode(name));
	      propNode.appendChild(nameNode);
	    
	      Element valueNode = doc.createElement("value");
	      valueNode.appendChild(doc.createTextNode(value));
	      propNode.appendChild(valueNode);
	
	      conf.appendChild(doc.createTextNode("\n"));
	    }
	  
	    DOMSource source = new DOMSource(doc);
	    StreamResult result = new StreamResult(out);
	    TransformerFactory transFactory = TransformerFactory.newInstance();
	    Transformer transformer = transFactory.newTransformer();
	    transformer.transform(source, result);
	  } catch (Exception e) {
	    throw new RuntimeException(e);
	  }
	}

	public static void main(String[] args) {
		
		Configuration conf = new Configuration();
		hdfsXmlFileParse(new Path("hdfs://192.168.1.9:9000/user/wl826214/out41614/_logs/history/0.0.0.0_1310775573711_job_201107152019_0001_conf.xml"),
				new Path("hdfs://192.168.1.9:9000/tmp/job.xml"),
		null,null,conf);
	}
}
