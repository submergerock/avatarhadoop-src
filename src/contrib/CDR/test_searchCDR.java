
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.HashMap;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.server.datanode.DataNode;

public class test_searchCDR {
	private static DataNode dataNode = null;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		HashMap<String, StringBuffer> hsMap = new HashMap<String, StringBuffer>();
		String[] str ;
		String[] localPaths = {"D:\\new.data"};//dataNode.getBlockFiles(str);		
		String[] hdfsPaths = new String[localPaths.length];
		String[] hdfsPaths1 = new String[localPaths.length];
		int flag = 0;
		for (int i = 0; i < localPaths.length; i++) {
			try {
				String info=getFirstLevelRecord(Long.parseLong("90086"), localPaths[i]);
				System.out.println(info);
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
			
			
			
			
			
			
			
//			// 查询二级索引
//			if (binarySearch(Long.parseLong("90086"), localPaths[i]) != -1) {
//				// 找到的数据
//				long[] result=getOffsetAndLength(Long.parseLong("90086"),localPaths[i]);
//				hdfsPaths[flag] = localPaths[i].substring(0, localPaths[i].lastIndexOf("/"))+ "first_level.data";
//				hdfsPaths1[flag] = localPaths[i].substring(0, localPaths[i].lastIndexOf("/"))+ "first_level.data " +result[0] ;
//				flag ++;
//			}
		}		
//		String[] temps = new String[flag];
//		System.arraycopy(hdfsPaths,0,temps,0,flag);
////		nameNode.getBlockLocationsForMutilFile(arg0, arg1, arg2);
//		LocatedBlocks[] lbArr = nameNode.getBlockLocationsForMutilFile(temps,0, 777);
//		System.arraycopy(hdfsPaths1,0,temps,0,flag);
//		for(int j = 0 ;j < lbArr.length ; j++){
//			String host = lbArr[j].get(0).getLocations()[0].getHost();			
//			if(hsMap.get(host)==null){
//				StringBuffer sBuffer = new StringBuffer();
//				hsMap.put(host,sBuffer.append(temps[j]));
//			}else {
//				hsMap.put(host, hsMap.get(host).append("$$$").append(temps[j]));
//			}
//		}
	}
	
	public static String getFirstLevelRecord(long target,String indexFolderPath) throws IOException
	{
		String info ="";
		long slen=-1;
		byte[] buffer;
//		FSDataInputStream inFirst=fs.open(new Path("/indexingCDR/bassap/1316687991_1316688001_1316688642550/merger.data"));
		RandomAccessFile inFirst = new RandomAccessFile("/usr/local/hadoop-0.20.1-dev-patch/blk_8284729323740687044","r");
//		System.out.println(inFirst.readInt());
		inFirst.seek(4);
//		slen = Bytes8tolong(buffer);
//		System.out.println(inFirst.readLong());
		slen = inFirst.readLong();
		int sl = (int)slen/16;
		long sep = -1;
		long start = 0;
		long end = sl;
		long pn = target;
		while (start<= end){
 			 long middleIndex=(start + end)/2;	 			
 			 inFirst.seek(4+8+(middleIndex*16));
 			 long data=inFirst.readLong();
			 if (pn==data)
			 {
				 sep = inFirst.readLong();
				 break;
			 }	
			 if (pn >= data)
			 {
			 	start = middleIndex + 1;
			 }
			 else
			 {	
			 	end = middleIndex - 1;
			 }
		}
//		for(int i =0;i<sl;i++){			
//			if(inFirst.readLong()==Long.valueOf(query.getPhoneNum())){
//				sep = inFirst.readLong();
//				break;
//			}else {
//				inFirst.skipBytes(8);
//			}			
//		}		
		System.out.println("sep : " + sep);
		if(sep!=-1){
			inFirst.seek(sep-8);
			info = inFirst.readLine();
			String[] infos=info.split(";");
			
		}
		Date d3 = new Date();
		inFirst.close();
		return info;
	}
	
	public static long Bytes8tolong(byte[] eight) {

		long[] longs = new long[8];
		longs[0] = eight[0] & 0x00000000000000ff;
		longs[1] = eight[1] & 0xff;
		longs[2] = eight[2] & 0xff;
		longs[3] = eight[3] & 0xff;
		longs[4] = eight[4] & 0xff;
		longs[5] = eight[5] & 0xff;
		longs[6] = eight[6] & 0xff;
		longs[7] = eight[7] & 0xff;
		long result = longs[0] << 56 | longs[1] << 48 | longs[2] << 40
				| longs[3] << 32 | longs[4] << 24 | longs[5] << 16
				| longs[6] << 8 | longs[7];
		return result;
	}
}
