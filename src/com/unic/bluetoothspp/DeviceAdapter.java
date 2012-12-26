package com.unic.bluetoothspp;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.List;

public class DeviceAdapter extends ArrayAdapter<String> {
    List<String> mDevices;    
    
    public DeviceAdapter(Context context, int resource, int textViewResourceId, List<String> objects) {
        super(context, resource, textViewResourceId, objects);
        mDevices = objects;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        v.setTag(mDevices.get(position));
        return v;
    }

}
