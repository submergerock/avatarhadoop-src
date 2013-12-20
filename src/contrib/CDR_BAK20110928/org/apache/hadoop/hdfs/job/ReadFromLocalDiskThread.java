package org.apache.hadoop.hdfs.job;

import org.apache.hadoop.hdfs.job.SearchCDRTools.biccCdrInfo;
import org.apache.hadoop.hdfs.job.SearchCDRTools.cdrInfo;
import org.apache.hadoop.hdfs.job.SearchCDRTools.queryConditions;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;



public class ReadFromLocalDiskThread extends ReadFromLocalDiskThreadBase<Index> {

	private int dataSize = 0;
	private String startPath = "";
	private String fileName = "";
	private queryConditions query;

	public static ArrayList<ReadFromLocalDiskThread> InitReadThread(
			String conPath, Integer condition) {
		ArrayList<ReadFromLocalDiskThread> list = new ArrayList<ReadFromLocalDiskThread>();
		Configuration conf = new Configuration();
		conf.addResource(new Path(conPath));
		String disks = conf.get("dfs.data.dir");
		if (disks.length() == 0) {
			return null;
		}
		String[] offsets = disks.split(",");
		for (int i = 0; i < offsets.length; i++) {
			ReadFromLocalDiskThread thread = new ReadFromLocalDiskThread(
					condition);
			thread.setDiskPath(offsets[i].trim());
			list.add(thread);
		}
		return list;
	}

	public void setDataSize(int dataSize) {
		this.dataSize = dataSize;
	}

	public void setDiskPath(String startPath) {
		this.startPath = startPath;
	}

	public ReadFromLocalDiskThread(Integer condition) {
		super(condition);
	}

	public void addReadIndex(String fileName, queryConditions query) {
		if (fileName.startsWith(startPath)) {
			this.fileName = fileName;
			this.query = query;
			addReadIndex();
		}
	}

	public Index currentReadPos() {
		return new Index(fileName, query);
	}

