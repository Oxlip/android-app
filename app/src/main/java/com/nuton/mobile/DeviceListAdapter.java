package com.nuton.mobile;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.makeramen.RoundedImageView;

import java.util.List;

/**
 * List Adapter to hold discovered devices and render them in UI.
 * This is a singleton class(because only one list is enough for the whole application).
 */
public class DeviceListAdapter extends BaseAdapter {

    private Context mContext;
    private List<Device> mDeviceList;
    private static DeviceListAdapter mInstance = null;

    private static final String LOG_TAG_DEVICE_LIST_ADAPTER = "DeviceListAdapter";

    protected DeviceListAdapter() {
        // Exists only to defeat instantiation.
    }

    /**
     * Returns the current instance.
     * @return DeviceListAdapter instance.
     */
    public static DeviceListAdapter getInstance() {
        return mInstance;
    }

    /**
     * Creates new instance if required.
     * @param context
     * @param deviceList
     * @return DeviceListAdapter instance.
     */
    public static DeviceListAdapter getInstance(Context context, List<Device> deviceList) {
        if(mInstance == null) {
            mInstance = new DeviceListAdapter();
            mInstance.mContext = context;
            mInstance.mDeviceList = deviceList;
        }
        return mInstance;
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
        for (int i = 0; i < mDeviceList.size(); i++) {
            Device device = mDeviceList.get(i);
            if (device.getDeviceInfo().address.equals(bleDevice.getAddress())) {
                return device;
            }
        }
        return null;
    }

    /**
     * Invalidates BLE connection state of all the devices in this list.
     */
    protected void invalidateConnectionState() {
        for (int i = 0; i < mDeviceList.size(); i++) {
            Device device = mDeviceList.get(i);
            device.setRssi(0);
        }
        this.notifyDataSetInvalidated();
    }

    /**
     * Add newly discovered device.
     * @param device - New device.
     */
    public void addDevice(Device device) {
        mDeviceList.add(device);
        this.notifyDataSetInvalidated();
    }

    @Override
    public int getCount() {
        return mDeviceList.size();
    }

    @Override
    public Object getItem(int position) {
        return mDeviceList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mDeviceList.indexOf(getItem(position));
    }

    private void loadApplianceImage(View rootView, String applianceTypeName) {
        Context context = ApplicationGlobals.getAppContext();
        DatabaseHelper.ApplianceType applianceType = DatabaseHelper.getApplianceTypeByName(applianceTypeName);
        if (applianceType == null) {
            return;
        }

        int imgId =  context.getResources().getIdentifier(applianceType.imageName, "drawable", context.getPackageName());
        Drawable imgDrawable = context.getResources().getDrawable(imgId);
        imgDrawable.mutate().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);

        RoundedImageView riv = (RoundedImageView) rootView.findViewById(R.id.dl_image);
        riv.setImageDrawable(imgDrawable);
        riv.setBackgroundColor(Color.GRAY);
        riv.setBorderColor(Color.DKGRAY);
    }

    /**
     * Renders the UI for the given device item.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imgIcon;
        TextView txtTitle;
        ToggleButton btnOn;
        Button btnConnect;
        Device device;

        device = mDeviceList.get(position);

        if (convertView == null) {
            LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = mInflater.inflate(R.layout.item_device_list, null);
        }

        loadApplianceImage(convertView, device.getDeviceInfo().applianceType);

        txtTitle = (TextView) convertView.findViewById(R.id.dl_name);
        btnOn = (ToggleButton)convertView.findViewById(R.id.dl_btn_on_off);
        btnConnect = (Button)convertView.findViewById(R.id.dl_btn_connect);

        /* Store the device in the buttons so that it can be retrieved when the button is clicked*/
        btnOn.setTag(device);
        btnConnect.setTag(device);

        /* Button Click Handler for on/off button*/
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

        /* Button Click Handler for connect button*/
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Device device = (Device)v.getTag();
                device.save();
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

        /* Enable the on button only if the device is in range */
        btnOn.setEnabled(device.getRssi() != 0);

        txtTitle.setText(device.getDeviceInfo().name);

        return convertView;
    }
}
