package org.apache.hadoop.hdfs.job;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.ClientDatanodeProtocol;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.job.tools.getHostIP;
import org.apache.hadoop.job.tools.tools;
import org.apache.hadoop.net.NetUtils;

public class DoINDEX2 implements JobProtocol{
	public final static int SECOND_LEVEL_RECORD_SIZE = 33;
	//private java.io.File fs = null;
	public  Configuration confg = null; 
	public  ClientProtocol nameNode = null;
	public  DataNode dataNode = null;
	public  Job job = null;	
	
	public DoINDEX2()  {  
	  }
	public DoINDEX2(DataNode dataNode,Job j)  {
    this.job = j;
    this.dataNode = dataNode; 
  }
	
	public HashMap<String, StringBuffer> handle() {
		HashMap<String, StringBuffer> hsMap = new HashMap<String, StringBuffer>();
		
		InetSocketAddress nameNodeAddr = NetUtils.createSocketAddr(this.job.getConf().get("hdfs.job.NNIP", getHostIP.getLocalIP()));
		try {
			this.nameNode = (ClientProtocol)RPC.getProxy(ClientProtocol.class,
					ClientProtocol.versionID, nameNodeAddr, this.confg,
			        NetUtils.getSocketFactory(this.confg, ClientProtocol.class));
		
		
		String[] index2Path  = job.getConf().getStrings("hdfs.job.param");
		
		LocatedBlocks[] index2Blocks  = this.nameNode.getBlockLocationsForMutilFile(index2Path, 0, 777);
		
		Block[] index2Block = changeLocatedBlocksToBlocks(index2Blocks);
		
		String[] localIndex2Paths = dataNode.getBlockFiles(index2Block);	
		
		String[] hdfsIndex1Paths = new String[localIndex2Paths.length];
		String[] hdfsIndex1PathsOffset = new String[localIndex2Paths.length];
		int flag = 0;
		for (int i = 0; i < localIndex2Paths.length; i++) {
			// 查询二级索引
			if (binarySearch(Long.parseLong("90086"), localIndex2Paths[i]) != -1) {
				// 找到的数据
				long[] result=getOffsetAndLength(Long.parseLong("90086"),null);
				//hdfsPaths[flag] = localPaths[i].substring(0, localPaths[i].lastIndexOf("/"))+ "first_level.data";
				//hdfsPaths1[flag] = localPaths[i].substring(0, localPaths[i].lastIndexOf("/"))+ "first_level.data " +result[0] ;
				flag ++;
			}
		}		
		String[] temps = new String[flag];
		System.arraycopy(null,0,temps,0,flag);
//		nameNode.getBlockLocationsForMutilFile(arg0, arg1, arg2);
		LocatedBlocks[] lbArr = nameNode.getBlockLocationsForMutilFile(temps,0, 777);
		System.arraycopy(null,0,temps,0,flag);
		for(int j = 0 ;j < lbArr.length ; j++){
			String host = lbArr[j].get(0).getLocations()[0].getHost();			
			if(hsMap.get(host)==null){
				StringBuffer sBuffer = new StringBuffer();
				
				hsMap.put(host,sBuffer.append(temps[j]));
			}else {
				hsMap.put(host, hsMap.get(host).append("$$$").append(temps[j]));
			}
		}
		} catch (IOException e) {
			e.printStackTrace();
		}
//		String[] abPath = datanode.getBlockFiles(blocks);
		// 查找所在的dn
		return hsMap;
	}
	
	
	public HashMap<String,String> handle4(){
		System.out.println("+++++DoINDEX2+++++");
		System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
		
		this.job.getConf().setClass("hdfs.job.class",DoINDEX1.class,JobProtocol.class);
		job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());
		String s = job.getConf().get("hdfs.job.param");
		s = s + " + DoINDEX2 + " + getHostIP.getLocalIP();
		job.getConf().set("hdfs.job.param",s);
		
		
        System.out.println("job.getConf().get(\"hdfs.job.param\") : " + s);
		
		
		String[] DNIPs = job.getConf().getStrings("hdfs.job.DNIPs");
		
