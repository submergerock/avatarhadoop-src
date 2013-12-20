package org.apache.hadoop.hdfs.job;
import org.apache.hadoop.hdfs.job.Job.QueryConditions;

import net.sf.json.JSONArray;

import net.sf.json.JSONObject;


public class SearchCDRTools {

	public static class biccCdrInfo
	{
		public long startTime;
		public long endTime;
		public long callingNum;
		public String filePath;
		public long rawOffset;
		public long opc;
		public long dpc;
		public int callType;
		
		public int getCallType() {
			return callType;
		}

		public void setCallType(String callType) {
			if(callType.equals("z"))
				this.setCallType(0);
			else if(callType.equals("b"))
				this.setCallType(1);
			else
				this.setCallType(Integer.valueOf(callType));
		}
		
		public void setCallType(int callType)
		{
			this.callType=callType;
		}

		public biccCdrInfo()
		{
			
		}
		
		public biccCdrInfo(long startTime,long endTime,long callingNum,long opc,long dpc,String  recordPath,String callType)
		{
			this.startTime=startTime;
			this.endTime=endTime;
			this.callingNum=callingNum;
			this.opc=opc;
			this.dpc=dpc;
			String[] strs=recordPath.split("%");
			this.filePath=strs[0];
			this.setRawOffset(Long.valueOf(strs[1]));
			if(callType.equals("z"))
				this.setCallType(0);
			else if(callType.equals("b"))
				this.setCallType(1);
			else
				this.setCallType(Integer.valueOf(callType));
		}
		
		public long getStartTime() {
			return startTime;
		}
		public void setStartTime(long startTime) {
			this.startTime = startTime;
		}
		public long getEndTime() {
			return endTime;
		}
		public void setEndTime(long endTime) {
			this.endTime = endTime;
		}
		public long getCallingNum() {
			return callingNum;
		}
		public void setCallingNum(long callingNum) {
			this.callingNum = callingNum;
		}
		public String getFilePath() {
			return filePath;
		}
		public void setFilePath(String recordPath) {
			String[] strs=recordPath.split("%");
			// System.out.println(recordPath+"******");
//System.out.println(strs[0]);
//System.out.println(strs[1]);
			this.filePath=strs[0];
			this.setRawOffset(Long.valueOf(strs[1]));
		}
		
		public long getRawOffset() {
			return rawOffset;
		}

		public void setRawOffset(long rawOffset) {
			this.rawOffset = rawOffset;
		}

		public long getOpc() {
			return opc;
		}
		public void setOpc(long opc) {
			this.opc = opc;
		}
		public long getDpc() {
			return dpc;
		}
		public void setDpc(long dpc) {
			this.dpc = dpc;
		}
		
	}
	
	public static class cdrInfo
	{
		// info=cdrType+","+"b,"+startTime+","+endTime+","+wangYuan+","+recordPath;
		public int cdrType;
		public int callType;
		public long startTime;
		public long endTime;
		public String wangYuan;
		public String filePath;
		public long rawOffset;
		public long phoneNum;
		
		public void setPhoneNum(String phoneNum)
		{
			this.phoneNum=Long.valueOf(phoneNum);
		}
		public void setPhoneNum(long phoneNum)
		{
			this.phoneNum=phoneNum;
		}
		public long getPhoneNum()
		{
			return this.phoneNum;
		}
		
		public cdrInfo(String cdrType,String callType,String startTime,String endTime,String wangYuan,String recordPath)
		{
			
			this.setCdrType(Integer.valueOf(cdrType));
			
			if(callType=="z")
				this.setCallType(0);
			else if(callType=="b")
				this.setCallType(1);
			else
				this.setCallType(Integer.valueOf(callType));
			this.setStartTime(Long.valueOf(startTime));
			this.setEndTime(Long.valueOf(endTime));
			this.setWangYuan(wangYuan);
			String[] strs=recordPath.split("%");
			this.setFilePath(strs[0]);
			this.setRawOffset(Long.valueOf(strs[1]));
		}
		
		public cdrInfo()
		{
			
		}
		
		public void setCdrType(String cdrType)
		{
			 this.cdrType=Integer.valueOf(cdrType);
		}
		
