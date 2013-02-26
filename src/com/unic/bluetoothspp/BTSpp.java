package com.unic.bluetoothspp;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class BTSpp extends Activity {
	ToggleButton bt_switcher;
	ListView devList;
	TextView warning;
	View bt_content;
	
	private Ringtone mNoiser;
	
	
	ArrayList<String> devices = new ArrayList<String>();
	ArrayAdapter<String> devAdapter;
	
	
	

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);        
        
        warning = (TextView) findViewById(R.id.warning);
        bt_content = findViewById(R.id.bt_content);        
        bt_switcher = (ToggleButton) findViewById(R.id.bluetoothswitch);
        

        
        devAdapter = new DeviceAdapter(this, R.layout.list_item,
        		R.id.textView1, devices);
        devList = (ListView) findViewById(R.id.btlist);
        devList.setAdapter(devAdapter);		
        
        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, bluetoothFilter);
        
        Intent intent = new Intent(BTSpp.this,BTService.class);
        startService(intent);
        
        doBindService();
    }
    
    
//    public PlayerInterface mPlayerInterface = null;
//    public void setPlayerInterface(PlayerInterface playerInterface) {
//		this.mPlayerInterface = playerInterface;
//	}
//
//	public PlayerInterface getPlayerInterface() {
//		return mPlayerInterface;
//	}
    
    /*
	 * 服务连接
	 */
    public BTService mBTService = null;
    private boolean mIsBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {
         public void onServiceConnected(ComponentName className, IBinder service) {
        	 mBTService = ((BTService.LocalBinder)service).getService();
             
         }

         public void onServiceDisconnected(ComponentName className) {
        	 mBTService = null;
         }
     };
	
     
     void doBindService() {
         bindService(new Intent(BTSpp.this, 
        		 BTService.class), mConnection, Context.BIND_AUTO_CREATE);
         mIsBound = true;
     }
     
     void doUnbindService() {
         if (mIsBound) {
             unbindService(mConnection);
             mIsBound = false;
         }
     }

    @Override
    protected void onResume() {   
    	if (mBTService != null) refreshUI(mBTService.btAdapt.isEnabled());
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
		super.onDestroy();
		unregisterReceiver(bluetoothReceiver);
		doUnbindService();
	    
	}

	private void makeNoise() {	    
	    if (mNoiser == null) mNoiser = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_RING,
                am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        mNoiser.play();
	}
	
//    @Override
//    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
//            if(mNoiser != null && mNoiser.isPlaying()) {
//                mNoiser.stop();
//                return true;
//            }
//        }
//        
//        if(keyCode == KeyEvent.KEYCODE_BACK) {
//            return true;
//        }
//        return super.onKeyUp(keyCode, event);
//    }    
    
    
    
    

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.discover:
        	if (mBTService != null) {
        		if(mBTService.btAdapt.isDiscovering()) mBTService.btAdapt.cancelDiscovery();
        		mBTService.btAdapt.startDiscovery();
        	}
            
            break;
        case R.id.bluetoothswitch:
            if (mBTService != null && mBTService.btAdapt.isEnabled()) {
            	mBTService.btAdapt.disable();
            } else {
                Intent intent = new Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(intent);
            }
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
            	if (mBTService != null){
            		mBTService.disconnect(true);
            	}
                
                                
            } else {
                Log.i("unic", "tag:" + ((View) v.getParent()).getTag());
                String devAddr = ((String)((View) v.getParent()).getTag()).split("/-/")[1]; 
                if (mBTService != null){
                	mBTService.connect(devAddr);
                }
                
                
            }
            
            break;
        case R.id.quitbtn:
            finish();
            break;            
        default: 
            break;
        }            
    }
    
//    private void makeNoise() {	    
//	    if (mNoiser == null) mNoiser = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
//        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        am.setStreamVolume(AudioManager.STREAM_RING,
//                am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
//        mNoiser.play();
//	}
    
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
    
	
	
}