package org.apache.hadoop.hdfs.job;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

public class ReadFromLocalDiskThread_20110927  extends ReadFromLocalDiskThreadBase<Index> {

	private int dataSize = 0;
	private String startPath = "";
	private String fileName = "";
	private String offsets = "";
	
	public static ArrayList<ReadFromLocalDiskThread_20110927> InitReadThread(
			String conPath, int dataSize, Integer condition) {
		ArrayList<ReadFromLocalDiskThread_20110927> list = new ArrayList<ReadFromLocalDiskThread_20110927>();
		Configuration conf = new Configuration();
	    conf.addResource(new Path(conPath));
		String disks = conf.get("dfs.data.dir");
		if (disks.length() == 0) {
			return null;
		}
		String[] offsets = disks.split(",");
		for (int i = 0; i < offsets.length; i++) {
			ReadFromLocalDiskThread_20110927 thread = new ReadFromLocalDiskThread_20110927(
					condition);
			thread.setDiskPath(offsets[i].trim());
			thread.setDataSize(dataSize);
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
	
	public ReadFromLocalDiskThread_20110927(Integer condition) {
		super(condition);
	}

	public void addReadIndex(String fileName, String offsets) {
		if (fileName.startsWith(startPath)) {
			this.fileName = fileName;
			this.offsets = offsets;
			addReadIndex();
		}
	}
	
	protected Index  currentReadPos()
	{
		return new Index(fileName,offsets);
	}

	protected  void readData( Index data)
	{
		try
		{
		File f = new File(data.getFileName());
		String[] offsets = data.getOffsets().split(",");
		long offset = 0;
		long lastOffset = 0;
		byte[] rawInfo = new byte[dataSize];
		long length = dataSize;
		if (f.exists()) {
			FileInputStream input = new FileInputStream(f);
			for (int i = 0; i < offsets.length; i++) {
				lastOffset = offset;
				offset = Long.valueOf(offsets[i]);
					if (i == 0) {
						long number = input.skip(offset);
					} else {
						long number = input.skip(offset - lastOffset-dataSize);
					}
				 int datalength = input.read(rawInfo, 0, dataSize);
				writeOneData(rawInfo,dataSize);
			}
			input.close();
		}
	}catch(Exception e)
	{
		e.printStackTrace();
	}
}
}

class Index {
	private String fileName = "";
	private String offsets = "";

	public String getFileName() {
		return fileName;
	}

	public Index(String fileName, String offsets) {
		this.fileName = fileName;
		this.offsets = offsets;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getOffsets() {
		return offsets;
	}

	public void setOffsets(String offsets) {
		this.offsets = offsets;
	}
}