		public void setCallType(String callType)
		{
			if(callType.equals("z"))
				this.setCallType(0);
			else if(callType.equals("b"))
				this.setCallType(1);
			else
				this.setCallType(Integer.valueOf(callType));
		}
		
		public void setStartTime(String startTime)
		{
			this.startTime=Long.valueOf(startTime);
		}
		
		public void SetEndTime(String endTime)
		{
			this.endTime=Long.valueOf(endTime);
		}
		
		public void setFilePathRawOffset(String recordPath)
		{
			String[] strs=recordPath.split("%");
			// //System.out.println(recordPath+"******");
////System.out.println(strs[0]);
////System.out.println(strs[1]);
			this.setFilePath(strs[0]);
			this.setRawOffset(Long.valueOf(strs[1]));
		}
		
		
		public void setCdrType(int cdrType)
		{
			 this.cdrType=cdrType;
		}
		
		public void setCallType(int callType)
		{
			this.callType=callType;
		}
		
		public void setStartTime(long startTime)
		{
			this.startTime=startTime;
		}
		
		public void setEndTime(long endTime)
		{
			this.endTime=endTime;
		}
		
		public void setWangYuan(String wangYuan)
		{
			this.wangYuan=wangYuan;
		}
		
		public void setFilePath(String filePath)
		{
			this.filePath=filePath;
		}
		
		public void setRawOffset(long rawOffset)
		{
			this.rawOffset=rawOffset;
		}
		
		public int getCdrType()
		{
			return cdrType;
		}
		
		public int getCallType()
		{
			return callType;
		}
		
		public long getStartTime()
		{
			return startTime;
		}
		
		public long getEndTime()
		{
			return endTime;
		}
		
		public String getWangYuan()
		{
			return wangYuan;
		}
		
		public String getFilePath()
		{
			return filePath;
		}
		
		public long getRawOffset()
		{
			return rawOffset;
		}
		
	}

	public static boolean checkBiccByQueryCondition(biccCdrInfo biccCdr,queryConditions query)
	{
		if(!checkCallType(biccCdr.callType,query.callType))
			return false;
		if(!checkStartTime(biccCdr.startTime,query.getStartTime(),query.getEndTime()))
			return false;
		if(!checkJuxiang(biccCdr.getDpc(),biccCdr.getOpc(),query.getDpc(),query.getOpc()))
			return false;
		return true;
	}
	public static boolean checkByQueryCondition(cdrInfo cdr,queryConditions query)
	{
		boolean result=true;
		//System.out.println("result1 : " + result);
		if(!checkCallType(cdr.getCallType(),query.getCallType()))
			result=false;
		//System.out.println("result2 : " + result);
		if(!checkCdrType(cdr.getCdrType(),query.getCdrType()))
			result=false;
		//System.out.println("result3 : " + result);
		if(!checkStartTime(cdr.getStartTime(),query.getStartTime(),query.getEndTime()))
			result=false;
		//System.out.println("result4 : " + result);
		if(!checkWangyuan(cdr.getWangYuan(),query.getWangYuanType(),query.getWangYuan()))
			result=false;
		//System.out.println("result5 : " + result);
		return result;
	}
	public static boolean checkCallType(int cdrCallType,int queryCallType)
	{
		if(cdrCallType==0)
		{
			int i=0;
		}
		boolean result=false;
		if(queryCallType==2)
			result=true;
		if(cdrCallType==queryCallType)
			result=true;
		return result;
	}
	public static boolean checkStartTime(long cdrStartTime,long[] startTime,long[] endTime)
	{
		boolean result=false;
//SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd
//HH:mm:ss");
//Date date = new Date();
//date.setTime((Long.valueOf(cdrStartTime)*1000L));
//System.out.println("cdr Time"+simpleDateFormat.format(date));
		if(startTime.length!=endTime.length)
		{
			result=false;
			// System.out.println("query conditons'time pair wrong!");
		}
		for(int i=0;i<startTime.length;i++)
		{
			if(cdrStartTime>=startTime[i]&&cdrStartTime<endTime[i])
				{
//date.setTime((Long.valueOf(startTime[i])*1000L));
//System.out.println("Start Time"+simpleDateFormat.format(date));
//date.setTime((Long.valueOf(endTime[i])*1000L));
//System.out.println("End Time"+simpleDateFormat.format(date));
					result=true;
					break;
				}
		}
		return result;
	}
	public static boolean checkJuxiang(long dpc,long opc,long[] dpcs,long[] opcs)
	{
		boolean flag=false;
		if(dpcs==null||opcs==null)
			return true;
		if(dpcs.length==opcs.length)
		{
			for(int i=0;i<dpcs.length;i++)
			{
				if(dpc==dpcs[i]&&opc==opcs[i])
				{
					flag=true;
					break;
				}
			}
		}
		return flag;
	}
	public static boolean checkWangyuan(String cdrWangyuan,int wangYuanType,String wangYuanInfo)
	{
		if(wangYuanInfo==null || wangYuanInfo.equals("null") || wangYuanInfo.equals(""))
			return true;
			
		boolean result=false;
		String[] strs=cdrWangyuan.split("%");
		String[] wangYuan=wangYuanInfo.split(",");
		// System.out.println();
		switch(wangYuanType)
		{
			case 0:
				for(int i=0;i<wangYuan.length;i++)
					if(strs[0].equals(wangYuan[i]))
						result=true;
				break;
			case 1:
				for(int i=0;i<wangYuan.length;i++)
					if(strs[1].equals(wangYuan[i]))
						result=true;
				break;
			case 2:
				for(int i=0;i<wangYuan.length;i++)
					if(strs[2].equals(wangYuan[i]))
						result=true;
				break;
			default:
				break;
		}
		return result;
	}
	public static boolean checkCdrType(int cdrType,String queryCdrType)
	{
		boolean result=false;
		String[] strs=queryCdrType.split(",");
		for(int i=0;i<strs.length;i++)
			if(Integer.valueOf(strs[i])==cdrType)
			{
				result=true;
				break;
			}
		return result;
	}

