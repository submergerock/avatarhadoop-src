package org.apache.hadoop.hdfs.job;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.ClientDatanodeProtocol;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.net.NetUtils;

import com.alibaba.fastjson.JSON;

public class FileUtils {
	private static final String serverURL = "http://192.168.0.42:9888/converterx/CDRSocketClient";
	public static final Map<String, Boolean> FLAG = new HashMap<String, Boolean>();
	private static final Runtime runntime = Runtime.getRuntime();
	// private static final DistributedFileSystem fs = new
	// DistributedFileSystem();
	private static final Configuration conf = new Configuration();
	private static final int byteLen = 777;
	private static ClientProtocol namenode = null;
	private static final String confPath = "/usr/hadoop-0.20.2/conf/hdfs-site.xml";
	private static ClientDatanodeProtocol datanode = null;
	static {

		InetSocketAddress nameNodeAddr = NetUtils.createSocketAddr(
				"192.168.1.8", 9000);

		InetSocketAddress dataNodeAddr = NetUtils.createSocketAddr("127.0.0.1",
				50020);

		try {
			namenode = (ClientProtocol) RPC.getProxy(ClientProtocol.class,
					ClientProtocol.versionID, nameNodeAddr, conf, NetUtils
							.getSocketFactory(conf, ClientProtocol.class));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			datanode = (ClientDatanodeProtocol) RPC.getProxy(
					ClientDatanodeProtocol.class,
					ClientDatanodeProtocol.versionID, dataNodeAddr, conf,
					NetUtils.getSocketFactory(conf,
							ClientDatanodeProtocol.class));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private FileUtils() {
	}

	public static List lisPathcontent(String dirs, String sessionId)
			throws IOException, InterruptedException {

		Integer condition = new Integer(0);
		ArrayList<ReadFromLocalDiskThread> threadList = ReadFromLocalDiskThread
				.InitReadThread(confPath, byteLen, condition);

		int total = 0;
		List infoList = new ArrayList();
		// System.out.println("1111");
		String[] outArr = dirs.split("#");
		if (outArr == null || outArr.length == 0)
			return infoList;
		// System.out.println("22222"+outArr.length);
		String[] paths = new String[outArr.length];
		String[] offsetsArr = new String[outArr.length];
		for (int _i = 0; _i < outArr.length; _i++) {
			String[] temp = outArr[_i].split(" ");
			paths[_i] = temp[0];
			offsetsArr[_i] = temp[1];
		}

		LocatedBlocks[] lbArr = namenode.getBlockLocationsForMutilFile(paths,
				0, 777);
		Block[] blocks = new Block[lbArr.length];
		for (int i = 0; i < lbArr.length; i++) {
			blocks[i] = lbArr[i].getLocatedBlocks().get(0).getBlock();
			// System.out.println(paths[i]+"--"+blocks[i].getBlockName());
		}

		String[] abPath = datanode.getBlockFiles(blocks);
		System.out.println(Server.IP+" total file   " +abPath.length);
		// System.out.println("55555 " + abPath.length);
		for (int oi = 0; oi < abPath.length; oi++) {
			if (FLAG.get(sessionId) != null) {
				FLAG.remove(sessionId);
				return infoList;
			}
			for (int i = 0; i < threadList.size(); i++) {
				threadList.get(i).addReadIndex(abPath[oi], offsetsArr[oi]);
			}
		}

		for (int i = 0; i < threadList.size(); i++) {
			Thread thread = new Thread(threadList.get(i));
			thread.start();
		}

		List<byte[]> reslutDataFromMainThread = new ArrayList<byte[]>();

		while (!ReadFromLocalDiskThread.ThreadsIsOver(threadList)) {
			if (!ReadFromLocalDiskThread.listIsHaveData(threadList)) {
				if (!ReadFromLocalDiskThread.ThreadsIsOver(threadList)) {
					synchronized (condition) {
						condition.wait(100);
					}
				}
			}
			for (int i = 0; i < threadList.size(); i++) {
				threadList.get(i).getResultData(reslutDataFromMainThread);
			}
			// System.out.println("size is " + reslutDataFromMainThread.size());
			for (int j = 0; j < reslutDataFromMainThread.size(); j++) {
				List<String> listStr = JavaToJson
						.getBSSAPCDRValue(reslutDataFromMainThread.get(j));
				infoList.add(listStr);
				if (infoList.size() >= 80000) {
					// send2tomcat(infoList, sessionId);
					infoList.clear();
				}
			}
			reslutDataFromMainThread.clear();
		}

		// System.out.println(total);
		return infoList;

	}

	public static void send2tomcat(List list, String sessionId)
			throws HttpException, IOException {
		//System.out.println(Server.IP + " start send:" + new Date().getTime());
		HttpClient httpClient = new HttpClient();
		String info = JSON.toJSONString(list).toString();
		String para = "?sessionID=" + sessionId + "&count=" + list.size()
				+ "&infoLength=" + info.length();
		PostMethod postMethod = new PostMethod(serverURL + para);

		ByteArrayInputStream byteInputStream = new ByteArrayInputStream(info
				.getBytes());

		postMethod.setRequestBody(byteInputStream);

		int statusCode = httpClient.executeMethod(postMethod);
		//System.out.println(" send out" + new Date().getTime());

	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

	}

}
