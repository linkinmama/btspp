package com.unic.bluetoothspp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.internal.telephony.ITelephony;

public class BTSpp extends Activity {
	static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
	static final UUID uuid = UUID.fromString(SPP_UUID);
	static final String tag = "BluetoothSPP";
	static final boolean D = true;
	Button discover, send, clear;
	ToggleButton bt_switcher;
	EditText sendEdit, msgEdit;
	ListView devList;
	TextView warning;
	View bt_content;
	ArrayList<String> devices = new ArrayList<String>();
	ArrayAdapter<String> devAdapter;
	
	BluetoothAdapter btAdapt;
	BluetoothSocket btSocket;
	InputStream btIn = null;
	OutputStream btOut = null;
	
	SppServer sppServer;
	
	boolean sppConnected = false;
	boolean goBackward = false;
	
	private TelephonyManager mTelephonyManager;
	private byte mSignalStrength = -1;
	private String mIncomingNumber = "";
	private byte mBatteryPercent = -1;
	
	private BatteryReceiver mBatteryReceiver;
	
	private MediaPlayer mNoiser;
	

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        ButtonClick bc = new ButtonClick();
        
        warning = (TextView) findViewById(R.id.warning);
        bt_content = findViewById(R.id.bt_content);
        
        
        bt_switcher = (ToggleButton) findViewById(R.id.bluetoothswitch);
        bt_switcher.setOnClickListener(bc);
        
        discover = (Button) findViewById(R.id.discover);
        discover.setOnClickListener(bc);
        
        send = (Button) findViewById(R.id.sendbtn);
        send.setOnClickListener(bc);
        
        clear = (Button) findViewById(R.id.clear);
        clear.setOnClickListener(bc);
        
        msgEdit = (EditText) findViewById(R.id.msgedit);
        sendEdit = (EditText) findViewById(R.id.sendedit);        
        
        // 用BroadcastReceiver来取得搜索结果
        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothDevice.ACTION_FOUND);
        intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, intent);
        
        btAdapt = BluetoothAdapter.getDefaultAdapter();
        devAdapter = new ArrayAdapter<String>(this, 
        		android.R.layout.simple_list_item_1, devices);
        devList = (ListView) findViewById(R.id.btlist);
        devList.setAdapter(devAdapter);		
		devList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (sppConnected) return;
				btAdapt.cancelDiscovery();
				String devAddr = ((String)devices.get(position)).split("/-/")[1];
				try {
					//创建SPP 的 RfcommSocket, SPP从机
					btSocket = btAdapt.getRemoteDevice(devAddr)
								.createRfcommSocketToServiceRecord(uuid);
					btSocket.connect();

					synchronized (BTSpp.this) {
						if (sppConnected) return;
						if (btServerSocket != null) btServerSocket.close();
						btIn = btSocket.getInputStream();
						btOut = btSocket.getOutputStream();
						connected();
					}
				} catch (IOException e) {
					e.printStackTrace();
					sppConnected = false;
					try {
						btSocket.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					btSocket = null;
					Toast.makeText(BTSpp.this, "SPP connect error", Toast.LENGTH_SHORT).show();
				}
			}
		});		
		
		// SPP服务
		sppServer = new SppServer();
		sppServer.start();		
    }

    @Override
    protected void onResume() {        
        refreshUI(btAdapt.isEnabled());
        super.onResume();
    }
    
    private void refreshUI(boolean isBTEnabled) {
        bt_switcher.setChecked(isBTEnabled);
        if (isBTEnabled) {
            bt_content.setVisibility(View.VISIBLE);
            warning.setVisibility(View.GONE);
        } else {
            bt_content.setVisibility(View.GONE);
            warning.setVisibility(View.VISIBLE);            
        }        
    }

