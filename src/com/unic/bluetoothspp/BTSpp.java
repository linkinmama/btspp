package com.unic.bluetoothspp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
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
//	Button discover, send, clear;
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
	
	boolean sppConnected = false;
	boolean goBackward = false;
	
	private TelephonyManager mTelephonyManager;
	private byte mSignalStrength = -1;
	private String mIncomingNumber = "";
	private byte mBatteryPercent = -1;
	
	private BatteryReceiver mBatteryReceiver;
	
	private Ringtone mNoiser;
	private SppReceiver mSppReceiver;
	

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);        
        
        warning = (TextView) findViewById(R.id.warning);
        bt_content = findViewById(R.id.bt_content);        
        
        bt_switcher = (ToggleButton) findViewById(R.id.bluetoothswitch);
//        discover = (Button) findViewById(R.id.discover);        
//        send = (Button) findViewById(R.id.sendbtn);        
//        clear = (Button) findViewById(R.id.clear);
        
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
        devAdapter = new DeviceAdapter(this, R.layout.list_item,
        		R.id.textView1, devices);
        devList = (ListView) findViewById(R.id.btlist);
        devList.setAdapter(devAdapter);		
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
	    
	    this.unregisterReceiver(bluetoothReceiver);
	    if (btIn != null) {
	    	try {
	    	    if (btSocket != null) btSocket.close();
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
	
	private void connect() {
		sppConnected = true;
		mSppReceiver = new SppReceiver(btIn);
		mSppReceiver.start();
		
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
	    try {
            btOut.write(new byte[]{});
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//		try {
//            btIn.close();
//            btOut.close();
//            btSocket.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
		
		btHandler.sendEmptyMessage(1);
	}
	
    private byte[] response(int answerType) {
        byte[] answer = null;
        switch (answerType) {
            case 0:
                answer = new byte[] {
                        (byte) 0xff, 0x02, 0x03, mBatteryPercent, mSignalStrength, 0
                };
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
                answer[9] = (byte) (time.weekDay + 1);
                break;
            case 2:
                answer = new byte[] {
                        (byte) 0xff, 0x02, 0x03, mBatteryPercent, mSignalStrength, 0
                };
                makeNoise();
                break;
            case 3:
                answer = new byte[] {
                        (byte) 0xff, 0x02, 0x03, mBatteryPercent, mSignalStrength, 0
                };
                endCall();
                break;
            case 4:
                byte[] part1 = new byte[]{(byte) 0xff, 0x04, 0x00};
                byte[] part2 = (mIncomingNumber + " " + getPeople(mIncomingNumber) + " ").getBytes();
                part1[2] = (byte) part2.length;
                answer = new byte[3 + part2.length];
                System.arraycopy(part1, 0, answer, 0, part1.length);
                System.arraycopy(part2, 0, answer, part1.length, part2.length);
                break;
        }

        return answer;
    }
	
	private void makeNoise() {	    
	    if (mNoiser == null) mNoiser = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_RING,
                am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        mNoiser.play();
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
	
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if(mNoiser != null && mNoiser.isPlaying()) {
                mNoiser.stop();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }    

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.discover:
            btAdapt.cancelDiscovery();
            btAdapt.startDiscovery();
            break;
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
        case R.id.noise_stopper:
            if(mNoiser!=null && mNoiser.isPlaying()) {
                mNoiser.stop();
                mNoiser = null;
            }
            break;
        case R.id.toggle_bt_device:
            ToggleButton toggle = (ToggleButton) v;
            if(!toggle.isChecked()) {
                disconnect();
            } else {
                btAdapt.cancelDiscovery();
                if(btSocket != null) {
                    try {
                        btSocket.close();
                        btSocket = null;
                    } catch (IOException e) {
                        btSocket = null;
                        e.printStackTrace();
                    }
                }
                
                Log.i("unic", "tag:" + ((View) v.getParent()).getTag());
                String devAddr = ((String)((View) v.getParent()).getTag()).split("/-/")[1];
                try {
                    //创建SPP 的 RfcommSocket, SPP从机
                    btSocket = btAdapt.getRemoteDevice(devAddr)
                                .createRfcommSocketToServiceRecord(uuid);
                    btSocket.connect();

                    synchronized (BTSpp.this) {
                        if (sppConnected) return;
                        btIn = btSocket.getInputStream();
                        btOut = btSocket.getOutputStream();
                        connect();
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
            
            break;              
        default: break;
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
			while (sppConnected) {
			    Time t = new Time();
			    t.setToNow();
			    Log.i("unic","re" + t.second);
				try {
					length = input.read(data);
					Log.i(tag, "SPP receiver");
					if (length > 0) {
						msg = new String(data, 0, length, "ASCII");
						Log.i(tag, "data[0]=" + data[0]);
						Log.i(tag, "data=" + data.toString());
						Log.i(tag, "length=" + length);
//						if(data[0]-0xff==0) {
							byte[] answer = response(data[3]);
							btOut.write(answer);
							btHandler.sendEmptyMessage(0);
//						}
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
				
				if(btOut != null) {
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
				
                if(btOut != null) {
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
				
                if(btOut != null) {
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