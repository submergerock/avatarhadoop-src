
package org.apache.hadoop.hdfs.job;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

public class ReadFromLocalDiskThread0920 implements Runnable {

	private String startPath = "";
	private List<Index> list = new ArrayList<Index>();
	private boolean isOver = false;
	private int dataSize = 0;
	private List<byte[]> tmpReslutData = new ArrayList<byte[]>(); // �ṩ����Ļ���,Ϊ�˿���l��ͣ�Ķv���
	private List<byte[]> reslutData = new ArrayList<byte[]>(); //
	private Integer condition = null;

	public ReadFromLocalDiskThread0920(Integer condition) {
		this.condition = condition;
	}

	public static boolean ThreadsIsOver(ArrayList<ReadFromLocalDiskThread0920> list) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).isOver && !list.get(i).isHaveData()) {
				synchronized (list.get(i)) {
					list.get(i).notify();
				}
				list.remove(i);
			}
		}
		if (list.size() == 0) {
			return true;
		}

		return false;
	}

	public static ArrayList<ReadFromLocalDiskThread0920> InitReadThread(
			String conPath, int dataSize, Integer condition) {
		ArrayList<ReadFromLocalDiskThread0920> list = new ArrayList<ReadFromLocalDiskThread0920>();
		Configuration conf = new Configuration();
		// System.out.println(conf.get("dfs.data.dir"));
		conf.addResource(new Path(conPath));
		// System.out.println(conPath);
		String disks = conf.get("dfs.data.dir");
		if (disks.length() == 0) {
			return null;
		}
		String[] offsets = disks.split(",");
		for (int i = 0; i < offsets.length; i++) {
			ReadFromLocalDiskThread0920 thread = new ReadFromLocalDiskThread0920(
					condition);
			thread.setDiskPath(offsets[i].trim());
			thread.setDataSize(dataSize);
			list.add(thread);
		}
		return list;
	}

	public boolean isOver() {
		return isOver;
	}

	public void setDataSize(int dataSize) {
		this.dataSize = dataSize;
	}

	public void getResultData(List<byte[]> reslutDataFromMainThread) {

		synchronized (tmpReslutData) {
			reslutDataFromMainThread.addAll(tmpReslutData);
			tmpReslutData.clear();
		}

	}

	public boolean isHaveData() {
		boolean haveData = true;
		haveData = !tmpReslutData.isEmpty();
		return haveData;
	}

	public static boolean listIsHaveData(ArrayList<ReadFromLocalDiskThread0920> list) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).isHaveData()) {
				return true;
			}
		}
		return false;
	}

	public void run() {
		//System.out.println(Server.IP+" read thread begin " + new Date().getTime());
		try {
			for (int j = 0; j < list.size(); j++) {
				File f = new File(list.get(j).getFileName());
				String[] offsets = list.get(j).getOffsets().split(",");
				long offset = 0;
				long lastOffset = 0;
				byte[] rawInfo = new byte[dataSize];
				long length = dataSize;
				if (f.exists()) {
					// System.out.println(f.getAbsolutePath()+"
					// "+offsets.length);
					// TODO file read
					FileInputStream input = new FileInputStream(f);

					for (int i = 0; i < offsets.length; i++) {
						lastOffset = offset;
						offset = Long.valueOf(offsets[i]);

						if (i == 0)
							input.skip(offset);
						else
							input.skip(offset - lastOffset);

						input.read(rawInfo, 0, dataSize);
						reslutData.add(rawInfo);

						if (reslutData.size() > 1000) {
							// ֪ͨ���̻߳�ȡ��ݣ�
							synchronized (tmpReslutData) {

								tmpReslutData.addAll(reslutData);
							}
							if(!reslutData.isEmpty()) {
								reslutData.clear();
							}

							synchronized (condition) {
								condition.notifyAll();
							}
						}
					}
					input.close();
				}
			}

			// ֪ͨ���߳̽����̣߳�
			synchronized (tmpReslutData) {
				tmpReslutData.addAll(reslutData);
			}
			reslutData.clear();

			// System.out.println("the last notify main thread");
			synchronized (condition) {
				condition.notifyAll();
				isOver = true;
			}

			synchronized (this) {
				this.wait();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		//System.out.println(Server.IP+" read thread over " + new Date().getTime());
	}

	public void setDiskPath(String startPath) {
		this.startPath = startPath;
	}

	public void addReadIndex(String fileName, String offsets) {
		// ��Ӽ���ļ��Ƿ��Ǹ�·���µ�
		if (fileName.startsWith(startPath)) {
			list.add(new Index(fileName, offsets));
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
}
