package com.unic.bluetoothspp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;

public class BTService extends Service {
	private static final String TAG = "BTService";
	static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
	static final UUID uuid = UUID.fromString(SPP_UUID);
	static final String tag = "BluetoothSPP";
	static final boolean D = true;

	BluetoothAdapter btAdapt;
	BluetoothSocket btSocket;
	InputStream btIn = null;
	OutputStream btOut = null;

	boolean sppConnected = false;
	boolean goBackward = false;

	private TelephonyManager mTelephonyManager;
	private byte mSignalStrength = -1;
	private String mIncomingNumber = "";
	private byte mBatteryPercent = -1;

	private SppReceiver mSppReceiver;
	public Ringtone mNoiser;

	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		BTService getService() {
			return BTService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.w(TAG, "Bind服务");
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.w(TAG, "创建服务");
		// 用BroadcastReceiver来取得搜索结果

		btAdapt = BluetoothAdapter.getDefaultAdapter();

		IntentFilter batteryFilter = new IntentFilter();
		batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(mBatteryReceiver, batteryFilter);

		if (mTelephonyManager == null) {
			mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		}
		mTelephonyManager.listen(new MyPhoneStateListener(),
				PhoneStateListener.LISTEN_CALL_STATE
						| PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

	}
	
	

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.w(TAG, "============ServiceOnStart===============");
	}