	public static class queryConditions

	{

		public long[] startTime;

		public long[] endTime;

		public long[] opc;

		public long[] dpc;

		public int tableType;

		public String cdrType;

		

		public int callType;

		public String phoneNum;

		public int wangYuanType;

		public String wangYuan;

		

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



		public queryConditions(long[] startTime,long[] endTime,int tableType,int callType,long[] opc,long[] dpc,String phoneNum)

		{

			this.startTime=startTime;

			this.endTime=endTime;

			this.tableType=tableType;

			this.callType=callType;

			this.opc=opc;

			this.dpc=dpc;

			this.phoneNum=phoneNum;

		}

		

		public queryConditions(long[] startTime,long[] endTime,int tableType,String cdrType,int callType,String phoneNum,int wangYuanType,String wangYuan)

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

		

		public queryConditions()

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

	}
	
	public static class CDRBeanForIndex {

		

		public String calling_number;//主叫

		public String called_number;//被叫

		public String cdr_type;//cdr类型

		public long[] start_time_s;//开始时间，

		public long[] end_time_s;//结束时间

		public String opc_dpc;//局向设置

		public int netElem;//网元类型

		public String netElemId;//网元ID

		public String tableName="";//表名

		public int tableType=0;//表类型

//		public int OnceCount;//要查的记录数

//		public int BeginNum;//从哪条记录数开始查

		public String companyId;//商家

		public long[] opc;

		public long[] dpc;

		public String IndexLog;

		public int IndexCount;

		public String sessionID;

		public String serverURL;

		public String hdfsPath;

		public String sendedFile;

		public String lastFile;

		

		//定义各个字段类型，一定要对应

		public int CallType;

		public int CDRCallType;//呼叫类型

		public String CallTypeNum;//主、被

		public String Info;//用户信息

		public JSONArray ProtocolType;//协议类别

		public JSONArray CdrType;//业务类别

		public String caq;

		public JSONArray netElemIdList;//网元ID

		public JSONArray tacElemList;

		public JSONArray pairElemList;//局向设置

		public JSONArray pairIpList;

		public JSONArray timeRangeList;

		public int timeRank;//枚举？？

