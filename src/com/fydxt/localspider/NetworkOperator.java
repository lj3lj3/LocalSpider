package com.fydxt.localspider;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.util.ByteArrayBuffer;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Base64;
import android.util.Log;

public class NetworkOperator implements Runnable {
	public static final String TAG = "NetworkOperator";

	private static final String NAME_BROADCAST = "broadcast";

	private static final String ENCODE_METHOD = "UTF-8";

	private static final long TIME_DELAY = 1000;
	private static final long TIME_PERIOD = 1000;
	/**
	 * LOCAL, this port is the local port of the UDP broadcast out
	 */
	private static final int PORT_UDP_OUT = 3333;
	/**
	 * HOST, this port is on the host, which we send the UDP to
	 */
	private static final int PORT_UDP_TARGET = 5555;
	/**
	 * LOCAL, this port is for the message out, this will send to PORT_MSG_IN
	 * port of target's
	 */
	private static final int PORT_MSG_OUT = 3334;
	/**
	 * LOCAL, this port is for the message in, we will receive the message on
	 * this port
	 */
	private static final int PORT_MSG_IN = 3335;
	// private static final String BROADCAST_ADDR = "192.168.3.255";
	private static final String BROADCAST_ADDR = "255.255.255.255";
	// private static final byte[] BROADCAST_ADDR_BYTE = new byte['255','255'];

	private static final int SIZE_MSG = 1000;

	private MainService service;
	private Timer timer;
	private BroadcastTask task;

	private DatagramSocket dataSocket;

	private DatagramSocket broadcastServerSocket;
	private DatagramPacket broadcastDataPack;

	private DatagramSocket msgSocket;

	private DatagramSocket msgServerSocket;
	private DatagramPacket msgDataPack;

	private BroadcastServerRunnable broadcastRunnable;
	private Thread broadcastServerThread;

	private MsgServerRunnable msgRunnable;
	private Thread msgServerThread;

	private byte[] localAddr;
	private byte[] data_out;

	private String ipAddr = null;

	public NetworkOperator(MainService context) {
		this.service = context;
	}

	public String getIp() {
		getLocalAddress();
		return ipAddr;
	}

	@Override
	public void run() {
		setBroadCastUp();
		setBroadcastServerUp();
		setMsgServerUp();
	}

	/**
	 * the begin of the operator
	 */
	public void setUp() {
		Log.i(TAG, "set up");
		// use other threa to get the network info
		Thread thread = new Thread(this);
		thread.start();
	}

	/**
	 * the end of the operator
	 */
	public void shutDown() {
		shutBroadcastServerDown();
		shutBroadCastDown();
		shutMsgServerDown();
	}

	/**
	 * use this method to start the broadcast up
	 */
	public void setBroadCastUp() {
		if (task == null) {
			Log.i(TAG, "setting the broadcast task up");
			task = new BroadcastTask();
		}

		if (timer == null) {
			Log.i(TAG, "setting the broadcast timer up");
			timer = new Timer(NAME_BROADCAST);
			timer.scheduleAtFixedRate(task, TIME_DELAY, TIME_PERIOD);
		}

		localAddr = getLocalAddress().getAddress();
		data_out = localAddr;
		// data = new String("8701").getBytes();

		// later use this
		// FIXME
		getHostAddress();
	}

