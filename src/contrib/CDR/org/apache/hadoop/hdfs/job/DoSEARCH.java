package org.apache.hadoop.hdfs.job;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.job.tools.getHostIP;

public class DoSEARCH implements JobProtocol{
	public  Configuration confg = null; 
	public  NameNode nameNode = null;
	public  DataNode dataNode = null;
	public  Job job = null;	
	
	public DoSEARCH()  {  
		
	  }
	public DoSEARCH(DataNode dataNode,Job e)  {
    this.job = e;
    this.dataNode = dataNode;    
  }
	
	public HashMap<String,StringBuffer> handle(){
		System.out.println("------------------------------DoSEARCH------------------------------");
		System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
		
		//this.job.getConf().setClass("hdfs.job.class",DoINDEX1.class,JobProtocol.class);
		//job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());
		//System.out.println("hdfs.job.param.system.param : " + job.getConf().get("hdfs.job.param.system.param"));
		
		String message = job.getConf().get("hdfs.job.param.system.param");
		
		/*
		String[] str = message.toString().split("!@");
		
		List<List<String>> list = new ArrayList<List<String>>(); 
		if(str.length >= 2 && str[0] != "" && str[1] != ""){	
				try {
					list = FileUtils.lisPathcontent(str[1], str[0]);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
		}
		System.out.println("---------");
		for(List<String> l : list){			
			for(String s : l){
				System.out.print(s);
			}
			System.out.println("");
		}
		System.out.println("---------");
		*/
		
		Server.ChatProtocolHandler.messageReceived(message);
		
		//System.out.println("hdfs.job.param.inputpara: " + job.getConf().get("hdfs.job.param.inputpara"));
		System.out.println("++++++++++++++++++++++++++++++DoSEARCH++++++++++++++++++++++++++++++");
		return null;
		
	}
    public void handle2(){	
    	System.out.println("-----------DoSEARCH------------");
    	System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
	}
    @Override
	public Configuration getConfiguration() {
		return this.confg;
	}
    
	public DataNode getDataNode() {
		return this.dataNode;
	}

	@Override
	public Job getJob() {
		return this.job;
	}

	public NameNode getNameNode() {
		return this.nameNode;
	}

	@Override
	public void setConfiguration(Configuration conf) {
		this.confg = conf;		
	}

	@Override
	public void setDataNode(DataNode datanode) {
		this.dataNode = datanode;
	}

	@Override
	public void setJob(Job j) {
		this.job = j;
	}

	@Override
	public void setNameNode(NameNode namenode) {
		this.nameNode = namenode;
	}
	public void stop() {
		System.out.println("-----------DoSEARCH stop!!------------");
	}
}