		public int staticDim;//枚举？？

		public JSONArray IMEIList;

		public String selectedKPI;

		public JSONArray kpiList;

		public JSONArray m_Provinces;

		public JSONArray m_Cities;

		public JSONArray m_MSCs;

		public JSONArray surSpcList;

		public JSONArray destSpcList;

		public int m_sumType;

		public String ShowType;

		public String OtherFilter;

		public boolean ISGSM;

		public boolean ISTD;

		public JSONObject timeRange;

		

		public CDRBeanForIndex(JSONObject object) {

			//接收到的json对象进行赋值

//			this.CallType = object.getInt("CallType");

			this.CDRCallType = object.getInt("CDRCallType");

			this.CallTypeNum = object.getString("CallTypeNum");

			this.CdrType = object.getJSONArray("CdrType");

			this.Info = object.getString("Info");

			this.ProtocolType= object.getJSONArray("ProtocolType");

			String tableNameTemp = object.getJSONArray("ProtocolType").toString();

			if(tableNameTemp!=null &&!"".equals(tableNameTemp))

				this.tableName = tableNameTemp.replace("[", "").replace("]", "");

			this.caq = object.getString("caq");

			

			this.netElem = object.getInt("netElem");

			

			this.netElemIdList = object.getJSONArray("netElemIdList");

			this.tacElemList = object.getJSONArray("tacElemList");

			this.pairElemList = object.getJSONArray("pairElemList");

			this.pairIpList = object.getJSONArray("pairIpList");

			this.timeRangeList = object.getJSONArray("timeRangeList");

			this.timeRank = object.getInt("timeRank");

			this.staticDim = object.getInt("staticDim");

			this.IMEIList = object.getJSONArray("IMEIList");

			this.selectedKPI = object.getString("selectedKPI");

			

			

			 

			this.kpiList = object.getJSONArray("kpiList");

			this.m_Provinces = object.getJSONArray("m_Provinces");

			this.m_Cities = object.getJSONArray("m_Cities");

			this.m_MSCs = object.getJSONArray("m_MSCs");

			this.CallType = object.getInt("CallType");

			this.surSpcList = object.getJSONArray("surSpcList");

			this.destSpcList = object.getJSONArray("destSpcList");

			this.m_sumType = object.getInt("m_sumType");

			this.ShowType = object.getString("ShowType");

			this.OtherFilter = object.getString("OtherFilter");

			this.ISGSM = object.getBoolean("ISGSM");

			this.ISTD = object.getBoolean("ISTD");

			this.timeRange = object.getJSONObject("timeRange");

			

			this.IndexLog = object.getString("IndexLog");

			this.IndexCount = object.getInt("IndexCount");

			

//			this.OnceCount = object.getInt("OnceCount");

//			this.BeginNum = object.getInt("BeginNum");

		}

		

		public String getLastFile() {

			return lastFile;

		}



		public void setLastFile(String lastFile) {

			this.lastFile = lastFile;

		}



		public String getSessionID() {

			return sessionID;

		}



		public void setSessionID(String sessionID) {

			this.sessionID = sessionID;

		}



		public String getServerURL() {

			return serverURL;

		}



		public void setServerURL(String serverURL) {

			this.serverURL = serverURL;

		}



		public String getHdfsPath() {

			return hdfsPath;

		}



		public void setHdfsPath(String hdfsPath) {

			this.hdfsPath = hdfsPath;

		}



		public int getIndexCount() {

			return IndexCount;

		}



		public void setIndexCount(int indexCount) {

			IndexCount = indexCount;

		}



		public String getIndexLog() {

			return IndexLog;

		}



		public void setIndexLog(String indexLog) {

			IndexLog = indexLog;

		}



		public long[] getOpc() {

			if(pairElemList!=null && pairElemList.size()>0){

				opc = new long[pairElemList.size()];

				for(int i=0;i<pairElemList.size();i++){

					JSONObject object = pairElemList.getJSONObject(i);

					opc[i]=object.getLong("id1");

				}

			}

			return opc;

		}



		public void setOpc(long[] opc) {

			this.opc = opc;

		}



