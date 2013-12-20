
package org.apache.hadoop.hdfs.job;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.mina.common.DefaultIoFilterChainBuilder;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoAcceptorConfig;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.util.SessionLog;

import com.alibaba.fastjson.JSON;

public class Server {

	public static final String IP = LinuxGetIPUtil.getLocalIP();

	public void init() throws Exception {
		IoAcceptor acceptor = new SocketAcceptor();
		IoAcceptorConfig config = new SocketAcceptorConfig();
		DefaultIoFilterChainBuilder chain = config.getFilterChain();

		// Add SSL filter if SSL is enabled.
		TextLineCodecFactory t = new TextLineCodecFactory();
		t.setDecoderMaxLineLength(Integer.MAX_VALUE);
		t.setEncoderMaxLineLength(Integer.MAX_VALUE);
		chain.addLast("codec", new ProtocolCodecFilter(t));

		// Bind
		acceptor.bind(new InetSocketAddress(40045), new ChatProtocolHandler(),
				config);

		System.out.println("Listening on port " + 40045);

	}

	public Server() throws Exception {
		init();
	}

	public static void main(String[] args) throws Exception {
		FileUtils.main(null);
		new Server();
		System.out.println("Server start");
	}

	public static class ChatProtocolHandler extends IoHandlerAdapter {
		private static final String serverURL = "http://192.168.0.199:9081/converterx/CDRSocketClient";
		private static  List<String> EMPTY_LIST = new ArrayList<String>(0);
		private Set<IoSession> sessions = Collections
				.synchronizedSet(new HashSet<IoSession>());

		private Set<String> users = Collections
				.synchronizedSet(new HashSet<String>());

		public void exceptionCaught(IoSession session, Throwable cause) {
			SessionLog.error(session, "", cause);
			// Close connection when unexpected exception is caught.
			session.close();
		}

		public  void messageReceived(IoSession session, Object message) {
			System.out.println("messageReceived:" + new Date().getTime());
			String[] str = message.toString().split("!@");
			if (str.length != 2) {
				try {
					send2tomcat(EMPTY_LIST, str[0]);
				} catch (HttpException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return;
			}
			// 如果为kill
			if (str[1].startsWith("kill")) {

				FileUtils.FLAG.put(str[0], Boolean.FALSE);

				return;
			}
			try {

				List list = FileUtils.lisPathcontent(str[1], str[0]);
				if (list != null && !list.isEmpty()) {
					// TODO send to tomcat
					send2tomcat(list, str[0]);
				} else {
					send2tomcat(EMPTY_LIST, str[0]);
				}
				// session.write(list);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(" over:" + new Date().getTime());
		}
		
		public static void messageReceived(String message){
			System.out.println("messageReceived:" + new Date().getTime());
			String[] str = message.split("!@");
			if (str.length != 2) {
				try {
					send2tomcat(EMPTY_LIST, str[0]);
				} catch (HttpException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return;
			}
			// 如果为kill
			if (str[1].startsWith("kill")) {

				FileUtils.FLAG.put(str[0], Boolean.FALSE);

				return;
			}
			try {

				List list = FileUtils.lisPathcontent(str[1], str[0]);
				if (list != null && !list.isEmpty()) {
					// TODO send to tomcat
					send2tomcat(list, str[0]);
				} else {
					send2tomcat(EMPTY_LIST, str[0]);
				}
				// session.write(list);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(" over:" + new Date().getTime());
		}

		public static void send2tomcat(List list, String sessionId) throws HttpException,
				IOException {
			HttpClient httpClient = new HttpClient();
			String info = JSON.toJSONString(list).toString();
			String para = "?sessionID=" + sessionId + "&count=" + list.size()
					+ "&infoLength=" + info.length();
			PostMethod postMethod = new PostMethod(serverURL + para);

			ByteArrayInputStream byteInputStream = new ByteArrayInputStream(info
					.getBytes());

			postMethod.setRequestBody(byteInputStream);
			//System.out.println("serverURL + para : " + serverURL + para);
			
			//System.out.println("info : " + info);
			//int statusCode = httpClient.executeMethod(postMethod);

		}

		//    

		public void sessionClosed(IoSession session) throws Exception {
			String user = (String) session.getAttribute("user");
			users.remove(user);
			sessions.remove(session);
			// broadcast("The user " + user + " has left the chat session.");
		}

		public boolean isChatUser(String name) {
			return users.contains(name);
		}

		public int getNumberOfUsers() {
			return users.size();
		}

		public void kick(String name) {
			synchronized (sessions) {
				Iterator iter = sessions.iterator();
				while (iter.hasNext()) {
					IoSession s = (IoSession) iter.next();
					if (name.equals(s.getAttribute("user"))) {
						s.close();
						break;
					}
				}
			}
		}
}


}