//    @Override
//    public void onBackPressed() {
//        Builder builder = new Builder(this);
//        builder.setMessage("exit or backward?")
//                .setPositiveButton("exit", mDialogListener)
//                .setNegativeButton("cancel", mDialogListener)
//                .setNeutralButton("backward", mDialogListener)
//                .show();
//    }
//    
//    private DialogInterface.OnClickListener mDialogListener = new DialogInterface.OnClickListener() {
//        
//        @Override
//        public void onClick(DialogInterface dialog, int which) {
//            switch (which) {
//                case DialogInterface.BUTTON_POSITIVE:                    
//                    finish();
//                    break;
//                case DialogInterface.BUTTON_NEUTRAL:
//                    goBackward = true;
//                    finish();
//                    break;
//                case DialogInterface.BUTTON_NEGATIVE:
//                    dialog.dismiss();
//                    break;                    
//                default:
//                    break;
//            }
//            
//        }
//    };

	@Override
	protected void onDestroy() {
//	    if (goBackward) {
//	        super.onDestroy();
//	        return;
//	    }
		if (sppServer != null)
			sppServer.cancel();
	    this.unregisterReceiver(bluetoothReceiver);
	    if (btIn != null) {
	    	try {
	    	    if (btSocket != null) btSocket.close();
	    		if (btServerSocket != null) btServerSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	    }
	    super.onDestroy();
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	
	Handler btHandler = new Handler() {
		@Override
		public void handleMessage(Message m) {
		    switch (m.what) {
                case 0:
                    msgEdit.append(msg + "\n");                    
                    break;
                case 1:
                    mTelephonyManager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_NONE);
                    unregisterReceiver(mBatteryReceiver);                    
                    break;
                default:
                    break;
            }
		}
	};
	

    class ButtonClick implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.discover:
				btAdapt.cancelDiscovery();
				btAdapt.startDiscovery();
				break;
//			case BTN_DISCOVERABLE:
//				Intent discoverableIntent = new Intent(
//						BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//				discoverableIntent.putExtra(
//						BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 100);
//				startActivity(discoverableIntent);
//				break;
			case R.id.bluetoothswitch:
				if (btAdapt.isEnabled()) {
					btAdapt.disable();
				} else {
					Intent intent = new Intent(
							BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivity(intent);
				}
				break;
			case R.id.sendbtn:
				try {
					btOut.write(sendEdit.getText().toString().getBytes());
					sendEdit.setText("");
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case R.id.clear:
				msgEdit.setText("");
				break;
			default: break;
			}
		}
    	
    }
    
    
	private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Bundle b = intent.getExtras();
			Object[] lstName = b.keySet().toArray();

			// 显示所有收到的消息及其细节
			for (int i = 0; i < lstName.length; i++) {
				String keyName = lstName[i].toString();
				Log.i(keyName, String.valueOf(b.get(keyName)));
			}
			//搜索设备时，取得设备的MAC地址
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String str = device.getName() + "/-/" + device.getAddress();
				if (devices.indexOf(str) == -1)// 防止重复添加
					devices.add(str); // 获取设备名称和mac地址
				devAdapter.notifyDataSetChanged();
			} else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
			    switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                    case BluetoothAdapter.STATE_OFF:
                        bt_switcher.setEnabled(true);
                        refreshUI(false);                        
                        break;
                    case BluetoothAdapter.STATE_ON:
                        bt_switcher.setEnabled(true);
                        refreshUI(true);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        bt_switcher.setEnabled(false);                        
                        break;                        
                    default:
                        break;
                }
			}
		}
	};
	
	private void connected() {
		sppConnected = true;
		new SppReceiver(btIn).start();
		devList.setClickable(false);
		sppServer = null;
		
        if(mTelephonyManager == null){
        	mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        }
        mTelephonyManager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_CALL_STATE|PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        
        if(mBatteryReceiver == null) mBatteryReceiver = new BatteryReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryReceiver, filter);
	}

	private void disconnect() {
	    sppConnected = false;
		devList.setClickable(true);
		btIn = null;
		btOut = null;
		
		btHandler.sendEmptyMessage(1);
	}
	
	private byte[] response(byte[]data) {
		byte[] answer = new byte[]{};
		if (!"".equals(mIncomingNumber) && data[0] != 3) {
			try {
				answer = (mIncomingNumber + " " + getPeople(mIncomingNumber) + " ").getBytes("ASCII");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} else {
			switch (data[0]) {
			case 0:
				answer = new byte[]{mBatteryPercent, mSignalStrength, 0};
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
				answer = new byte[7];
				answer[0] = (byte) (time.year - 2000);
				answer[1] = (byte) (time.month + 1);
				answer[2] = (byte) (time.monthDay);
				answer[3] = (byte) (time.hour);
				answer[4] = (byte) (time.minute);
				answer[5] = (byte) (time.second);
				answer[6] = (byte) (time.weekDay + 1);
				break;
			case 2:
				answer = new byte[]{mBatteryPercent, mSignalStrength, 0};
				makeNoise();
				break;
			case 3:
				answer = new byte[]{mBatteryPercent, mSignalStrength, 0};
				endCall();
				break;			
			default:
				break;
			}			
		}
		
		return answer;
	}
	
	private void makeNoise() {
		Log.i(tag, "need Noise!!!");
		if(mNoiser != null) return;
		mNoiser = MediaPlayer.create(this, R.raw.test_cbr);
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
		mNoiser.setLooping(false);
		mNoiser.start();
		mNoiser.setOnCompletionListener(new OnCompletionListener() {
			
			@Override
			public void onCompletion(MediaPlayer mp) {
				mNoiser.release();
				mNoiser = null;
			}
		});
		
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
	
    public String getPeople(String incomingNumber) {  
        String[] projection = { ContactsContract.PhoneLookup.DISPLAY_NAME,  
                                ContactsContract.CommonDataKinds.Phone.NUMBER};  
          
        Cursor cursor = this.getContentResolver().query(  
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,  
                projection,    // Which columns to return.  
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = '" + incomingNumber + "'", // WHERE clause.  
                null,          // WHERE clause value substitution  
                null);   // Sort order.  
  
        if( cursor == null ) {  
            Log.i(tag, "getPeople null");  
            return "";  
        }  
        Log.i(tag, "getPeople cursor.getCount() = " + cursor.getCount());  
        cursor.moveToPosition(0);          
        int nameFieldColumnIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);     
        String name = cursor.getString(nameFieldColumnIndex);
        Log.i("Contacts", "" + name);
        cursor.close();
        return name;
    }	
	
    private BluetoothServerSocket btServerSocket;
	private class SppServer extends Thread {
        public SppServer() {        	
        	try {
				btServerSocket = btAdapt.listenUsingRfcommWithServiceRecord("SPP", uuid);
			} catch (IOException e) {
				e.printStackTrace();
				btServerSocket = null;
			}			
        }
        
        public void run() {
        	BluetoothSocket bs = null;
        	if (btServerSocket == null) {
        		Log.e(tag, "ServerSocket null");
        		return;
        	}
        	
        	try {
				bs = btServerSocket.accept();
				synchronized (BTSpp.this) {
					if (sppConnected) return;
					Log.i(tag, "Devices Name: " + bs.getRemoteDevice().getName());
					btIn = bs.getInputStream();
					btOut = bs.getOutputStream();
					connected();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(tag, "ServerSoket accept failed");
			}
			if (D) Log.i(tag, "End Bluetooth SPP Server");
        }
        
        public void cancel() {
        	if (btServerSocket == null)
        		return;
        	try {
				btServerSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(tag, "close ServerSocket failed");
			}
        }
        
	}
	
	private class SppReceiver extends Thread {
		private InputStream input = null;
		public SppReceiver(InputStream in) {
			input = in;
			Log.i(tag, "SppReceiver ");
		}
		public void run() {
			byte[] data = new byte[1024];
			int length = 0;
			if (input == null) {
				Log.d(tag, "InputStream null");
				return;
			}
			while (true) {
				try {
					length = input.read(data);
					Log.i(tag, "SPP receiver");
					if (length > 0) {
						msg = new String(data, 0, length, "ASCII");
						Log.i(tag, "data=" + data[0]);
						Log.i(tag, "length=" + length);
						byte[] answer = response(data);
						btOut.write(answer);
						btHandler.sendEmptyMessage(0);
					}
				} catch (IOException e) {
					Log.e(tag, "disconnect");
					disconnect();
					return;
				}
			}
		}
	}
	
	private String msg = "";
	
	private class MyPhoneStateListener extends PhoneStateListener {

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
				mIncomingNumber = incomingNumber;
				Log.i("unic", "ringing-" + mIncomingNumber);
				getPeople(incomingNumber);
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				mIncomingNumber = "";
				Log.i("unic", "offhook-" + mIncomingNumber);
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				mIncomingNumber = "";
				Log.i("unic", "idle-" + mIncomingNumber);
				break;
			default:
				break;
			}
			super.onCallStateChanged(state, incomingNumber);
		}

		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			mSignalStrength = (byte) signalStrength.getGsmSignalStrength();
			Log.i(tag, "signal strength:" + mSignalStrength);
			super.onSignalStrengthsChanged(signalStrength);
		}
	}
	
	
	private class BatteryReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			int current = intent.getExtras().getInt("level");// 获得当前电量
			int total = intent.getExtras().getInt("scale");// 获得总电量
			int percent = current * 100 / total;
			mBatteryPercent = (byte) percent;
			Log.i(tag, "battery:" + mBatteryPercent);
		}
	}
}