	// 搜索索引
	public void readData(Index data) {
		HashMap<String,StringBuffer> result=new HashMap<String,StringBuffer>();
		try {
			File f = new File(data.getFileName());
			System.out.println("-----readData-----");
			cdrInfo cdr = new cdrInfo();
			File indexFile = new File(data.getFileName());
			if (!indexFile.exists())
				return;
			String info = "";
			long slen = 8;
			byte[] buffer;
			RandomAccessFile inFirst = new RandomAccessFile(indexFile, "r");
			inFirst.seek(4);
			slen = inFirst.readLong();
			int sl = (int) slen / 16;
			long sep = -1;
			long start = 0;
			long end = sl;
			long pn = Long.parseLong(data.getQueryConditions().getPhoneNum());
			while (start <= end) {
				long middleIndex = (start + end) / 2;
				inFirst.seek(4 + 8 + (middleIndex * 16));
				long data1 = inFirst.readLong();
				if (pn == data1) {
					sep = inFirst.readLong();
					break;
				}
				if (pn >= data1) {
					start = middleIndex + 1;
				} else {
					end = middleIndex - 1;
				}
			}
			if (sep != -1) {
				inFirst.seek(sep - 8);
				info = inFirst.readLine();
			} else {
				inFirst.close();
				return;
			}
			inFirst.close();
			String[] infos = info.split(";");
			String[] strs;
			String[] recordPathOffset;
			if (query.tableType != 2) {
				for (int j = 0; j < infos.length; j++) {
					strs = infos[j].split(",");
					try {
						cdr.setCdrType(strs[0]);
						cdr.setCallType(strs[1]);
						cdr.setStartTime(strs[2]);
						cdr.SetEndTime(strs[3]);
						cdr.setWangYuan(strs[4]);
						cdr.setFilePathRawOffset(strs[5]);
					} catch (Exception e) {
						continue;
					}
					if (checkByQueryCondition(cdr, data.getQueryConditions())) {
						recordPathOffset = strs[5].split("%");
						if (!result.containsKey(recordPathOffset[0])) {
							result.put(recordPathOffset[0], new StringBuffer(
									recordPathOffset[1]));
						} else {
							result.put(recordPathOffset[0], result.get(recordPathOffset[0]).append(",").append(recordPathOffset[1]));
						}
					}
				}
			} else {
				biccCdrInfo biccCdr = new biccCdrInfo();
				for (int j = 0; j < infos.length; j++) {
					strs = infos[j].split(",");
					biccCdr.setDpc(Long.valueOf(strs[4]));
					biccCdr.setOpc(Long.valueOf(strs[3]));
					biccCdr.setStartTime(Long.valueOf(strs[1]));
					biccCdr.setEndTime(Long.valueOf(strs[2]));
					biccCdr.setFilePath(strs[5]);
					biccCdr.setCallType(strs[0]);
					if (checkBiccByQueryCondition(biccCdr, data
							.getQueryConditions())) {
						recordPathOffset = strs[5].split("%");
						if (!result.containsKey(recordPathOffset[0])) {
							result.put(recordPathOffset[0],new StringBuffer(recordPathOffset[1]));
						} else {
							result.put(recordPathOffset[0],result.get(recordPathOffset[0]).append(",").append(recordPathOffset[1]));
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		if(!result.isEmpty())
			writeOneData(result);
		System.out.println("+++++readData+++++");
	}

	public boolean checkBiccByQueryCondition(biccCdrInfo biccCdr,
			queryConditions query) {
		if (!checkCallType(biccCdr.callType, query.callType))
			return false;
		if (!checkStartTime(biccCdr.startTime, query.getStartTime(), query
				.getEndTime()))
			return false;
		if (!checkJuxiang(biccCdr.getDpc(), biccCdr.getOpc(), query.getDpc(),
				query.getOpc()))
			return false;
		return true;
	}

	public boolean checkJuxiang(long dpc, long opc, long[] dpcs, long[] opcs) {
		boolean flag = false;
		if (dpcs == null || opcs == null)
			return true;
		if (dpcs.length == opcs.length) {
			for (int i = 0; i < dpcs.length; i++) {
				if (dpc == dpcs[i] && opc == opcs[i]) {
					flag = true;
					break;
				}
			}
		}
		return flag;
	}

	public boolean checkByQueryCondition(cdrInfo cdr, queryConditions query) {
		boolean result = true;
		if (!checkCallType(cdr.getCallType(), query.getCallType()))
			result = false;
		if (!checkCdrType(cdr.getCdrType(), query.getCdrType()))
			result = false;
		if (!checkStartTime(cdr.getStartTime(), query.getStartTime(), query
				.getEndTime()))
			result = false;
		if (!checkWangyuan(cdr.getWangYuan(), query.getWangYuanType(), query
				.getWangYuan()))
			result = false;

		return result;
	}

	public boolean checkCdrType(int cdrType, String queryCdrType) {
		boolean result = false;
		String[] strs = queryCdrType.split(",");
		for (int i = 0; i < strs.length; i++)
			if (Integer.valueOf(strs[i]) == cdrType) {
				result = true;
				break;
			}
		return result;
	}

	public boolean checkCallType(int cdrCallType, int queryCallType) {
		if (cdrCallType == 0) {
			int i = 0;
		}
		boolean result = false;
		if (queryCallType == 2)
			result = true;
		if (cdrCallType == queryCallType)
			result = true;
		return result;
	}

	public boolean checkWangyuan(String cdrWangyuan, int wangYuanType,
			String wangYuanInfo) {
		if (wangYuanInfo == null || wangYuanInfo.equals("null")
				|| wangYuanInfo.equals(""))
			return true;

		boolean result = false;
		String[] strs = cdrWangyuan.split("%");
		String[] wangYuan = wangYuanInfo.split(",");
		switch (wangYuanType) {
		case 0:
			for (int i = 0; i < wangYuan.length; i++)
				if (strs[0].equals(wangYuan[i]))
					result = true;
			break;
		case 1:
			for (int i = 0; i < wangYuan.length; i++)
				if (strs[1].equals(wangYuan[i]))
					result = true;
			break;
		case 2:
			for (int i = 0; i < wangYuan.length; i++)
				if (strs[2].equals(wangYuan[i]))
					result = true;
			break;
		default:
			break;
		}
		return result;
	}

	public boolean checkStartTime(long cdrStartTime, long[] startTime,
			long[] endTime) {
		boolean result = false;
		if (startTime.length != endTime.length) {
			result = false;
		}
		for (int i = 0; i < startTime.length; i++) {
			if (cdrStartTime >= startTime[i] && cdrStartTime < endTime[i]) {
				result = true;
				break;
			}
		}
		return result;
	}

	
}
class Index {
	private String fileName =  "";
	private queryConditions query;

	public String getFileName() {
		return fileName;
	}

	public Index(String fileName, queryConditions query) {
		this.fileName = fileName;
		this.query = query;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public queryConditions getQueryConditions() {
		return query;
	}

	public void setQueryConditions(queryConditions query) {
		this.query = query;
	}
}