	@Override
	public void onDestroy() {
		Log.w(TAG, "============ServiceonDestroy===============");
		Toast.makeText(getApplicationContext(), "BT服务销毁！！！",
			     Toast.LENGTH_SHORT).show();
		mTelephonyManager.listen(new MyPhoneStateListener(),
				PhoneStateListener.LISTEN_NONE);
		unregisterReceiver(mBatteryReceiver);

		if (btIn != null) {
			try {
				if (btSocket != null)
					btSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		

		sppConnected = false;
		super.onDestroy();
		
		Intent localIntent = new Intent();
        localIntent.setClass(this, BTService.class);  //销毁时重新启动Service
        this.startService(localIntent);
//		android.os.Process.killProcess(android.os.Process.myPid());
	}
	
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	private byte[] mData = new byte[5];
	private int mDataLength = 0;

	private class SppReceiver extends Thread {
		private InputStream input = null;

		public SppReceiver(InputStream in) {
			input = in;
		}

		public void run() {
			byte[] data = new byte[1024];
			int length = 0;
			if (input == null) {
				Log.d(tag, "InputStream null");
				return;
			}
			while (sppConnected) {
				try {
					Log.i("yz", "Connected!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1");
					length = input.read(data);
					Log.i("unic", "length=" + length);
//					for (int i = 0; i < length; i++) {
//						Log.i("unic", "SPP receiver:" + data[i]);
//					}
					// Log.i("unic", "SPP receiver:" + data);
					if (length > 0 && length < 5) {
						mDataLength += length;
						Log.i("unic", "mDataLength========" + mDataLength);
						Log.i("unic", "length========" + length);
						int startPos = mDataLength - length;
						for (int i = startPos; i < mDataLength; i++) {
							mData[i] = data[i - startPos];
						}
					} else if (length == 5) {
						mDataLength = 5;
						mData = data;
					} 
					if (mDataLength == 5 && dataCheckPassed(mData)) {
						Log.i("yz", "SPP RESPONCE!!!!!!!");
						byte[] answer = response(mData[3]);
						btOut.write(answer);
						mDataLength = 0;
					}
				} catch (IOException e) {
					Log.e("unic", "disconnect");
					return;
				}
			}
		}
	}
	
	private void makeNoise() {	    
	    if (mNoiser == null) mNoiser = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_RING,
                am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        mNoiser.play();
	}
	

	private class ReConnector extends Thread {
		public ReConnector() {
		}

		public void run() {
			while (sppConnected) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (lastAnswerTime != -1
						&& System.currentTimeMillis() - lastAnswerTime > 3000) {
					Log.i("unic", lastDevice);
					btHandler.sendEmptyMessage(2);
					return;

				}
			}
		}
	}

	private boolean dataCheckPassed(byte[] data) {
		if (data[4] == (data[0] + data[1] + data[2] + data[3]) % 0xff) {
			lastAnswerTime = System.currentTimeMillis();
			Log.i(tag, "lastAnswerTime:" + lastAnswerTime);
			return true;
		}
		return false;
	}

	private long lastAnswerTime = -1;
	private String lastDevice = "";

	private class MyPhoneStateListener extends PhoneStateListener {

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
				mIncomingNumber = incomingNumber;
				Log.i("unic", "ringing-" + mIncomingNumber);

				if (btOut != null && sppConnected) {
					try {
						btOut.write(response(4));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				mIncomingNumber = "";
				Log.i("unic", "offhook-" + mIncomingNumber);

				if (btOut != null && sppConnected) {
					try {
						btOut.write(response(0));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				mIncomingNumber = "";
				Log.i("unic", "idle-" + mIncomingNumber);

				if (btOut != null && sppConnected) {
					try {
						btOut.write(response(0));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break;
			default:
				break;
			}
			super.onCallStateChanged(state, incomingNumber);
		}

		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			mSignalStrength = (byte) ((int) (signalStrength
					.getGsmSignalStrength() * 2 - 113));// ��asu����dbm
			Log.i(tag, "signal strength:" + mSignalStrength);
			super.onSignalStrengthsChanged(signalStrength);
		}
	}

	private BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			int current = intent.getExtras().getInt("level");// 获得当前电量
			int total = intent.getExtras().getInt("scale");// 获得总电量
			int percent = current * 100 / total;
			mBatteryPercent = (byte) percent;
			Log.i(tag, "battery:" + mBatteryPercent);
		}
	};

	Handler btHandler = new Handler() {
		@Override
		public void handleMessage(Message m) {
			switch (m.what) {
			case 0:
				break;
			case 1:
				break;
			case 2:
				if (!lastDevice.equals(""))
					connect(lastDevice);
				break;
			default:
				break;
			}
		}
	};

	public void connect(String devAddr) {

		if (btAdapt.isDiscovering()) {
			btAdapt.cancelDiscovery();
		}

		if (btSocket != null) {
			try {
				btSocket.close();
				btSocket = null;
			} catch (IOException e) {
				btSocket = null;
				Log.i("unic", "error when closing btSocket");
			}
		}

		lastAnswerTime = -1;

		try {
			// 创建SPP 的 RfcommSocket, SPP从机
			btSocket = btAdapt.getRemoteDevice(devAddr)
					.createRfcommSocketToServiceRecord(uuid);
			btSocket.connect();

			synchronized (BTService.this) {
				btIn = btSocket.getInputStream();
				btOut = btSocket.getOutputStream();

				sppConnected = true;
				mSppReceiver = new SppReceiver(btIn);
				mSppReceiver.start();

				new ReConnector().start();
			}

			lastDevice = devAddr;
		} catch (IOException e) {
			Log.e("unic", "error when connecting");

			try {
				btSocket.close();
			} catch (IOException e1) {
				e1.printStackTrace();
				Log.e("unic", "error when btSocket.close()");
			}
			btSocket = null;
			Toast.makeText(BTService.this,
					getString(R.string.connection_failed), Toast.LENGTH_SHORT)
					.show();
			btHandler.sendEmptyMessageDelayed(2, 5000);
		}
	}

	public void disconnect(boolean isByUser) {
		if (isByUser) {
			sppConnected = false;
			if (btSocket != null) {
				try {
					btSocket.close();
					btSocket = null;
					lastAnswerTime = -1;
					Log.i("unic", "btSocket closed");
				} catch (IOException e) {
					Log.i("unic", "error when btSocket closing");
				}
			}
		}
		lastDevice = "";
	}

	private byte[] response(int answerType) {
		byte[] answer = null;
		switch (answerType) {
		case 0:
			answer = new byte[] { (byte) 0xff, 0x02, 0x03, mBatteryPercent,
					mSignalStrength, 0 };
			break;
		case 1:
			Time time = new Time();
			time.setToNow();

			Log.i(tag, "" + (time.year - 2000));
			Log.i(tag, "" + (time.month + 1));
			Log.i(tag, "" + time.monthDay);
			Log.i(tag, "" + time.hour);
			Log.i(tag, "" + time.minute);
			Log.i(tag, "" + time.second);
			Log.i(tag, "" + (time.weekDay + 1));
			answer = new byte[10];
			answer[0] = (byte) 0xff;
			answer[1] = (byte) 0x03;
			answer[2] = (byte) 0x07;
			answer[3] = (byte) (time.year - 2000);
			answer[4] = (byte) (time.month + 1);
			answer[5] = (byte) (time.monthDay);
			answer[6] = (byte) (time.hour);
			answer[7] = (byte) (time.minute);
			answer[8] = (byte) (time.second);
			answer[9] = (byte) (time.weekDay);
			break;
		case 2:
			answer = new byte[] { (byte) 0xff, 0x02, 0x03, mBatteryPercent,
					mSignalStrength, 2 };
			makeNoise();
			Intent intent = new Intent (this,WarnActivity.class);
			intent.setFlags(intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			break;
		case 3:
			answer = new byte[] { (byte) 0xff, 0x02, 0x03, mBatteryPercent,
					mSignalStrength, 3 };
			endCall();
			break;
		case 4:
			byte[] part1 = new byte[] { (byte) 0xff, 0x04, 0x00 };
			byte[] part2 = mIncomingNumber.getBytes(Charset.forName("GBK"));
			if (part2.length > 20) {
				byte[] temp = new byte[20];
				System.arraycopy(part2, 0, temp, 0, 20);
				part2 = temp;
			}

			byte[] part3 = getNameByPhoneNumber(mIncomingNumber).getBytes(
					Charset.forName("GBK"));
			if (part3.length > 12) {
				byte[] temp = new byte[12];
				System.arraycopy(part3, 0, temp, 0, 12);
				part3 = temp;
			}

			part1[2] = (byte) part2.length;
			answer = new byte[part1.length + part2.length + 1 + part3.length
					+ 1];

			System.arraycopy(part1, 0, answer, 0, part1.length);
			System.arraycopy(part2, 0, answer, part1.length, part2.length);
			System.arraycopy(new byte[] { 0x00 }, 0, answer, part1.length
					+ part2.length, 1);
			System.arraycopy(part3, 0, answer, part1.length + part2.length + 1,
					part3.length);
			System.arraycopy(new byte[] { 0x00 }, 0, answer, part1.length
					+ part2.length + 1 + part3.length, 1);
			break;
		}

		return answer;
	}

	private void endCall() {
		TelephonyManager telMag = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		Class<TelephonyManager> c = TelephonyManager.class;
		Method mthEndCall = null;
		try {
			mthEndCall = c.getDeclaredMethod("getITelephony", (Class[]) null);
			mthEndCall.setAccessible(true);
			ITelephony iTel = (ITelephony) mthEndCall.invoke(telMag,
					(Object[]) null);
			iTel.endCall();
		} catch (Exception e) {
			e.printStackTrace();
			Log.e("iTel", e.toString());
		}
	}

	public String getNameByPhoneNumber(String incomingNumber) {
		String[] projection = { ContactsContract.PhoneLookup.DISPLAY_NAME,
				ContactsContract.CommonDataKinds.Phone.NUMBER };

		Cursor cursor = this.getContentResolver().query(
				ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				projection, // Which columns to return.
				ContactsContract.CommonDataKinds.Phone.NUMBER + " = '"
						+ incomingNumber + "'", // WHERE clause.
				null, // WHERE clause value substitution
				null); // Sort order.

		Log.i(tag, "getPeople cursor.getCount() = " + cursor.getCount());
		if (cursor.getCount() == 0) {
			cursor.close();
			return getString(R.string.unknown_imcomingnumber);
		}
		cursor.moveToPosition(0);
		int nameFieldColumnIndex = cursor
				.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
		String name = cursor.getString(nameFieldColumnIndex);
		Log.i("Contacts", "" + name);
		cursor.close();
		return name;
	}

}