		public long[] getDpc() {

			if(pairElemList!=null && pairElemList.size()>0){

				dpc = new long[pairElemList.size()];

				for(int i=0;i<pairElemList.size();i++){

					JSONObject object = pairElemList.getJSONObject(i);

					dpc[i]=object.getLong("id2");

				}

			}

			return dpc;

		}



		public void setDpc(long[] dpc) {

			this.dpc = dpc;

		}



		public long[] getStart_time_s() {

			if (timeRangeList != null && timeRangeList.size() > 0) {

				start_time_s = new long[timeRangeList.size()];

				for (int j = 0; j < timeRangeList.size(); j++) {

					JSONObject object = timeRangeList.getJSONObject(j);

					start_time_s[j]=object.getLong("nBeginTime");

				}

			} else if (timeRange != null ) {

				start_time_s = new long[1];

				start_time_s[0]=timeRange.getLong("nBeginTime");

			}

			return start_time_s;

		}



		public long[] getEnd_time_s() {

			if (timeRangeList != null && timeRangeList.size() > 0) {

				end_time_s = new long[timeRangeList.size()];

				for (int j = 0; j < timeRangeList.size(); j++) {

					JSONObject object = timeRangeList.getJSONObject(j);

					end_time_s[j]=object.getLong("nEndTime");

				}

			} else if (timeRange != null) {

				end_time_s = new long[1];

				end_time_s[0]=timeRange.getLong("nEndTime");

			}

			return end_time_s;

		}



		public void setEnd_time_s(long[] end_time_s) {

			this.end_time_s = end_time_s;

		}



		public void setStart_time_s(long[] start_time_s) {

			this.start_time_s = start_time_s;

		}



		public String getCdr_type() {

			//业务类别

			if(CdrType!=null && CdrType.size()>0){

				cdr_type = CdrType.toString().replace("[", "").replace("]", "");

			}

			return cdr_type;

		}



		public void setCdr_type(String cdr_type) {

			this.cdr_type = cdr_type;

		}



		public String getCalling_number() {

			//主被叫

			if(CDRCallType==0){

				calling_number=CallTypeNum+"";

			}else if(CDRCallType==1){

			}else if(CDRCallType==2){

				calling_number=CallTypeNum+"";

			}

			return calling_number;

		}



		public void setCalling_number(String calling_number) {

			this.calling_number = calling_number;

		}



		public String getCalled_number() {

			//主被叫

			if(CDRCallType==0){

			}else if(CDRCallType==1){

				called_number=CallTypeNum+"";

			}else if(CDRCallType==2){

				called_number=CallTypeNum+"";

			}

			return called_number;

		}



		public void setCalled_number(String called_number) {

			this.called_number = called_number;

		}



//		public int getOnceCount() {

//			return OnceCount;

//		}

//		public void setOnceCount(int onceCount) {

//			OnceCount = onceCount;

//		}

//		public int getBeginNum() {

//			return BeginNum;

//		}

//		public void setBeginNum(int beginNum) {

//			BeginNum = beginNum;

//		}

		public int getCallType() {

			return CallType;

		}

		public void setCallType(int callType) {

			CallType = callType;

		}

		public int getCDRCallType() {

			return CDRCallType;

		}

		public void setCDRCallType(int callType) {

			CDRCallType = callType;

		}

		public String getCallTypeNum() {

			return CallTypeNum;

		}

		public void setCallTypeNum(String callTypeNum) {

			CallTypeNum = callTypeNum;

		}

		public String getInfo() {

			return Info;

		}

		public void setInfo(String info) {

			Info = info;

		}

		public JSONArray getProtocolType() {

			return ProtocolType;

		}

		public void setProtocolType(JSONArray protocolType) {

			ProtocolType = protocolType;

		}

		public JSONArray getCdrType() {

			return CdrType;

		}

		public void setCdrType(JSONArray cdrType) {

			CdrType = cdrType;

		}

		public String getCaq() {

			return caq;

		}

		public void setCaq(String caq) {

			this.caq = caq;

		}

		public int getNetElem() {

			return netElem;

		}

		public void setNetElem(int netElem) {

			this.netElem = netElem;

		}

		public JSONArray getNetElemIdList() {

			return netElemIdList;

		}

