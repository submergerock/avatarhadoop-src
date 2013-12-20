package org.apache.hadoop.hdfs.job;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;


public class Job implements Writable{
    private QueryConditions queryConditions;
	private LongWritable jobId;
	private Text jobName;
	private Text date;
	private Configuration conf;
	//private enum JobType{NNDIR,INDEX2,INDEX1,SEARCH}
	//private JobType jobType;

	public Job(){		
		this(new Configuration());
	}
	public Job(Configuration conf){
		this(new LongWritable((long)((Math.random()*10000000)%100000)), 
				 new Text(""), 
				 new Text((new Date()).toString()), 
				 conf);
	}
	
	
    public Job(LongWritable jobId,Text jobName,Text date,Configuration conf){
		this.jobId = jobId;
		this.jobName = jobName;
		this.date = date;
		this.conf = conf;
		this.queryConditions = null;
	}
	
    public QueryConditions getQueryConditions() {
		return this.queryConditions;
	}

	public void setQueryConditions(QueryConditions queryConditions) {
		this.queryConditions = queryConditions;
	}
	
	public LongWritable getJobId() {
		return jobId;
	}

	public void setJobId(LongWritable jobId) {
		this.jobId = jobId;
	}

	public Text getJobName() {
		return jobName;
	}

	public void setJobName(Text jobName) {
		this.jobName = jobName;
	}

	public Text getDate() {
		return date;
	}

	public void setDate(Text date) {
		this.date = date;
	}

	public Configuration getConf() {
		return conf;
	}

	public void setConf(Configuration conf) {
		this.conf = conf;
	}

	//public JobType getJobType() {
	//	return jobType;
	//}

	//public void setType(JobType type) {
	//	this.jobType = jobType;
	//}

	@Override
	public void write(DataOutput out) throws IOException {
		this.conf.write(out);		
		this.date.write(out);
		this.jobId.write(out);
		this.jobName.write(out);
		//this.queryConditions.write(out);
		//out.writeInt(this.jobType.ordinal());		
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		this.conf.readFields(in);
		this.date.readFields(in);
		this.jobId.readFields(in);
		this.jobName.readFields(in);
		//this.queryConditions.readFields(in);
		/*
		switch(in.readInt()){
		case 0 : this.jobType = JobType.NNDIR;
		case 1 : this.jobType = JobType.INDEX2;
		case 2 : this.jobType = JobType.INDEX1;
		case 3 : this.jobType = JobType.SEARCH;
		}*/		
	}
	
	public static class QueryConditions implements org.apache.hadoop.io.Writable
	{
		public long[] startTime = null;
		public long[] endTime = null;
		public long[] opc = null;
		public long[] dpc = null;
		public int tableType = 0;
		public String cdrType  = "";
		
		public int callType  = 0;
		public String phoneNum = "";
		public int wangYuanType  = 0;
		public String wangYuan  = "";
		
		public long[] getOpc() {
			return opc;
		}

		public void setOpc(long[] opc) {
			this.opc = opc;
		}

		public long[] getDpc() {
			return dpc;
		}

		public void setDpc(long[] dpc) {
			this.dpc = dpc;
		}

		public QueryConditions(long[] startTime,long[] endTime,int tableType,int callType,long[] opc,long[] dpc,String phoneNum)
		{
			this.startTime=startTime;
			this.endTime=endTime;
			this.tableType=tableType;
			this.callType=callType;
			this.opc=opc;
			this.dpc=dpc;
			this.phoneNum=phoneNum;
		}
		
		public QueryConditions(long[] startTime,long[] endTime,int tableType,String cdrType,int callType,String phoneNum,int wangYuanType,String wangYuan)
		{
			this.setStartTime(startTime);
			this.setEndTime(endTime);
			this.setTableType(tableType);
			this.setCdrType(cdrType);
			this.setCallType(callType);
			this.setPhoneNum(phoneNum);
			this.setWangYuanType(wangYuanType);
			this.setWangYuan(wangYuan);
		}
		
