package com.getastral.astralmobile;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.List;

// Adapter for holding devices.
public class DeviceListAdapter extends BaseAdapter {

    private Context context;
    private List<Device> deviceList;

    // Make this class singleton
    private static DeviceListAdapter instance = null;
    protected DeviceListAdapter() {
        // Exists only to defeat instantiation.
    }
    // Get the current instance
    public static DeviceListAdapter getInstance() {
        return instance;
    }
    // Get the current instance, create new one if required.
    public static DeviceListAdapter getInstance(Context context, List<Device> deviceList) {
        if(instance == null) {
            instance = new DeviceListAdapter();
            instance.context = context;
            instance.deviceList = deviceList;
        }
        return instance;
    }

    /**
     * Associate the given ble device with matching device uuid.
     *
     * @param bleDevice Bluetooth device to be associated.
     * @param rssi Received signal strength
     * @return true if a device with given uuid is found and bleDevice is associated.
     */
    protected boolean associateBleDevice(BluetoothDevice bleDevice, int rssi) {
        Device device = getDevice(bleDevice);
        if (device == null) {
            return false;
        }
        device.setBleDevice(bleDevice);
        if (device.getRssi() != rssi) {
            device.setRssi(rssi);
            this.notifyDataSetInvalidated();
        }
        return true;
    }

    /**
     * Finds the Device from a given bluetooth Device.
     *
     * @param bleDevice Bluetooth device to search
     * @return Device associated with the given BLE device.
     */
    protected Device getDevice(BluetoothDevice bleDevice) {
        for (int i = 0; i < deviceList.size(); i++) {
            Device device = deviceList.get(i);
            if (device.getBleMacAddress().equals(bleDevice.getAddress())) {
                return device;
            }
        }
        return null;
    }

    /**
     * Invalidates BLE connection state of all the devices in this list.
     */
    protected void invalidateConnectionState() {
        for (int i = 0; i < deviceList.size(); i++) {
            Device device = deviceList.get(i);
            device.setRssi(0);
        }
        this.notifyDataSetInvalidated();
    }

    /**
     * Add newly discovered device.
     * @param device - New device.
     */
    public void addDevice(Device device) {
        deviceList.add(device);
        this.notifyDataSetInvalidated();
    }

    @Override
    public int getCount() {

        return deviceList.size();
    }

    @Override
    public Object getItem(int position) {

        return deviceList.get(position);
    }

    @Override
    public long getItemId(int position) {

        return deviceList.indexOf(getItem(position));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        Device device = deviceList.get(position);

        if (convertView == null) {
            LayoutInflater mInflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = mInflater.inflate(R.layout.item_device_list, null);
        }

        ImageView imgIcon = (ImageView) convertView.findViewById(R.id.dl_image);
        TextView txtTitle = (TextView) convertView.findViewById(R.id.dl_name);
        ToggleButton btnOn = (ToggleButton)convertView.findViewById(R.id.dl_btn_on_off);
        Button btnConnect = (Button)convertView.findViewById(R.id.dl_btn_connect);

            /* Store the device in the buttons so that it can be retrieved when the button is clicked*/
        btnOn.setTag(device);
        btnConnect.setTag(device);

        btnOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToggleButton btnOn = (ToggleButton)v;
                Device device = (Device)v.getTag();
                byte brightness;

                if (!btnOn.isChecked()) {
                    brightness = 0;
                } else {
                    brightness = 100;
                }
                device.dimmerControl(brightness);
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Device device = (Device)v.getTag();
                DatabaseHandler db = new DatabaseHandler(v.getContext());
                db.connectDevice(device);
                device.setRegistered();
                notifyDataSetInvalidated();
            }
        });

            /* "connect" button should be visible only for the first time.
             *  "On/Off" button should be visible only if the device is connected.
             */
        if (device.isRegistered()) {
            btnOn.setVisibility(View.VISIBLE);
            btnConnect.setVisibility(View.INVISIBLE);
        } else {
            btnOn.setVisibility(View.INVISIBLE);
            btnConnect.setVisibility(View.VISIBLE);
        }
        btnOn.setEnabled(device.getRssi() != 0);

        // setting the image resource and title
        imgIcon.setImageResource(R.drawable.ic_launcher);
        txtTitle.setText(device.getName());

        return convertView;
    }
}
