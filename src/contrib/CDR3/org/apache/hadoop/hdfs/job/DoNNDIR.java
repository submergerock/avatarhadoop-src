package org.apache.hadoop.hdfs.job;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.hdfs.protocol.ClientDatanodeProtocol;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.FSDirectory;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.job.tools.getHostIP;
import org.apache.hadoop.job.tools.tools;
import org.apache.hadoop.net.NetUtils;

public class DoNNDIR implements JobProtocol{
	public  Configuration confg = null; 
	public  NameNode nameNode = null;
	public  DataNode dataNode = null;
	public  Job job = null;
	
	public DoNNDIR() { 
  }
	
	public DoNNDIR(NameNode nameNode,Job e,Configuration confg) {
    this.job = e;
    this.nameNode = nameNode;  
    this.confg = confg;    
  }
	
	public HashMap<String,StringBuffer> handle5(){
		System.out.println("------------------------------DoNNDIR------------------------------");
		System.out.println("MyJob:" + "this job come from " + job.getConf().get("hdfs.job.from.ip"));
		System.out.println("MyJob:" + "hdfs.job.param.sessionId : " + job.getConf().get("hdfs.job.param.sessionId"));
		System.out.println("MyJob:" + "hdfs.job.param.indexDir : " + job.getConf().get("hdfs.job.param.indexDir"));
		this.job.getConf().setClass("hdfs.job.class",DoINDEX1.class,JobProtocol.class);
		job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());	
		if(this.nameNode == null)
			return null;
		HashMap<String,StringBuffer> tmp = getIndexDataNodePath();		
		if(tmp == null)
			return null;
		for(String DNIp : tmp.keySet()){
			StringBuffer indexFiles = tmp.get(DNIp);
			System.out.println("DNIp : " + DNIp);
			System.out.println("indexFiles : " + indexFiles);			
		}
		
