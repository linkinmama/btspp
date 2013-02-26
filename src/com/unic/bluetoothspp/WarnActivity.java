package com.unic.bluetoothspp;

import java.util.ArrayList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

public class WarnActivity extends Activity {
	ToggleButton bt_switcher;
	ListView devList;
	TextView warning;
	View bt_content;

	ArrayList<String> devices = new ArrayList<String>();
	ArrayAdapter<String> devAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.warn);
		doBindService();
		Button btn = (Button) findViewById(R.id.btn);
		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mBTService != null && mBTService.mNoiser != null) {
					mBTService.mNoiser.stop();
					finish();
				}
			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	/*
	 * 服务连接
	 */
	public BTService mBTService = null;
	private boolean mIsBound = false;
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBTService = ((BTService.LocalBinder) service).getService();

		}

		public void onServiceDisconnected(ComponentName className) {
			mBTService = null;
		}
	};

	void doBindService() {
		bindService(new Intent(WarnActivity.this, BTService.class),
				mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			unbindService(mConnection);
			mIsBound = false;
		}
	}

}