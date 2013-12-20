package org.apache.hadoop.hdfs.job;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.HashMap;

import net.sf.json.JSONObject;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.job.Job.QueryConditions;
import org.apache.hadoop.hdfs.job.SearchCDRTools.*;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.ClientDatanodeProtocol;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.job.tools.getHostIP;
import org.apache.hadoop.job.tools.tools;
import org.apache.hadoop.net.NetUtils;


public class DoINDEX1_20110927 implements JobProtocol{
	public  Configuration confg = null; 
	public  ClientProtocol nameNode = null;
	public  DataNode dataNode = null;
	public  Job job = null;	
	HashMap<String,StringBuffer> result = new HashMap<String,StringBuffer>();
	
	public DoINDEX1_20110927()  {  	
	  }
	public DoINDEX1_20110927(DataNode dataNode,Job e)  {
    this.job = e;
    this.dataNode = dataNode;    
  }
	@Override
	public HashMap<String, StringBuffer> handle() {
		
		HashMap<String,StringBuffer> DNIPtoFileandOffsets = new HashMap<String,StringBuffer>();
		System.out.println("------------------------------DoINDEX1------------------------------");

		System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
		
		System.out.println("hdfs.job.param.system.param : " + job.getConf().get("hdfs.job.param.system.param"));
		
		//System.out.println("hdfs.job.param.inputpara: " + job.getConf().get("hdfs.job.param.inputpara"));
		
		this.job.getConf().setClass("hdfs.job.class",DoSEARCH.class,JobProtocol.class);
		job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());
				
		queryConditions query = paraToObject(job.getConf().get("hdfs.job.param.inputpara"));		
		
		String[] index2Files = job.getConf().getStrings("hdfs.job.param.system.param");		
		
		String sessionId = job.getConf().get("hdfs.job.param.sessionId");		

		InetSocketAddress nameNodeAddr = NetUtils.createSocketAddr(job.getConf().get("hdfs.job.NN.ip"));
		
		LocatedBlocks[] lbArr = null;

