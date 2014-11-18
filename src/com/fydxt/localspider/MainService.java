package com.fydxt.localspider;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class MainService extends Service {
	public static final String TAG = "MainService";

	public static final String KEY_IP = "ip";
	public static final String KEY_NULL = "null";
	// this used for the new message broadcast
	public static final String KEY_MSG = "msg";

	// this is the chat id of now conversation
	private int chatId = -1;

	// we store the list in the service, and the show list in the activity
	private LinkedList<HashMap<String, String>> connectedClient;

	private NetworkOperator operator;

	// set this binder to the fianl, 'casue we just need one, and the binder
	// only be create once
	private final MainServiceBinder binder = new MainServiceBinder();

	private final Intent INTENT_REFRESH_LIST = new Intent(
			MainActivity.BROADCAST_REFRESH_LIST_ACTION);
	private Intent intentNewMsg = new Intent(
			MainActivity.BROADCAST_NEW_MSG_ACTION);

	// private ServiceHandler handler;

	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "on create");

		connectedClient = new LinkedList<HashMap<String, String>>();
		createOperator();
		// handler = new ServiceHandler(this);

		super.onCreate();
	}
	
	private void createOperator (){
		operator = new NetworkOperator(this);
		operator.setUp();		
	}

	@Override
	public void onDestroy() {
		operator.shutDown();
		operator = null;

		// handler.destory();
		// handler = null;

		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return super.onStartCommand(intent, flags, startId);
	}

	public boolean add2List(String ip) {
		Log.i(TAG, "adding the ip to the list");
		ListIterator<HashMap<String, String>> iterator = connectedClient.listIterator();
		HashMap<String, String> tempMap;
		String tempString;
		while (iterator.hasNext()) {
			tempMap = iterator.next();
			tempString = tempMap.get(KEY_IP); 
			if (tempString.equalsIgnoreCase(ip)) {
				Log.d(TAG, "we already have this ip in the list : " + ip);
				return false;
			}
			Log.d(TAG, "the ip in the list is : " + tempString);
		}

		tempMap = new HashMap<String, String>();
		tempMap.put(KEY_IP, ip);
		connectedClient.add(tempMap);

		this.sendBroadcast(INTENT_REFRESH_LIST);
		return true;
	}

	/**
	 * BIND get the ip of this device
	 * 
	 * @return
	 */
	public String getIp() {
//		if(operator == null){
//			createOperator();
//		}
		return operator.getIp();
	}

	/**
	 * BIND get the chat id of the current session
	 * @return
	 */
	public int getChatId() {
		return chatId;
	}

	/**
	 * BIND get the list of the connected client
	 * 
	 * @return
	 */
	public LinkedList<HashMap<String, String>> getList() {
		return connectedClient;
	}

	/**
	 * BIND set the now id of the chat id
	 * 
	 * @param chatId
	 * @return true if the chat id has changed, false if the chat id still the
	 *         same
	 */
	public boolean setNowChatId(int chatId) {
		Log.d(TAG, "setting now charId : " + chatId);
		if (this.chatId == chatId) {
			return false;
		}

		this.chatId = chatId;
		return true;
	}

	/**
	 * BIND send a message to the chat id
	 * 
	 * @param msg
	 */
	public void sendMessage(String msg) {
		Log.d(TAG, "the now chat id is : " + chatId);
		if (chatId < 0) {
			return;
		}
		String ipTarget = connectedClient.get(chatId).get(KEY_IP);
//		String msgOut = getIp() + ":" + msg;
		Log.v(TAG, "the sending message is : " + msg);
		operator.sendMessage(ipTarget, msg);
	}

	public void onNewMsg(String msg) {
		intentNewMsg.putExtra(KEY_MSG, msg);
		this.sendBroadcast(intentNewMsg);
	}

	@Override
	public void onRebind(Intent intent) {
		// TODO Auto-generated method stub
		super.onRebind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// TODO Auto-generated method stub
		return super.onUnbind(intent);
	}

	public class MainServiceBinder extends Binder {

		public MainService getService() {
			return MainService.this;
		}
	}
}
