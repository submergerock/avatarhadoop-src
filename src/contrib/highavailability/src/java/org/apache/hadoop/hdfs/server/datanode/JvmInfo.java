package org.apache.hadoop.hdfs.server.datanode;

import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

public class JvmInfo {
	private OperatingSystemMXBean osMxb = null;
	private MemoryMXBean memMx = null;
	private ThreadMXBean thMx  =  null;
	public JvmInfo(){
		osMxb = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
		memMx=(MemoryMXBean)ManagementFactory.getMemoryMXBean();	
		thMx=(ThreadMXBean)ManagementFactory.getThreadMXBean();  		
	}
	
	public long getTotalPhysicalMemorySize(){
        long totalPhysicalMemorySize = osMxb.getTotalPhysicalMemorySize()/(1024*1024);
		return totalPhysicalMemorySize;
	}
	
	public long getFreePhysicalMemorySize(){
        long freePhysicalMemorySize = osMxb.getFreePhysicalMemorySize()/(1024*1024);
        return freePhysicalMemorySize;
	}
	
	public long getTotaVirtualMemory(){
        long totaVirtualMemory =  osMxb.getTotalSwapSpaceSize()/(1024*1024);
        return 	totaVirtualMemory;					
	} 
	
	public long getFreeSweepMemorySize(){
		long freeSweepMemorySize = osMxb.getFreeSwapSpaceSize()/(1024*1024);
		return freeSweepMemorySize;
	}
	
	public long getCommitVirtualMemorySize(){
        long commitVirtualMemorySize = osMxb.getCommittedVirtualMemorySize()/(1024*1024);
        return 	commitVirtualMemorySize;	
	}
	
	public long getTotalJvmMemorySize(){
		long totalJvmMemorySize = Runtime.getRuntime().totalMemory()/(1024*1024);
		return totalJvmMemorySize;
	}
	
	public long getFreeJvmMemorySize(){
		long freeJvmMemory = Runtime.getRuntime().freeMemory()/(1024*1024);
		return freeJvmMemory;
	}
	
	public long getMaxMemorySize(){
		long maxMemory = Runtime.getRuntime().maxMemory()/(1024*1024);
		return maxMemory;
	}
	
	public long getUsedHeapSize(){
		long usedHeapSize = memMx.getHeapMemoryUsage().getUsed()/(1024*1024);
		return usedHeapSize;
	}
	
	public long getUsedNoneHeapSize(){
		long usedNoneHeapSize = memMx.getNonHeapMemoryUsage().getUsed()/(1024*1024);
		return usedNoneHeapSize;
	}
	
	public long getThreadCount(){
		long thCount = thMx.getThreadCount();
		return thCount;
	}
	
	public long getPeekThreadCount(){
		long peekThCount = thMx.getPeakThreadCount();
		return peekThCount;
	}	
	
	public long getDaemonThreadCount(){
		long daemonThreadCount = thMx.getDaemonThreadCount();
		return daemonThreadCount;
	}
	
	public String toString(){
		String jvminfo = 
      		   " totalvirtualmem="+getTotaVirtualMemory()+"m"
      		  +" freeSweepMemorySize="+getFreeSweepMemorySize()+"m"
      		  +" totalPhysicalMemorySize="+getTotalPhysicalMemorySize()+"m"
      		  +" freePhysicalMemorySize="+getFreePhysicalMemorySize()+"m"
      		  +" commitVirtualMemorySize="+getCommitVirtualMemorySize()+"m"
      		  +" totalJvmMemorySize="+getTotalJvmMemorySize()+"m"
      		  +" freeJvmMemory="+getFreeJvmMemorySize()+"m"
      		  +" maxMemory="+getMaxMemorySize()+"m"
      		  +" usedHeapSize="+getUsedHeapSize()+"m"
      		  +" usedNoneHeapSize="+getUsedNoneHeapSize()+"m"
      		  +" threadCount="+getThreadCount()
      		  +" peekThCount="+getPeekThreadCount()
      		  +" daemonThreadCount="+getDaemonThreadCount();
		return jvminfo;
				
	}
	
	
	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		// TODO Auto-generated method stub
		JvmInfo jvminfo = new JvmInfo();
		for(int i=0;i<100;i++){
			System.out.println("jvminfo="+jvminfo.toString());
			Thread.sleep(100);
		
		}//for
	}

}