		public void setNetElemIdList(JSONArray netElemIdList) {

			this.netElemIdList = netElemIdList;

		}

		public String getTacElemList() {

			String tacElemListStr="";

			//局向设置

			if(tacElemList!=null && tacElemList.size()>0){

				tacElemListStr=tacElemList.toString().replace("[", "").replace("]", "");

			}

			return tacElemListStr;

		}

		public void setTacElemList(JSONArray tacElemList) {

			this.tacElemList = tacElemList;

		}

		public JSONArray getPairElemList() {

			return pairElemList;

		}

		public void setPairElemList(JSONArray pairElemList) {

			this.pairElemList = pairElemList;

		}

		public JSONArray getPairIpList() {

			return pairIpList;

		}

		public void setPairIpList(JSONArray pairIpList) {

			this.pairIpList = pairIpList;

		}

		public JSONArray getTimeRangeList() {

			return timeRangeList;

		}

		public void setTimeRangeList(JSONArray timeRangeList) {

			this.timeRangeList = timeRangeList;

		}

		public int getTimeRank() {

			return timeRank;

		}

		public void setTimeRank(int timeRank) {

			this.timeRank = timeRank;

		}

		public int getStaticDim() {

			return staticDim;

		}

		public void setStaticDim(int staticDim) {

			this.staticDim = staticDim;

		}

		public JSONArray getIMEIList() {

			return IMEIList;

		}

		public void setIMEIList(JSONArray list) {

			IMEIList = list;

		}

		public String getSelectedKPI() {

			return selectedKPI;

		}

		public void setSelectedKPI(String selectedKPI) {

			this.selectedKPI = selectedKPI;

		}

		public JSONArray getKpiList() {

			return kpiList;

		}

		public void setKpiList(JSONArray kpiList) {

			this.kpiList = kpiList;

		}

		public JSONArray getM_Provinces() {

			return m_Provinces;

		}

		public void setM_Provinces(JSONArray provinces) {

			m_Provinces = provinces;

		}

		public JSONArray getM_Cities() {

			return m_Cities;

		}

		public void setM_Cities(JSONArray cities) {

			m_Cities = cities;

		}

		public JSONArray getM_MSCs() {

			return m_MSCs;

		}

		public void setM_MSCs(JSONArray cs) {

			m_MSCs = cs;

		}

		public JSONArray getSurSpcList() {

			return surSpcList;

		}

		public void setSurSpcList(JSONArray surSpcList) {

			this.surSpcList = surSpcList;

		}

		public JSONArray getDestSpcList() {

			return destSpcList;

		}

		public void setDestSpcList(JSONArray destSpcList) {

			this.destSpcList = destSpcList;

		}

		public int getM_sumType() {

			return m_sumType;

		}

		public void setM_sumType(int type) {

			m_sumType = type;

		}

		public String getShowType() {

			return ShowType;

		}

		public void setShowType(String showType) {

			ShowType = showType;

		}

		public String getOtherFilter() {

			return OtherFilter;

		}

		public void setOtherFilter(String otherFilter) {

			OtherFilter = otherFilter;

		}

		public boolean isISGSM() {

			return ISGSM;

		}

		public void setISGSM(boolean isgsm) {

			ISGSM = isgsm;

		}

		public boolean isISTD() {

			return ISTD;

		}

		public void setISTD(boolean istd) {

			ISTD = istd;

		}

		public JSONObject getTimeRange() {

			return timeRange;

		}

		public void setTimeRange(JSONObject timeRange) {

			this.timeRange = timeRange;

		}

		public String getTableName() {

			//局向设置

			if(ProtocolType!=null && ProtocolType.size()>0){

				tableName=ProtocolType.get(0).toString();

			}

			return tableName;

		}

		public void setTableName(String tableName) {

			this.tableName = tableName;

		}



		public String getOpc_dpc() {

			//局向设置

			if(pairElemList!=null && pairElemList.size()>0){

				opc_dpc=pairElemList.toString().replace("[", "").replace("]", "");

			}

			return opc_dpc;

		}



		public void setOpc_dpc(String opc_dpc) {

			this.opc_dpc = opc_dpc;

		}