		System.out.println("hdfs.job.param.inputpara : "
				+ job.getConf().get("hdfs.job.param.inputpara"));
		System.out.println("++++++++++++++++++++++++++++++DoNNDIR++++++++++++++++++++++++++++++");
		return  tmp;
	}
	
	/**
	 * 获取复合索引在dataNode上的路径信息
	 * @return
	 */
	public HashMap<String,StringBuffer> getIndexDataNodePath(){
		String indexDir = job.getConf().get("hdfs.job.param.indexDir");
		//1.获取复合索引文件列表
		ArrayList<String> indexFilesPathList = getMergeIndexPathList(indexDir);
		//2.获取索引文件dataNode信息,将dataNode信息存入hashMap
		return getPathMap(indexFilesPathList);
	}
	/**
	 * 获取复合索引文件列表
	 * @param indexDir
	 * @return
	 */
	public ArrayList<String> getMergeIndexPathList(String indexDir){
		ArrayList<String> indexFilesPathList = new ArrayList<String>();	
		FSDirectory fsDir = this.nameNode.namesystem.dir;
		for(FileStatus indexDirStatus : fsDir.getListing(indexDir)){
			
			if(!indexDirStatus.getPath().getName().equalsIgnoreCase("tmp") && indexDirStatus.isDir() ){
				FileStatus[] fs = fsDir.getListing(indexDirStatus.getPath().toString());				
				for(FileStatus f : fs){
					if(f.getPath().getName().contains("merger.data")){
						indexFilesPathList.add(f.getPath().toString());						
						break;
					}
				}
			}
		}
		return indexFilesPathList;
	}
	/**
	 * 获取索引文件dataNode信息,将dataNode信息存入hashMap
	 * @param indexFilesPathList
	 * @return
	 */
	public HashMap<String,StringBuffer> getPathMap(ArrayList<String> indexFilesPathList){
		HashMap<String,StringBuffer> DNIPtoIndexFiles = new HashMap<String,StringBuffer>();
		String[] pathArray = new String[indexFilesPathList.size()];
		
		String[] test = new String[indexFilesPathList.size()];
		
		test = indexFilesPathList.toArray(pathArray);
		
		//for(int i = 0 ;i < indexFilesPathList.size();i++){
		//	System.out.println("pathArray[" + i + "] :" + pathArray[i] + "test[" + i + "] :" + test[i] );
		//}
		String[] indexFilesPath = new String[indexFilesPathList.size()];
		int j = 0;
		for(String indexFilePath : indexFilesPathList){
			//System.out.println("MyJob:" + "indexFilePath : " + indexFilePath);
			//try {
				//nameNode.getBlockLocations(indexFilePath, 0, 777);
			//} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			//}
			indexFilesPath[j++] = indexFilePath;
		}
		
		try {
			String DNIP = null;
			LocatedBlocks[] lbArr = this.nameNode.getBlockLocationsForMutilFile(indexFilesPath,0, 777);
			for(int i=0;i<lbArr.length;i++){
				DatanodeInfo[] dis = lbArr[i++].getLocatedBlocks().get(0).getLocations();
				DatanodeInfo di = dis[((int)(Math.random()*1000))%dis.length];				
				DNIP = di.getHost() + ":" + di.getIpcPort();
				if(!DNIPtoIndexFiles.containsKey(DNIP)){
					System.out.println("MyJob:" + DNIP + "indexFilesPathList.get(" +i+ ") : " + indexFilesPathList.get(i));
					DNIPtoIndexFiles.put(DNIP,(new StringBuffer(indexFilesPathList.get(i))));
				}else{
					DNIPtoIndexFiles.put(DNIP,
							DNIPtoIndexFiles.get(DNIP).append(",").append(indexFilesPathList.get(i)));
				}
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return DNIPtoIndexFiles;
	}
	
	
	
	
	
	public HashMap<String,StringBuffer> handle(){
		System.out.println("------------------------------DoNNDIR------------------------------");
		System.out.println("MyJob:" + "this job come from " + job.getConf().get("hdfs.job.from.ip"));
		System.out.println("MyJob:" + "hdfs.job.param.sessionId : " + job.getConf().get("hdfs.job.param.sessionId"));
		System.out.println("MyJob:" + "hdfs.job.param.indexDir : " + job.getConf().get("hdfs.job.param.indexDir"));
		this.job.getConf().setClass("hdfs.job.class",DoINDEX1.class,JobProtocol.class);
		job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());
		String indexDir = job.getConf().get("hdfs.job.param.indexDir");
		//s = s + " + DoNNDIR + " + getHostIP.getLocalIP();
		//job.getConf().set("hdfs.job.param",s);
		
		//System.out.println("job.getConf().get(\"hdfs.job.param\") : " + s);
		if(this.nameNode == null)
			return null;
		ArrayList<String> indexFilesPathList = new ArrayList<String>();
		HashMap<String,StringBuffer> DNIPtoIndexFiles = new HashMap<String,StringBuffer>();
		
		for(FileStatus indexDirStatus : this.nameNode.namesystem.dir.getListing(indexDir)){
			
			if(!indexDirStatus.getPath().getName().equalsIgnoreCase("tmp") && indexDirStatus.isDir() ){
				//System.out.println("MyJob:" + "indexDirStatus.getPath().toString() : " +indexDirStatus.getPath().toString());
				FileStatus[] fs = this.nameNode.namesystem.dir.getListing(indexDirStatus.getPath().toString());				
				for(FileStatus f : fs){
					if(f.getPath().getName().contains("merger.data")){
						//System.out.println("MyJob:" + "f.getPath() : " +f.getPath());
						indexFilesPathList.add(f.getPath().toString());
						break;
					}
				}
			}
		}
		System.out.println("MyJob: indexFilesPathList.size() : " + indexFilesPathList.size());
		int i = 0;
		String[] indexFilesPath = new String[indexFilesPathList.size()];
		for(String indexFilePath : indexFilesPathList){
			//System.out.println("MyJob:" + "indexFilePath : " + indexFilePath);
			//try {
				//nameNode.getBlockLocations(indexFilePath, 0, 777);
			//} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			//}
			indexFilesPath[i++] = indexFilePath;
		}
		
		LocatedBlocks[] lbArr = null;
		String DNIP = "";
		try {
			lbArr = nameNode.getBlockLocationsForMutilFile(indexFilesPath,0, 777);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("MyJob: lbArr.length : " + lbArr.length);
		//for(LocatedBlocks lb : lbArr){
			//System.out.println("MyJob: lb.getLocatedBlocks().size() : " + lb.getLocatedBlocks().size());
			
			//System.out.println("MyJob: lb.getLocatedBlocks().get(0).getLocations().length : " 
					//+ lb.getLocatedBlocks().get(0).getLocations().length);
			
			//for(DatanodeInfo di : lb.getLocatedBlocks().get(0).getLocations()){
				//System.out.println("MyJob: di.getHost() : " + di.getHost());
			//}			
		//}
		
		i = 0;
		StringBuffer sb = new StringBuffer();
		System.out.println("MyJob: indexFilesPath.length : " + indexFilesPath.length);
		for(String indexFilePath : indexFilesPath){
			//System.out.println("MyJob: indexFilePath : " + indexFilePath);
			DatanodeInfo[] dis = lbArr[i++].getLocatedBlocks().get(0).getLocations();
			//for(DatanodeInfo d : dis){
				//sb.append(d.getHost()+",");
			//}
			//System.out.println("MyJob:" +"dis:"+sb); 
					
			DatanodeInfo di = dis[((int)(Math.random()*1000))%dis.length];
			
			DNIP = di.getHost() + ":" + di.getIpcPort(); 
			
			//System.out.println("MyJob:" + "indexFilePath : " + indexFilePath + " <----> DNIP : " + DNIP);
			if(!DNIPtoIndexFiles.containsKey(DNIP))
			{
				DNIPtoIndexFiles.put(DNIP,(new StringBuffer(indexFilePath)));
			}
			else
			{
				DNIPtoIndexFiles.put(DNIP,
						DNIPtoIndexFiles.get(DNIP).append(",").append(indexFilePath));
			}				
			// System.out.println(paths[i]+"--"+blocks[i].getBlockName());
		}	
		
		for(String DNIp : DNIPtoIndexFiles.keySet()){
			StringBuffer indexFiles = DNIPtoIndexFiles.get(DNIp);
			System.out.println("DNIp : " + DNIp);
			System.out.println("indexFiles : " + indexFiles);			
		}
		
		String[] DNIPs = job.getConf().getStrings("hdfs.job.DNIPs");
		if(DNIPtoIndexFiles.keySet().size() < DNIPs.length){
			for(String DNIp : DNIPs){
				if(!DNIPtoIndexFiles.keySet().contains(DNIp)){
					System.out.println("there is no DNIp : " + DNIp);			
					DNIPtoIndexFiles.put(DNIP,new StringBuffer(""));
				}
			}
		}
		System.out.println("++++++++++++++++++++++++++++++DoNNDIR++++++++++++++++++++++++++++++");
		return DNIPtoIndexFiles;
		//return null;
	}
	
	
	public void handle3(){
		System.out.println("-----------DoNNDIR------------");
		System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
		
		this.job.getConf().setClass("hdfs.job.class",DoINDEX1.class,JobProtocol.class);
		job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());
		ClientDatanodeProtocol datanode00 = null;
		InetSocketAddress dataNodeAddr00 = NetUtils.createSocketAddr("192.168.1.13", 50020);
		ClientDatanodeProtocol datanode01 = null;
		InetSocketAddress dataNodeAddr01 = NetUtils.createSocketAddr("192.168.1.14", 50020);
		ClientDatanodeProtocol datanode02 = null;
		InetSocketAddress dataNodeAddr02 = NetUtils.createSocketAddr("192.168.1.19", 50020);
		ClientDatanodeProtocol datanode03 = null;
		InetSocketAddress dataNodeAddr03 = NetUtils.createSocketAddr("192.168.1.20", 50020);
		
		try {
			datanode00 = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
					ClientDatanodeProtocol.versionID, dataNodeAddr00, this.confg,
			        NetUtils.getSocketFactory(this.confg, ClientDatanodeProtocol.class));
			datanode01 = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
					ClientDatanodeProtocol.versionID, dataNodeAddr01, this.confg,
			        NetUtils.getSocketFactory(this.confg, ClientDatanodeProtocol.class));
			datanode02 = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
					ClientDatanodeProtocol.versionID, dataNodeAddr02, this.confg,
			        NetUtils.getSocketFactory(this.confg, ClientDatanodeProtocol.class));
			datanode03 = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
					ClientDatanodeProtocol.versionID, dataNodeAddr03, this.confg,
			        NetUtils.getSocketFactory(this.confg, ClientDatanodeProtocol.class));
			datanode00.submitJob(job);
			datanode01.submitJob(job);
			datanode02.submitJob(job);
			datanode03.submitJob(job);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public void handle2(){	   
	  	Configuration conf = this.job.getConf();
	  	String secondIndexPath = conf.get("CDR.secondIndexPath");
	  	String[] filePath = null;
	  	//HashMap<String, ArrayList<String>> DNtoSecondIndexFile = null;
	  	HashMap<DatanodeInfo, String> DNtoSecondIndexFile = null;
	  	
	  	FileStatus[] fileStatus = null;
	  	ArrayList<FileStatus> fileStatusWithOutDIR = new ArrayList<FileStatus>();
	  	LocatedBlocks[] lbs = null;
	  	
	  	ClientDatanodeProtocol datanode = null;
		InetSocketAddress dataNodeAddr = null ;
	  	 
  	    try {
  		fileStatus = this.nameNode.namesystem.dir.getListing(secondIndexPath);
  		for(FileStatus fs : fileStatus){  			
  			if(fs.isDir() == false)
  			fileStatusWithOutDIR.add(fs);
  		}
  		
	  	filePath = new String[fileStatusWithOutDIR.size()];
	  	for(int i =0 ;i < fileStatusWithOutDIR.size();  i++){	  		
	  		filePath[i] = fileStatusWithOutDIR.get(i).getPath().toString();
	  		System.out.println("filePath[" + i + "] : " + filePath[i]);
	  	}
	  	lbs = this.nameNode.getBlockLocationsForMutilFile(filePath, 0, 777);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		

		int sumOfDN = 0;
		DatanodeInfo DNIP = null;
		for(int i = 0 ;i < lbs.length ;i++){
			sumOfDN = lbs[i].getLocatedBlocks().get(0).getLocations().length;
			//System.out.println("sumOfDN : " + sumOfDN );
			DNIP = lbs[i].getLocatedBlocks().get(0).getLocations()[(int)Math.random()%sumOfDN];
			for(DatanodeInfo di : lbs[i].getLocatedBlocks().get(0).getLocations()){
				System.out.print("di : " + di + ",");
			}
			System.out.println(" ");
			System.out.println("DNIP : " + DNIP + ",filePath[" + i + "] : " + filePath[i]);
			if(DNtoSecondIndexFile == null){
				DNtoSecondIndexFile = new HashMap<DatanodeInfo, String>();
				DNtoSecondIndexFile.put(DNIP, filePath[i]);
			} else {
				if(DNtoSecondIndexFile.get(DNIP) == null){				
					DNtoSecondIndexFile.put(DNIP, filePath[i]);
				} else {
					DNtoSecondIndexFile.put(DNIP, DNtoSecondIndexFile.get(DNIP) + "," +filePath[i]);
				}
			}
		}
		
		
		Set<DatanodeInfo> DNIPs = DNtoSecondIndexFile.keySet();
		Iterator<DatanodeInfo> iteratorDNIP = DNIPs.iterator();
		
		this.job.getConf().set("hdfs.job.jobtype", "INDEX2");
		this.job.getConf().set("hdfs.namenode.ip", this.nameNode.getNameNodeAddress().toString());
		while(iteratorDNIP.hasNext()){
			DNIP = iteratorDNIP.next();
			String secondIndexFilesOnDN = DNtoSecondIndexFile.get(DNIP);	
			System.out.println("DNIP : " + DNIP + ",secondIndexFilesOnDN : " + secondIndexFilesOnDN);
			this.job.getConf().set("param0", secondIndexFilesOnDN);
			dataNodeAddr = NetUtils.createSocketAddr(DNIP.getHostName(), DNIP.getIpcPort());
			try {
				datanode = (ClientDatanodeProtocol)RPC.getProxy(ClientDatanodeProtocol.class,
						ClientDatanodeProtocol.versionID, dataNodeAddr, conf,
				        NetUtils.getSocketFactory(conf, ClientDatanodeProtocol.class));
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
			try {
				datanode.submitJob(job);
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
		}
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

	@Override
	public void stop() {
		System.out.println("-----------DoNNDIR stop!!------------");
	}
}