		HashMap<String,String> DNIPtoString = new HashMap<String,String>();
		for(String DNIP : DNIPs){
			DNIPtoString.put(DNIP, s);
		}		
		return DNIPtoString;
		
	}
    public void handle2(){	
    	System.out.println("-----------DoINDEX2------------");
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

	public ClientProtocol getNameNode() {
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
	
	public Block[] changeLocatedBlocksToBlocks(LocatedBlocks[] lbs){
		Block[] b = new Block[lbs.length];
		int r = 0;
		
		for(int i = 0 ;i < lbs.length; i++){
			r = (int)(Math.random()*1000);
			b[i] = lbs[i].getLocatedBlocks().get(r%lbs[i].getLocatedBlocks().size()).getBlock();
		}
		return b;		
	}
	public long binarySearch(long target, String secondLevelFileName) throws IOException {
		RandomAccessFile inSecond = new RandomAccessFile(secondLevelFileName,"r");
		long start = 0;
		long end = getIndexNum(inSecond) - 1;
		int count = 0;
		while (start <= end) {
			count++;
			long middleIndex = (start + end) / 2;
			String[] strs = getData(middleIndex, inSecond).split(" ");
			long data = Long.valueOf(strs[0]);
			if (target == data) {
				// System.out.println("binary serach matched times:"+count);
				inSecond.close();
				return Long.valueOf(strs[1]);
			}
			if (target >= data) {
				start = middleIndex + 1;
			} else {
				end = middleIndex - 1;
			}
		}
		inSecond.close();
		return -1;
	}	
	
	public long[] getOffsetAndLength(long target,String secondIndexPath) throws IOException
	{
		//FSDataInputStream inSecond = fs.open(new Path(secondIndexPath));
		
		java.io.File f = new java.io.File(secondIndexPath);
		
		FSDataInputStream inSecond = new FSDataInputStream(new FileInputStream(f));
		
		long[] result=new long[2];
		result[0]=-1L;
		result[1]=-1L;
		
		long start = 0;
		long end = -1;//getIndexNum(secondIndexPath) - 1;
		int count=0;
		String str;
		String[] strs;
		long length=0;
		while (start<= end)
		{	
			count++;
			long middleIndex=(start + end)/2;
			str=getData(middleIndex,inSecond);
			strs=str.split(" ");
			long data=Long.valueOf(strs[0]);
			if (target==data)
			{
				result[0]=Long.valueOf(strs[1]); // set offset;
				
				middleIndex++;
				str=getData(middleIndex,inSecond);
				if(str!=null)
				{
					strs=str.split(" ");
					result[1]=Long.valueOf(strs[1])-result[0]-2;
					break;
				}
			}	
			if (target >= data)
			{
				start = middleIndex + 1;
			}
			else
			{	
				end = middleIndex - 1;
			}
		 }
		 inSecond.close();
		 return result;
	}
	
	
	
	public  String getData(long lineNum,FSDataInputStream in) throws IOException
	{
		in.seek(SECOND_LEVEL_RECORD_SIZE*lineNum);
		return in.readLine();
	}
/*
	public  long getIndexNum(String secondLevelPath) throws IOException
	{ 
		FileStatus fileStatus=fs.getFileStatus(new Path(secondLevelPath));
		return (fileStatus.getLen()/SECOND_LEVEL_RECORD_SIZE);  
	}*/
	
	private String getData(long middleIndex, RandomAccessFile inSecond) throws IOException {
		// TODO Auto-generated method stub
		inSecond.seek(SECOND_LEVEL_RECORD_SIZE * middleIndex);
		return inSecond.readLine();
	}
	
	private int getIndexNum(RandomAccessFile inSecond) throws IOException {
		return (int) inSecond.length();
		// return 0;
	}
	
      public StringBuffer montitor(String url) throws IOException{
		StringBuffer result = new StringBuffer("");
		//Configuration conf = new Configuration();
		//Path path  = new Path(url);
		//FileSystem fs = FileSystem.get(URI.create(url),conf);
		FileStatus[] fileStatuses =  this.nameNode.getListing(url);
		for(int i=0;i<fileStatuses.length;i++){
			System.out.println(fileStatuses[i].getPath().toString()+"/second_level.data");
		    result.append(fileStatuses[i].getPath().toString()+"/second_level.data,");
		}
		result.substring(0, result.length()-1);
		return result;
	}
	//--------------------------------------------------
	
}
