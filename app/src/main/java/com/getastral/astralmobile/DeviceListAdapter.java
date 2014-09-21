package com.getastral.astralmobile;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.List;

public class DeviceListAdapter extends BaseAdapter {

    Context context;
    List<Device> rowItem;

    DeviceListAdapter(Context context, List<Device> rowItem) {
        this.context = context;
        this.rowItem = rowItem;
    }

    @Override
    public int getCount() {

        return rowItem.size();
    }

    @Override
    public Object getItem(int position) {

        return rowItem.get(position);
    }

    @Override
    public long getItemId(int position) {

        return rowItem.indexOf(getItem(position));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater mInflater = (LayoutInflater) context
                    .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = mInflater.inflate(R.layout.item_device_list, null);
        }

        ImageView imgIcon = (ImageView) convertView.findViewById(R.id.dl_image);
        TextView txtTitle = (TextView) convertView.findViewById(R.id.dl_name);
        ToggleButton btnOn = (ToggleButton)convertView.findViewById(R.id.dl_btn_on_off);
        btnOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToggleButton btnOn = (ToggleButton)v;
                if (!btnOn.isChecked()) {
                    Log.d("Button", "Sending BLE Turn off message");
                } else {
                    Log.d("Button", "Sending BLE Turn on message");
                }

            }
        });

        Device row_pos = rowItem.get(position);
        // setting the image resource and title
        imgIcon.setImageResource(R.drawable.ic_launcher);
        txtTitle.setText(row_pos.getName());

        return convertView;

    }

}
