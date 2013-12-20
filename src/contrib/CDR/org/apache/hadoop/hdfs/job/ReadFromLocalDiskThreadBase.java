package org.apache.hadoop.hdfs.job;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

public  abstract class ReadFromLocalDiskThreadBase<T> implements Runnable {


	private List<T> list = new ArrayList<T>();
	private boolean isOver = false;
	private List<byte[]> tmpReslutData = new ArrayList<byte[]>(); // 锟结供锟斤拷锟斤拷幕锟斤拷锟�为锟剿匡拷锟斤拷l锟斤拷停锟侥秜锟斤拷锟�
	private List<byte[]> reslutData = new ArrayList<byte[]>(); //
	private Integer condition = null;

	public ReadFromLocalDiskThreadBase(Integer condition) {
		this.condition = condition;
	}

	public static boolean ThreadsIsOver(ArrayList<? extends ReadFromLocalDiskThreadBase> list) {
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

	public boolean isOver() {
		return isOver;
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

	public static boolean listIsHaveData(ArrayList<ReadFromLocalDiskThread> list) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).isHaveData()) {
				return true;
			}
		}
		return false;
	}

	public void writeOneData(byte[] rawInfo,int length)
	{
		if(length <= 0)
		{
			length = rawInfo.length;
		}
		byte [] data = new byte[length];
		System.arraycopy(rawInfo, 0, data, 0, length);
		 reslutData.add(data);
		 
		if (reslutData.size() > 1000) {
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
	
	public void writeDataOver()
	{				
		synchronized (tmpReslutData) {
			tmpReslutData.addAll(reslutData);
		}
		reslutData.clear();
		
		synchronized (condition) {
			condition.notifyAll();
			isOver = true;
		}

		synchronized (this) {
			try {
				this.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void run() {
		//System.out.println(Server.IP+" read thread begin " + new Date().getTime());
		try {
			for (int j = 0; j < list.size(); j++) {
				readData(list.get(j));
			}
			writeDataOver();
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}

	private  T getReadPos()
	{
		return currentReadPos();
	}
	
	//override 
	protected abstract T  currentReadPos();
	//override
	protected abstract void readData( T data);
	
	public void addReadIndex() {
			list.add(getReadPos());
	}
		
}

