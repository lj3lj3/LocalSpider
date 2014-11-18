package com.fydxt.localspider;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.fydxt.localspider.MainService.MainServiceBinder;

@EActivity(R.layout.activity_main)
public class MainActivity extends Activity implements OnItemClickListener {
	public static final String TAG = "MainActivity";
	public static final String BROADCAST_REFRESH_LIST_ACTION = "com.fydxt.Localspider.refreshlist";
	public static final String BROADCAST_NEW_MSG_ACTION = "com.fydxt.Localspider.newmsg";
	// the time delay of the runnable of list view scroller  
	public static final int TIME_DELAY = 200;
 
	// private LinkedList<HashMap<String, String>> clientList;
	private MainService service;
	private MainActivityConnection conn;

	private IntentFilter filter;
	private MainActivityReceiver receiver;
	private SimpleAdapter adapter;
	private boolean mBound = false;
	private String ip = null;

	@ViewById(R.id.bt_start)
	Button btStart;
	
	@ViewById(R.id.bt_stop)
	Button btStop;
	
	@ViewById(R.id.lv_client_list)
	ListView lvClient;
	
	@ViewById(R.id.tv_message)
	TextView tvMessage;
	
	@ViewById(R.id.tv_send)
	EditText etSend;
	
	@ViewById(R.id.bt_send)
	Button btSend;
	
	@ViewById(R.id.sv_message)
	ScrollView svMessage;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
//		setContentView(R.layout.activity_main);
		// clientList = new LinkedList<HashMap<String, String>>();

//		btStart = (Button) this.findViewById(R.id.bt_start);
//		btStop = (Button) this.findViewById(R.id.bt_stop);
//		lvClient = (ListView) this.findViewById(R.id.lv_client_list);
//		svMessage = (ScrollView) this.findViewById(R.id.sv_message);
//		tvMessage = (TextView) this.findViewById(R.id.tv_message);
//		etSend = (EditText) this.findViewById(R.id.tv_send);
//		btSend = (Button) this.findViewById(R.id.bt_send);

		conn = new MainActivityConnection();
		filter = new IntentFilter(BROADCAST_REFRESH_LIST_ACTION);
		// don't forget to set the filter :_)
		filter.addAction(BROADCAST_NEW_MSG_ACTION);
		receiver = new MainActivityReceiver();