	/**
	 * use this method to shut the broadcast down
	 */
	public void shutBroadCastDown() {
		Log.i(TAG, "shutting the broadcast down");
		if (dataSocket != null) {
			dataSocket.close();
			dataSocket = null;
		}

		if (task != null) {
			task.cancel();
			task = null;
		}

		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	/**
	 * set up the server, so we can receive the broadcast
	 */
	public void setBroadcastServerUp() {
		Log.i(TAG, "setting the messaging server up");
		try {
			broadcastServerSocket = new DatagramSocket(PORT_UDP_TARGET);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		// FIXME remove later
		// data = new String("server").getBytes();
		broadcastDataPack = new DatagramPacket(new byte[data_out.length],
				data_out.length);

		broadcastRunnable = new BroadcastServerRunnable(broadcastServerSocket,
				broadcastDataPack);
		broadcastServerThread = new Thread(broadcastRunnable);
		broadcastServerThread.start();
	}

	public void shutBroadcastServerDown() {
		Log.i(TAG, "shutting the server down");
		if (broadcastServerSocket != null) {
			if (!broadcastServerSocket.isClosed()) {
				broadcastServerSocket.close();
			}

			broadcastServerSocket = null;
		}

		if (broadcastRunnable != null) {
			broadcastRunnable.setRun(false);
			broadcastRunnable = null;
		}

		if (broadcastServerThread != null) {
			if (broadcastServerThread.isAlive()) {
				broadcastServerThread.interrupt();
			}

			broadcastServerThread = null;
		}
	}

	/**
	 * set up the message server, so we can receive the message
	 */
	public void setMsgServerUp() {
		Log.i(TAG, "setting the broadcast server up");
		try {
			msgServerSocket = new DatagramSocket(PORT_MSG_IN);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// FIXME remove later
		// data = new String("server").getBytes();
		msgDataPack = new DatagramPacket(new byte[SIZE_MSG], SIZE_MSG);

		msgRunnable = new MsgServerRunnable(msgServerSocket, msgDataPack);
		msgServerThread = new Thread(msgRunnable);
		msgServerThread.start();
	}

	public void shutMsgServerDown() {
		Log.i(TAG, "shutting the message server down");
		if (msgServerSocket != null) {
			if (!msgServerSocket.isClosed()) {
				msgServerSocket.close();
			}

			msgServerSocket = null;
		}

		if (msgRunnable != null) {
			msgRunnable.setRun(false);
			msgRunnable = null;
		}

		if (msgServerThread != null) {
			if (msgServerThread.isAlive()) {
				msgServerThread.interrupt();
			}

			msgServerThread = null;
		}
	}

	private InetAddress getLocalAddress() {
		WifiManager wim = (WifiManager) service
				.getSystemService(Context.WIFI_SERVICE);

		InetAddress addr = null;
		// try {
		// addr = InetAddress.getLocalHost();
		// } catch (UnknownHostException e) {
		// e.printStackTrace();
		// }
		String ip = Formatter.formatIpAddress(wim.getConnectionInfo()
				.getIpAddress());
		Log.i(TAG, "the ip address is : " + ip);
		try {
			addr = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		ipAddr = ip;
		return addr;
	}

	private InetAddress getHostAddress() {
		Log.i(TAG, "getting the host address");
		InetAddress addr = null;
		WifiManager manager = (WifiManager) service
				.getSystemService(Context.WIFI_SERVICE);
		DhcpInfo dhcp = manager.getDhcpInfo();
		Log.i(TAG, "the dhcp is : " + dhcp);
		try {
			addr = InetAddress.getByAddress(ByteBuffer.allocate(4)
					.putInt(dhcp.gateway).array());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		return addr;
	}

	// move into the runnable
	// public void sendLocalBroadCast() {
	//
	// }

	/**
	 * when the server receive a broadcast
	 * 
	 * @param data
	 */
	public void onBroadcastReceive(byte[] data) {
		String ip = null;
		try {
			ip = InetAddress.getByAddress(data).getHostAddress();
			// ip = new String(data, ENCODE_METHOD);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		try {
			if (InetAddress.getByAddress(localAddr).getHostAddress()
					.equalsIgnoreCase(ip)) {
				Log.d(TAG, "we receive the broadcast send by our own");
				return;
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return;
		}

		service.add2List(ip);
	}

	public void onMsgReceive(DatagramPacket msgPack) {
		int length = msgPack.getLength();
		Log.d(TAG, "length of the message : " + length);
		//trim the content of the msg
		ByteArrayBuffer byteArray = new ByteArrayBuffer(length);
		byteArray.append(msgPack.getData(), 0, length);
		byte[] data = byteArray.buffer();
		// decode the message from the web 
		byte[] dataDecode = Base64.decode(data, Base64.DEFAULT); 
		ByteBuffer buffer = ByteBuffer.wrap(dataDecode);
		CharBuffer cb = Charset.forName(ENCODE_METHOD).decode(buffer);
//		String msg = cb.toString();
		StringBuffer sb = new StringBuffer(msgPack.getAddress()
				.getHostAddress());
		sb.append(" : ");
		sb.append(cb.toString());
		Log.d(TAG, "the msg we receive is : " + sb.toString());
		service.onNewMsg(sb.toString());
	}

	/**
	 * send the message to the ipTarget
	 * 
	 * @param ipTarget
	 * @param msg
	 */
	public void sendMessage(String ipTarget, String msg) {
		MsgSenderRunnable runnable = new MsgSenderRunnable(ipTarget, msg);
		Thread thread = new Thread(runnable);
		thread.start();
	}

	private class BroadcastTask extends TimerTask {

		@Override
		public void run() {
			// NetworkOperator.this.sendLocalBroadCast();
			try {
				if (dataSocket == null || dataSocket.isClosed()) {
					dataSocket = new DatagramSocket(PORT_UDP_OUT);
					dataSocket.setBroadcast(true);
					Log.i(TAG, "set a new data socket up");
				} else {
					Log.i(TAG, "the data socket is ok");
				}
				// byte[] localAddr = getLocalAddress().getAddress();
				//
				// byte[] data = localAddr;
				// InetAddress hostAddr = getHostAddress();
				Log.i(TAG, "a data : from : "
						+ dataSocket.getLocalAddress().getHostAddress() + " : "
						+ PORT_UDP_OUT + " ---> " + BROADCAST_ADDR + " : "
						+ PORT_UDP_TARGET + ", data : "
						+ InetAddress.getByAddress(data_out).getHostAddress()
						+ ", length : " + data_out.length);
				//
				// Log.i(TAG, "build a data pack, data : " + new String(data)
				// + ", length : " + data.length + ", host ip : "
				// + BROADCAST_ADDR + ", port : " + PORT_UDP_TARGET);
				DatagramPacket dataPack = new DatagramPacket(data_out, data_out.length,
						InetAddress.getByName(BROADCAST_ADDR), PORT_UDP_TARGET);
				Log.i(TAG, "sending the data package");
				dataSocket.send(dataPack);
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class BroadcastServerRunnable implements Runnable {
		private DatagramSocket serverSocket;
		private DatagramPacket dataPack;
		private boolean isRun = true;

		public BroadcastServerRunnable(DatagramSocket serverSocket,
				DatagramPacket dataPack) {
			this.serverSocket = serverSocket;
			this.dataPack = dataPack;
		}

		public void setRun(boolean isRun) {
			this.isRun = isRun;
		}

		@Override
		public void run() {
			while (isRun) {
				try {
					serverSocket.receive(dataPack);
					Log.d(TAG, "we receive a data package : "
							+ dataPack
							+ ", the ip address of the sender is : "
							+ InetAddress.getByAddress(dataPack.getData())
									.getHostAddress());
				} catch (SocketException e) {
					e.printStackTrace();
					continue;
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}

				NetworkOperator.this.onBroadcastReceive(dataPack.getData());
			}
		}
	}

	private class MsgServerRunnable implements Runnable {
		private DatagramSocket msgServerSocket;
		private DatagramPacket msgDataPack;
		private boolean isRun = true;

		public MsgServerRunnable(DatagramSocket msgServerSocket,
				DatagramPacket msgDataPack) {
			this.msgServerSocket = msgServerSocket;
			this.msgDataPack = msgDataPack;
		}

		public void setRun(boolean isRun) {
			this.isRun = isRun;
		}

		@Override
		public void run() {
			while (isRun) {
				try {
					msgServerSocket.receive(msgDataPack);
					Log.d(TAG, "we receive a msg package : " + msgDataPack);
				} catch (SocketException e) {
					e.printStackTrace();
					continue;
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}

				onMsgReceive(msgDataPack);
				// NetworkOperator.this.onBroadcastReceive(dataPack.getData());
			}
		}
	}

	private class MsgSenderRunnable implements Runnable {
		private String ipTarget;
		private String msg;

		public MsgSenderRunnable(String ipTarget, String msg) {
			this.ipTarget = ipTarget;
			this.msg = msg;
		}

		@Override
		public void run() {
			try {
				if (msgSocket == null || msgSocket.isClosed()) {
					msgSocket = new DatagramSocket(PORT_MSG_OUT);
					// msgSocket.setBroadcast(true);
					Log.i(TAG, "set a new msg socket up");
					// Log.i(TAG, "setting the message out socket, ip"
					// + msgSocket.getLocalAddress().getHostAddress()
					// + ", port : " + PORT_MSG_OUT);
				} else {
					Log.i(TAG, "the msg socket is ok");
				}
				// byte[] localAddr = getLocalAddress().getAddress();
				//
				// byte[] data = localAddr;
				// InetAddress hostAddr = getHostAddress();
				byte[] msgData = msg.getBytes(Charset.forName(ENCODE_METHOD));
				// use the base64 encode to transfer the info message on the web
				byte[] msgDataEncode = Base64.encode(msgData, Base64.DEFAULT);
				Log.i(TAG, "a message : "
						+ msgSocket.getLocalAddress().getHostAddress() + " : "
						+ PORT_MSG_OUT + " ---> " + ipTarget + " : "
						+ PORT_MSG_IN + ", msg : " + msg + ", length : "
						+ msgDataEncode.length);
				// Log.i(TAG, "build a message pack, msg : " + msg +
				// ", length : "
				// + msgData.length + ", host ip : " + ipTarget
				// + ", port : " + PORT_MSG_IN);

				DatagramPacket dataPack = new DatagramPacket(msgDataEncode,
						msgDataEncode.length, InetAddress.getByName(ipTarget),
						PORT_MSG_IN);
				Log.i(TAG, "sending the message package");
				dataSocket.send(dataPack);
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
