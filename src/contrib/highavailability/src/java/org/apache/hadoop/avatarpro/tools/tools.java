package org.apache.hadoop.avatarpro.tools;

public class tools {

	public static void sleep(int milis){
		try {
			Thread.sleep(milis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
	}
}