		lvClient.setOnItemClickListener(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onStart() {
		Log.i(TAG, "registering the receiver");
		this.registerReceiver(receiver, filter);
		super.onStart();
	}

	@Override
	protected void onStop() {
		Log.i(TAG, "unregistering the receiver");
		this.unregisterReceiver(receiver);
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		// getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * refresh the client, and notify to show the new list
	 */
	private void refreshList() {
		if (!mBound) {
			return;
		}

		// clear the list
		// clientList.clear();

		// LinkedList<Bundle> list = service.getList();
		// ListIterator<Bundle> iterator = list.listIterator();
		// Bundle bundle;
		// HashMap<String, String> map;
		// while (iterator.hasNext()) {
		// bundle = iterator.next();
		// map = new HashMap<String, String>();
		// map.put(MainService.KEY_IP, bundle.getString(MainService.KEY_IP));
		// clientList.push(map);
		// }
		// notify the list data has changed
		adapter.notifyDataSetChanged();
	}
	
/*	@Click(R.id.bt_start)
	private void onBtStartClick (View v){
		Log.i(TAG, "start service ");

		this.startService(intent);
		if (!mBound) {
			this.bindService(intent, conn, Context.BIND_AUTO_CREATE);
		}

		btStart.setEnabled(false);
		btStop.setEnabled(true);
	}*/

	// get called when one of the buttons was clicked
//	@Click({R.id.bt_start, R.id.bt_stop, R.id.bt_send})
	public void onButtonClick(View v) {
		Intent intent = new Intent(this, MainService.class);
		switch (v.getId()) {
		case R.id.bt_start:
			Log.i(TAG, "start service ");

			this.startService(intent);
			if (!mBound) {
				this.bindService(intent, conn, Context.BIND_AUTO_CREATE);
			}

			btStart.setEnabled(false);
			btStop.setEnabled(true);
			break;
		case R.id.bt_stop:
			Log.i(TAG, "stop service");

			if (mBound) {
				this.unbindService(conn);
				mBound = false;
			}
			this.stopService(intent);

			btStart.setEnabled(true);
			btStop.setEnabled(false);
			setTitle(false);

			break;
		case R.id.bt_send:
			Log.v(TAG, "click the send button");
			if (!mBound) {
				return;
			}

			String msg = etSend.getText().toString();
			// the empty message, do nothing
			if (msg.equals("")) {
				return;
			}

			if (service.getChatId() < 0) {
				Toast.makeText(getApplicationContext(),
						R.string.pl_select_target_first, Toast.LENGTH_SHORT)
						.show();
				return;
			}
			// clear the entered message
			etSend.setText("");
			addMyMessage(msg);
			service.sendMessage(msg);

			break;
		default:
			Log.d(TAG, "unhandled button click, id : " + v.getId());
			break;
		}
	}

	// add the user send message to the end of the tvMessage
	private void addMyMessage(String msg) {
		if (!mBound) {
			return;
		}

		// no use for now
		// if (ip == null) {
		// ip = service.getIp();
		// }

		StringBuffer sb = new StringBuffer("\n");
		// SpannableStringBuilder builder = new SpannableStringBuilder("\n");
		// builder.append("me(" + ip + ") --> ");
		sb.append("me --> ");
		// two list's index both from 0, do nothing. only need to do is change
		// the method to getCheckedItemPosition
		sb.append(service.getList().get(lvClient.getCheckedItemPosition())
				.get(MainService.KEY_IP));
		// builder.append(service.getList().get(lvClient.getCheckedItemPosition()).get(
		// MainService.KEY_IP));
		sb.append(" : ");
		// builder.append(" : ");
		// builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags)
		sb.append(msg);
		// builder.
		// sb.append(msg);
		SpannableString ss = new SpannableString(sb.toString());
		ss.setSpan(new StyleSpan(Typeface.BOLD), sb.indexOf(":"), sb.length(),
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		// ss.setSpan(new , sb.indexOf(":") + 1, sb.length(),
		// Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		// if(tvMessage.getText().equals("")){
		// tvMessage.setText(ss);
		// } else {
		tvMessage.append(ss);
		// }
		scrollMsg2Bottom();
	}

	// when a new message has arrive
	// EDIT:set the type face of the message which came from the other device
	private void onNewMsg(String msg) {
		StringBuffer sb = new StringBuffer("\n");
		sb.append(msg);
		SpannableString ss = new SpannableString(sb.toString());
		ss.setSpan(new StyleSpan(Typeface.BOLD), sb.indexOf(":"), sb.length(),
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		tvMessage.append(ss);
		scrollMsg2Bottom();
	}
	
	private void scrollMsg2Bottom (){
		tvMessage.postDelayed(new Runnable() {
			@Override
			public void run() {
				svMessage.smoothScrollTo(0, svMessage.getBottom());
			}
		}, TIME_DELAY);
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int postion,
			long arg3) {
		if (!mBound) {
			return;
		}

		if (service.setNowChatId(postion)) {
			onChatIdChanged(postion);
		}
	}

	/**
	 * call this method when chat id has changed, then we do some stuff
	 * 
	 * @param chatId
	 */
	private void onChatIdChanged(int chatId) {
		// do something
	}

	// set the title of the activity with the ip address
	private void setTitle(boolean hasIp) {
		StringBuffer title = new StringBuffer(this.getString(R.string.app_name));
		if (hasIp) {
			// when we should show ip, get it
			ip = service.getIp();
			title.append("(" + ip + ")");
		}

		this.setTitle(title.toString());
	}

	private void setListAdapter() {
		int[] to = { R.id.text1 };
		adapter = new SimpleAdapter(getApplicationContext(), service.getList(),
				R.layout.client_list_item, new String[] { MainService.KEY_IP },
				to);
		lvClient.setAdapter(adapter);
	}

	private class MainHandler extends Handler {
		public static final String TAG = "Main Handler";

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
		}
	}

	private class MainActivityConnection implements ServiceConnection {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mBound = true;
			MainServiceBinder binder = (MainServiceBinder) service;
			MainActivity.this.service = binder.getService();

			setTitle(true);
			// set the list adapter, we store the list in the service
			setListAdapter();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			setTitle(false);
		}
	}

	private class MainActivityReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BROADCAST_REFRESH_LIST_ACTION)) {
				Log.d(TAG, "refresh list");
				refreshList();
			} else if (action.equals(BROADCAST_NEW_MSG_ACTION)) {
				Log.d(TAG, "got a new message");
				onNewMsg(intent.getStringExtra(MainService.KEY_MSG));
			}
		}
	}
}
