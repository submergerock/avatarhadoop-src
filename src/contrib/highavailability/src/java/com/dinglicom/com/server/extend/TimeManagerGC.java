package com.dinglicom.com.server.extend;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class TimeManagerGC {
     //时间间隔
	//private static long PERIOD_DAY = 24 * 60 * 60 *1000;
	
	public TimeManagerGC(int hour,int min,int sec,long period){
	
	  Calendar calendar = Calendar.getInstance();
	  /*  定制每日  06：00 执行*/
	  calendar.set(Calendar.HOUR_OF_DAY, hour);
	  calendar.set(Calendar.MINUTE, min);
	  calendar.set(Calendar.SECOND, sec);
	  //第一次执行任务的时间
	  Date date = calendar.getTime();
	  /* 如果第一次执行定时任务的时间 < 当前的时间
	     此时要在第一次执行定时任务的时间 +1 天，以便此任务在下个时间点执行，如果不加一天，任务会立即执行
	  */
	  if(date.before(new Date())){
		  date = this.addDay(date, 1);
	  }
	  
	  Timer timer = new Timer();
	  
	  GCTask gcTask = new GCTask();
	  //安排指定的任务在指定的时间开始进行重复的固定延迟执行
	  timer.schedule(gcTask, date,period);
	}//end func
	
	//增加或减少天数
	public Date addDay(Date date,int num){
		Calendar startDT = Calendar.getInstance();
		startDT.setTime(date);
		startDT.add(Calendar.DAY_OF_MONTH, num);
		return startDT.getTime();
	}
	
	static class GCTask extends TimerTask{
		
		@Override
		public void run(){
			try{
				System.gc();
			}catch(Exception oe){
				System.out.println("run:  "+oe);
			}
			
		}
	}//end class
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TimeManagerGC timeManager = new  TimeManagerGC(16,12,0,0);
//		while(true){
//			try{
//				Thread.sleep(100);
//			}catch(Exception er){}
//		}
	}
}//end class