		public QueryConditions()
		{
			
		}
		
		public String  getPhoneNum()
		{
			return phoneNum;
		}
		public int getWangYuanType()
		{
			return wangYuanType;
		}
		
		public String getWangYuan()
		{
			return wangYuan;
		}
		
		public long[] getStartTime()
		{
			return startTime;
		}
		public long[] getEndTime()
		{
			return endTime;
		}
		public int getTableType(){
			return tableType;
		}
		public int getCallType()
		{
			return callType;
		}
		public String getCdrType()
		{
			return cdrType;
		}
		
		public void setPhoneNum(String  phoneNum)
		{
			this.phoneNum=String.valueOf(phoneNum);
		}
	
		public void setWangYuanType(int wangYuanType)
		{
			this.wangYuanType=wangYuanType;
		}
		
		public void setWangYuan(String wangYuan)
		{
			this.wangYuan=wangYuan;
		}
		
		public void setStartTime(long[] startTime)
		{
			this.startTime=startTime;
		}
		
		public void setEndTime(long[] endTime)
		{
			this.endTime=endTime;
		}
		
		public void setTableType(int tableType)
		{
			this.tableType=tableType;
		}
		public void setCallType(int callType)
		{
			this.callType=callType;
		}
		public void setCdrType(String cdrType)
		{
			this.cdrType=cdrType;
		}
		
		public  void write(java.io.DataOutput out) throws java.io.IOException
		{
			
			if(startTime != null){
				out.writeInt(startTime.length);
				for(int i = 0 ; i < startTime.length ; i++)
				{
					out.writeLong(startTime[i]);
				}
			} else {
				out.writeInt(0);
			}
			
			if(endTime != null){
				out.writeInt(endTime.length);
				for(int i = 0 ; i < endTime.length ; i++)
				{
					out.writeLong(endTime[i]);
				}
			} else {
				out.writeInt(0);
			}
			
			if(opc != null){
				out.writeInt(opc.length);
				for(int i = 0 ; i < opc.length ; i++)
				{
					out.writeLong(opc[i]);
				}
			} else {
				out.writeInt(0);
			}
			
			if(dpc != null){
				out.writeInt(dpc.length);
				for(int i = 0 ; i < dpc.length ; i++)
				{
					out.writeLong(dpc[i]);
				}
			} else {
				out.writeInt(0);
			}
			
			out.writeInt(tableType);
			out.writeInt(callType);
			out.writeInt(wangYuanType);
			
			Text.writeString(out,cdrType);
			Text.writeString(out,phoneNum);
			Text.writeString(out,wangYuan);
			
			
			
		}
		  
		  // Method descriptor #8 (Ljava/io/DataInput;)V
		  public  void readFields(java.io.DataInput in) throws java.io.IOException
		  {			
			  int number = in.readInt();
			  if(number > 0){
				  startTime = new long[number];
				  for(int i = 0 ; i < number ; i++)
				  {
					  startTime[i] = in.readLong();
				  }
			  }
			  
			   number = in.readInt();
			   if(number > 0){
				  endTime = new long[number];
				  for(int i = 0 ; i < number ; i++)
				  {
					  endTime[i] = in.readLong();
				  }
			   }
			  
			  number = in.readInt();
			  if(number > 0){
				  opc = new long[number];
				  for(int i = 0 ; i < number ; i++)
				  {
					  opc[i] = in.readLong();
				  }
			  }			  
			  
			  number = in.readInt();
			  if(number > 0){
				  dpc = new long[number];
				  for(int i = 0 ; i < number ; i++)
				  {
					  dpc[i] = in.readLong();
				  } 
			  }
			  
			  tableType = in.readInt();
			  callType = in.readInt();
			  wangYuanType = in.readInt();

			  cdrType = Text.readString(in);
			  phoneNum = Text.readString(in);
			  wangYuan = Text.readString(in);			  
		  }
	}

}