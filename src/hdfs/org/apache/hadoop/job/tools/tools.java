package org.apache.hadoop.job.tools;

public class tools {

	public static void sleep(int milis){
		try {
			Thread.sleep(milis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}			
	}
}