		try {
			nameNode = (ClientProtocol) RPC.getProxy(ClientProtocol.class,
					ClientProtocol.versionID, nameNodeAddr, confg, NetUtils
							.getSocketFactory(confg, ClientProtocol.class));
			lbArr = nameNode.getBlockLocationsForMutilFile(index2Files,0, 777);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Block[] locatedBlocks = new Block[lbArr.length];
		int i = 0;
		for(LocatedBlocks lb : lbArr){
			locatedBlocks[i++] = lb.get(0).getBlock();
		}
		
		String[] locatedFiles = null;
		
		try {
			locatedFiles = this.dataNode.getBlockFiles(locatedBlocks);
			
			for(String locatedFile : locatedFiles){
				//System.out.println("locatedFile : " + locatedFile);
			}
			for(String locatedFile : locatedFiles){
				searchCDR(query,locatedFile);
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String[] dataFiles = new String[result.size()];
		i = 0 ;
		for(String dataFile : result.keySet()){
			dataFiles[i++] = dataFile;
		}		
		
		String DNIP = "";
		try {
			lbArr = nameNode.getBlockLocationsForMutilFile(dataFiles,0, 777);
		} catch (IOException e) {
			e.printStackTrace();
		}
		i = 0;
		for(String dataFile : result.keySet()){		
			
			DatanodeInfo[] dis = lbArr[i++].getLocatedBlocks().get(0).getLocations();
					
			DatanodeInfo di = dis[((int)(Math.random()*1000))%dis.length];
			
			DNIP = di.getHost() + ":" + di.getIpcPort(); 
			
			StringBuffer offsets = result.get(dataFile);
			
			if(!DNIPtoFileandOffsets.containsKey(DNIP))
			{
				DNIPtoFileandOffsets.put(DNIP,(new StringBuffer(sessionId + "!@" + dataFile + " ")).append(offsets));
			}
			else
			{
				DNIPtoFileandOffsets.put(DNIP,
						DNIPtoFileandOffsets.get(DNIP).append("#" + dataFile + " ").append(offsets));
			}				
			// System.out.println(paths[i]+"--"+blocks[i].getBlockName());
		}
		String[] DNIPs = job.getConf().getStrings("hdfs.job.DNIPs");
		if(DNIPtoFileandOffsets.keySet().size() < DNIPs.length){
			for(String DNIp : DNIPs){
				if(!DNIPtoFileandOffsets.keySet().contains(DNIp)){
					System.out.println("there is no DNIp : " + DNIp);
					DNIPtoFileandOffsets.put(DNIp,new StringBuffer(sessionId + "!@"));
				}
				System.out.println("DNIp : " + DNIp);
				//System.out.println("DNIPtoFileandOffsets.get(DNIp) : " + DNIPtoFileandOffsets.get(DNIp));				
			}
		}
		
		for(String DNIp : DNIPtoFileandOffsets.keySet()){
			System.out.println("DNIp : " + DNIp);
		}
		
		System.out.println("++++++++++++++++++++++++++++++DoINDEX1++++++++++++++++++++++++++++++");
		return DNIPtoFileandOffsets;
	}
	public HashMap<String,StringBuffer> handle3(){
		System.out.println("+++++DoINDEX1+++++");
		System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
		
		this.job.getConf().setClass("hdfs.job.class",DoSEARCH.class,JobProtocol.class);
		job.getConf().set("hdfs.job.from.ip",getHostIP.getLocalIP());
		String s = job.getConf().get("hdfs.job.param");
		
		s = s + " + DoINDEX1 + " + getHostIP.getLocalIP();
		job.getConf().set("hdfs.job.param",s);
		
        System.out.println("job.getConf().get(\"hdfs.job.param\") : " + s);		
		
		String[] DNIPs = job.getConf().getStrings("hdfs.job.DNIPs");
		StringBuffer sb = new StringBuffer(s);
		HashMap<String,StringBuffer> DNIPtoString = new HashMap<String,StringBuffer>();
		for(String DNIP : DNIPs){
			DNIPtoString.put(DNIP, sb);
		}		
		return DNIPtoString;
		
	}
    public void handle2(){	
    	System.out.println("-----------DoINDEX1------------");
    	System.out.println("this job come from " + job.getConf().get("hdfs.job.from.ip"));
		this.job.getConf().setClass("hdfs.job.class",DoSEARCH.class,JobProtocol.class);
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
		System.out.println("-----------DoINDEX1 stop!!------------");
	}
	
	public long readLong(char[] temp){
		long tempLong = 8;
		if(temp!=null && temp.length>0){
			tempLong = Long.parseLong(String.valueOf(temp));
		}
		return tempLong;
	}
	
	
	public queryConditions paraToObject(String para){
		
		CDRBeanForIndex cdr=new CDRBeanForIndex(JSONObject.fromObject(para));
		
		
		long[] startTime=cdr.getStart_time_s();

		long[] endTime=cdr.getEnd_time_s();

		long[] opc=cdr.getOpc();

	    long[] dpc=cdr.getDpc();

		int tableType=cdr.getTableType();

		String cdrType=cdr.getCdr_type();

		int callType=cdr.getCDRCallType();

        String phoneNum=String.valueOf(cdr.getCalled_number());

        if(phoneNum==null||"".equals(phoneNum)||"null".equals(phoneNum))

        	phoneNum=String.valueOf(cdr.getCalling_number());

		int wangYuanType=cdr.getNetElem();

		String wangYuan=cdr.getNetElemId();
		
		queryConditions query;

		if(tableType!=2){
			query=new queryConditions(startTime, endTime,tableType,cdrType,callType, phoneNum,wangYuanType,wangYuan);
		} else {
			query=new queryConditions(startTime,endTime,tableType,callType,opc,dpc,phoneNum);
		}
		
		return query;		
	}
	
	public  void searchCDR(queryConditions query,String targetIndexFolder) throws NumberFormatException, IOException
	{		
		//System.out.println("-----searchCDR-----");
		cdrInfo cdr=new cdrInfo();
		File indexFile = new File(targetIndexFolder);
		
		//System.out.println("targetIndexFolder : " + targetIndexFolder);
		
		if(!indexFile.exists())
			return ;
		Date d1 = new Date();
		Date d2 = new Date();
		String info ="";
		long slen=8;
		byte[] buffer;		

		//FileInputStream indexFileis = new FileInputStream(indexFile);
		
		RandomAccessFile inFirst = new RandomAccessFile(indexFile,"r");
		
		
		//BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(indexFileis));
		//BufferedInputStream bis = new BufferedInputStream(indexFileis);		
		//DataInputStream inFirst= new DataInputStream(bis);		
		
		//bufferedreader.skip(4);
				
		inFirst.seek(4);
		slen = inFirst.readLong();
		//System.out.println("slen : " + slen);
		int sl = (int)slen/16;
		long sep = -1;
		long start = 0;
		long end = sl;
		long pn = Long.parseLong(query.getPhoneNum());
		//System.out.println("--------pn : " + pn);
		//long pn = Long.parseLong("90086");
		while (start<= end){
			//System.out.println("--------sep : " + sep);
			
 			 long middleIndex=(start + end)/2;	 
 			 
 			//System.out.println("--------start : " + start);
			//System.out.println("--------end : " + end);
			//System.out.println("--------middleIndex : " + middleIndex);
 			 inFirst.seek(4+8+(middleIndex*16));
 			 long data=inFirst.readLong();
 			// System.out.println("--------data : " + data);
			 if (pn==data)
			 {
				 sep = inFirst.readLong();
				 
				 break;
			 }	
			 if (pn >= data)
			 {
			 	start = middleIndex + 1;
			 }
			 else
			 {	
			 	end = middleIndex - 1;
			 }
		}
//		for(int i =0;i<sl;i++){			
//			if(inFirst.readLong()==Long.valueOf(query.getPhoneNum())){
//				sep = inFirst.readLong();
//				break;
//			}else {
//				inFirst.skipBytes(8);
//			}			
//		}			
		//System.out.println("sep : " + sep);
		if(sep!=-1){
			inFirst.seek(sep-8);
			info = inFirst.readLine();
			//System.out.println("info : " + info);
			
		} else {
			System.out.println("+++++searchCDR+++++");
			return;
		}
		Date d3 = new Date();
		//System.out.println(d3.getTime()-d2.getTime());
		inFirst.close();
		String[] infos=info.split(";");
		
		//System.out.println("infos.length : " + infos.length);
		
		//System.out.println("infos[0] : " + infos[0]);
		String[] strs;
		String[] recordPathOffset;
		
		if(query.tableType!=2)
		//if(true)
		{
			//System.out.println("query.tableType != 2 ");
			for(int j=0;j<infos.length;j++)
			{
				strs=infos[j].split(",");
				//System.out.println("infos["+ j +"] : " + infos[j]);
				try
				{
					cdr.setCdrType(strs[0]);
					cdr.setCallType(strs[1]);
					cdr.setStartTime(strs[2]);
					cdr.SetEndTime(strs[3]);
					cdr.setWangYuan(strs[4]);
					cdr.setFilePathRawOffset(strs[5]);
				}
				catch(Exception e)
				{
					System.out.println(strs[2]);
					System.out.println(strs[3]);
					System.out.println(targetIndexFolder);
					continue;
				}
		
	// check the cdr is fit the conditions;if is record it:)
		    
			if(SearchCDRTools.checkByQueryCondition(cdr,query))
//if(true)
				{
				    //System.out.println("in checkByQueryCondition");
					recordPathOffset=strs[5].split("%");
					if(!result.containsKey(recordPathOffset[0]))
					{
						//System.out.println("result.put()");
						result.put(recordPathOffset[0],new StringBuffer(recordPathOffset[1]));
					}
					else
					{
						result.put(recordPathOffset[0],result.get(recordPathOffset[0]).append(",").append(recordPathOffset[1]));
					}
//System.out.println(result.size());
				}
			}
		}
		else
		{
			//System.out.println("query.tableType == 2 ");
			biccCdrInfo biccCdr=new biccCdrInfo();
			for(int j=0;j<infos.length;j++)
			{
	// "z,"+startTime+","+endTime+","+opc+","+dpc+","+recordPath;
				strs=infos[j].split(",");
				biccCdr.setDpc(Long.valueOf(strs[4]));
				biccCdr.setOpc(Long.valueOf(strs[3]));
				biccCdr.setStartTime(Long.valueOf(strs[1]));
				biccCdr.setEndTime(Long.valueOf(strs[2]));
				biccCdr.setFilePath(strs[5]);
				biccCdr.setCallType(strs[0]);
	// check the cdr is fit the conditions;if is record it:)
				if(SearchCDRTools.checkBiccByQueryCondition(biccCdr,query))
//if(true)
				{
					recordPathOffset=strs[5].split("%");
					if(!result.containsKey(recordPathOffset[0]))
					{
						System.out.println("result.put()");
						result.put(recordPathOffset[0],new StringBuffer(recordPathOffset[1]));
					}
					else
					{
						result.put(recordPathOffset[0],result.get(recordPathOffset[0]).append(",").append(recordPathOffset[1]));
					}
				}
			}
		}
		
		//System.out.println("+++++searchCDR+++++");
	}
	
	
	public  void searchCDR3(queryConditions query,String targetIndexFolder) throws NumberFormatException, IOException
	{		
		System.out.println("-----searchCDR-----");
		cdrInfo cdr=new cdrInfo();
		File indexFile = new File(targetIndexFolder);
		
		System.out.println("targetIndexFolder : " + targetIndexFolder);
		
		if(!indexFile.exists())
			return ;
		Date d1 = new Date();
		Date d2 = new Date();
		String info ="";
		long slen=8;
		byte[] buffer;		

		FileInputStream indexFileis = new FileInputStream(indexFile);
		
		RandomAccessFile raf = new RandomAccessFile(indexFile,"rw");
		
		
		BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(indexFileis));
		BufferedInputStream bis = new BufferedInputStream(indexFileis);		
		DataInputStream inFirst= new DataInputStream(bis);		
		
		bufferedreader.skip(4);
		
		
		//raf.se
		//inFirst.re
//		slen = Bytes8tolong(buffer);
//		System.out.println(inFirst.readLong());
		int sl = (int)slen/16;
		long sep = -1;
		long start = 0;
		long end = sl;
		long pn = Long.parseLong(query.getPhoneNum());
		char[] temp = new char[8];
		while (start<= end){
 			 long middleIndex=(start + end)/2;	 			
 			 bufferedreader.skip(4+8+(middleIndex*16));
 			 bufferedreader.read(temp,0,8);
 			 long data=readLong(temp);
			 if (pn==data)
			 {
				 bufferedreader.read(temp,0,8);
				 sep=readLong(temp);
//				 sep = bufferedreader.readLong();
				 break;
			 }	
			 if (pn >= data)
			 {
			 	start = middleIndex + 1;
			 }
			 else
			 {	
			 	end = middleIndex - 1;
			 }
		}
		if(sep!=-1){
			bufferedreader.skip(sep-8);
			info = bufferedreader.readLine();
			String[] infos=info.split(";");			
		}
		Date d3 = new Date();
		System.out.println(d3.getTime()-d2.getTime());
		bufferedreader.close();
		String[] infos=info.split(";");
		String[] strs;
		String[] recordPathOffset;
		if(query.tableType!=2)
		{
			for(int j=0;j<infos.length;j++)
			{
				strs=infos[j].split(",");
				try
				{
					cdr.setCdrType(strs[0]);
					cdr.setCallType(strs[1]);
					cdr.setStartTime(strs[2]);
					cdr.SetEndTime(strs[3]);
					cdr.setWangYuan(strs[4]);
					cdr.setFilePathRawOffset(strs[5]);
				}
				catch(Exception e)
				{
					System.out.println(strs[2]);
					System.out.println(strs[3]);
					System.out.println(targetIndexFolder);
					continue;
				}
		
	// check the cdr is fit the conditions;if is record it:)
			if(SearchCDRTools.checkByQueryCondition(cdr,query))
//if(true)
				{
					recordPathOffset=strs[5].split("%");
					if(!result.containsKey(recordPathOffset[0]))
					{
						result.put(recordPathOffset[0],new StringBuffer(recordPathOffset[1]));
					}
					else
					{
						result.put(recordPathOffset[0],result.get(recordPathOffset[0]).append(",").append(recordPathOffset[1]));
					}
//System.out.println(result.size());
				}
			}
		}
		else
		{
			biccCdrInfo biccCdr=new biccCdrInfo();
			for(int j=0;j<infos.length;j++)
			{
	// "z,"+startTime+","+endTime+","+opc+","+dpc+","+recordPath;
				strs=infos[j].split(",");
				biccCdr.setDpc(Long.valueOf(strs[4]));
				biccCdr.setOpc(Long.valueOf(strs[3]));
				biccCdr.setStartTime(Long.valueOf(strs[1]));
				biccCdr.setEndTime(Long.valueOf(strs[2]));
				biccCdr.setFilePath(strs[5]);
				biccCdr.setCallType(strs[0]);
	// check the cdr is fit the conditions;if is record it:)
				if(SearchCDRTools.checkBiccByQueryCondition(biccCdr,query))
				{
					recordPathOffset=strs[5].split("%");
					if(!result.containsKey(recordPathOffset[0]))
					{
						result.put(recordPathOffset[0],new StringBuffer(recordPathOffset[1]));
					}
					else
					{
						result.put(recordPathOffset[0],result.get(recordPathOffset[0]).append(",").append(recordPathOffset[1]));
					}
				}
			}
		}
		System.out.println("+++++searchCDR+++++");
	}
	
	
	public  void searchCDR2(queryConditions query,String targetIndexFolder) throws NumberFormatException, IOException
	{		
		System.out.println("-----searchCDR2-----");
		
		result.put("/ffCDR22/bssap/20110922/18/1316687590_255_1316687591_504_ser208_7072.dat",new StringBuffer("42735"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687609_539_1316687610_743_ser208_7072.dat",new StringBuffer("34737339"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687599_966_1316687601_198_ser208_7072.dat",new StringBuffer("34592817"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687630_959_1316687632_202_ser208_7072.dat",new StringBuffer("85470"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687619_647_1316687620_985_ser208_7072.dat",new StringBuffer("56721"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687639_425_1316687640_581_ser208_7072.dat",new StringBuffer("66045"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687650_980_1316687652_187_ser208_7072.dat",new StringBuffer("94794"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687660_829_1316687662_16_ser208_7072.dat",new StringBuffer("239316"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687670_543_1316687671_880_ser208_7072.dat",new StringBuffer("104118"));
		
		result.put("/ffCDR22/bssap/20110922/18/1316687690_285_1316687691_455_ser208_7072.dat",new StringBuffer("89355"));
		System.out.println("+++++searchCDR2+++++");
	}
	public static void main(){
		
		DoINDEX1_20110927 did1 = new DoINDEX1_20110927();
		try {
			did1.searchCDR(null,"/usr/local/hadoop-0.20.1-dev-patch/blk_8284729323740687044");
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