		public String getNetElemId() {

			//网元设置

			if(netElemIdList!=null && netElemIdList.size()>0){

				netElemId = netElemIdList.toString().replace("[", "").replace("]", "");

			}

			return netElemId;

		}



		public void setNetElemId(String netElemId) {

			this.netElemId = netElemId;

		}

		

		public int getTableType() {

			if (ProtocolType != null && ProtocolType.size() > 0) {

				tableName = ProtocolType.get(0).toString();

			}

			if ("BSSAP".equals(tableName))

				tableType = 0;

			else if ("RANAP".equals(tableName))

				tableType = 1;

			else if ("BICC".equals(tableName))

				tableType = 2;

			return tableType;

		}



		public String getCompanyId() {

			if (tacElemList  != null && tacElemList .size() > 0) {

				companyId = tacElemList .toString().replace("[", "").replace("]",

						"");

			}

			return companyId;

		}



		public void setCompanyId(String companyId) {

			this.companyId = companyId;

		}



		public void setTableType(int tableType) {

			this.tableType = tableType;

		}


/*
		public static void main(String[] args){

//			String para = "{"+

//			"CallType:0,"+

//			"CDRCallType:0,"+

//			"CallTypeNum:'10086',"+

//			"Info:'1',IndexLog:'xxxxx',"+

//			"ProtocolType:['BSSAP'],"+

//			"CdrType:[1,2,3,4],"+

//			"caq:null,netElem:1,netElemIdList:[123,456,778],tacElemList:[],pairElemList:[{'id1':11,'id2':22},{'id1':33,'id2':44}],pairIpList:[],"+

//			"timeRangeList:[{'nBeginTime':1301587200,'nEndTime':1304092800},{nBeginTime:1309622400,nEndTime:1309968000}],timeRank:0,staticDim:0,IMEIList:[]," +

//			"selectedKPI:null,kpiList:[],m_Provinces:[],m_Cities:[],m_MSCs:[],surSpcList:[],destSpcList:[],m_sumType:0,"+

//			"ShowType:null,OtherFilter:null,ISGSM:false,ISTD:false,"+

//			"timeRange:{}"+

//			"}";

//	        //2.定义表信息(通过字段获取之)，将json字段信息解析出来

//	    	CDRBeanForIndex cdr=new CDRBeanForIndex(JSONObject.fromObject(para));

//	    	System.out.println("主叫:"+cdr.getCalling_number());

//	    	System.out.println("被叫:"+cdr.getCalled_number());

//	    	System.out.println("cdr_type:"+cdr.getCdr_type());

//	    	System.out.println("网元类型:"+cdr.getNetElem());

//	    	System.out.println("网元设置数据:"+cdr.getNetElemId());

//	    	System.out.println("局向设置数据:"+cdr.getOpc_dpc());

//	    	long[] list = cdr.getStart_time_s();

//	    	long[] list1 = cdr.getEnd_time_s();

//			for (int i = 0; i < list.length; i++) {

//				System.out.println("开始时间:" + list[i]);

//			}

//			for (int i = 0; i < list1.length; i++) {

//				System.out.println("结束时间:" + list1[i]);

//			}

//			long[] opc = cdr.getOpc();

//	    	long[] dpc = cdr.getDpc();

//			for (int i = 0; i < opc.length; i++) {

//				System.out.println("opc:" + opc[i]);

//			}

//			for (int i = 0; i < dpc.length; i++) {

//				System.out.println("dpc:" + dpc[i]);

//			}

////	    	System.out.println("开始数目:"+cdr.getBeginNum());

////	    	System.out.println("查询数目:"+cdr.getOnceCount());

//	    	System.out.println("表名:"+cdr.getTableName());

//	    	System.out.println("表类型:"+cdr.getTableType());

//	    	System.out.println("查询log:"+cdr.getIndexLog());

	    	

	    	String ab = "11111";

	    	System.out.println(ab.getBytes().length);

		}
*/


		public String getSendedFile() {

			return sendedFile;

		}



		public void setSendedFile(String sendedFile) {

			this.sendedFile = sendedFile;

		}

	}


}
