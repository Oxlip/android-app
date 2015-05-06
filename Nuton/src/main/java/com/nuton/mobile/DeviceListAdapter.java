package com.nuton.mobile;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

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
     * @return DeviceListAdapter instance.
     */
    public static DeviceListAdapter getInstance(Context context) {
        if(mInstance == null) {
            mInstance = new DeviceListAdapter();
            mInstance.mContext = context;
            mInstance.mDeviceList = DatabaseHelper.getDevices();
        }
        return mInstance;
    }

    @Override
    public void notifyDataSetChanged() {
        mInstance.mDeviceList = DatabaseHelper.getDevices();
        super.notifyDataSetChanged();
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
        return getDevice(bleDevice.getAddress());
    }

    /**
     * Finds the Device from a given bluetooth Device.
     *
     * @param deviceAddress Bluetooth device to search
     * @return Device associated with the given BLE device.
     */
    protected Device getDevice(String deviceAddress) {
        for (int i = 0; i < mDeviceList.size(); i++) {
            Device device = mDeviceList.get(i);
            if (device.getDeviceInfo().address.equals(deviceAddress)) {
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

        RoundedImageView riv = (RoundedImageView) rootView.findViewById(R.id.dl_image);
        riv.setBackgroundColor(Color.GRAY);
        riv.setBorderColor(Color.DKGRAY);
        if (applianceType == null) {
            return;
        }

        int imgId =  context.getResources().getIdentifier(applianceType.imageName, "drawable", context.getPackageName());
        Drawable imgDrawable = context.getResources().getDrawable(imgId);
        imgDrawable.mutate().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);

        riv.setImageDrawable(imgDrawable);
    }

    public View loadNewDeviceView(Device device) {
        View view;
        Button btnConnect;

        LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        view = mInflater.inflate(R.layout.not_connected_list_item, null);

        btnConnect = (Button)view.findViewById(R.id.dl_btn_connect);
        btnConnect.setTag(device);
        /* Button Click Handler for connect button*/
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Device device = (Device) v.getTag();
                device.save();
                notifyDataSetInvalidated();
            }
        });

        return  view;
    }

    public View loadLyraView(Device device) {
        View view;

        LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        view = mInflater.inflate(R.layout.lyra_list_item, null);

        return  view;
    }

    public View loadAuraView(Device device) {
        SwitchCompat btnOn;
        View view;

        LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        view = mInflater.inflate(R.layout.aura_list_item, null);

        loadApplianceImage(view, device.getDeviceInfo().applianceType);

        btnOn = (SwitchCompat)view.findViewById(R.id.dl_btn_on_off);

        /* Store the device in the buttons so that it can be retrieved when the button is clicked*/
        btnOn.setTag(device);

        /* Button Click Handler for on/off button*/
        btnOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Device device = (Device) buttonView.getTag();
                device.dimmerControl((byte) (isChecked ? 100 : 0));
            }
        });

        /* Enable the on button only if the device is in range */
        btnOn.setEnabled(device.getRssi() != 0);

        return  view;
    }

    /**
     * Renders the UI for the given device item.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView txtTitle;
        Device device;

        device = mDeviceList.get(position);

        /* Load different view based on device type. */
        if (convertView == null) {
            if (device.isRegistered()) {
                if (device.getDeviceInfo().deviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA) {
                    convertView = loadAuraView(device);
                } else {
                    convertView = loadLyraView(device);
                }
            } else {
                convertView = loadNewDeviceView(device);
            }
        }

        txtTitle = (TextView) convertView.findViewById(R.id.dl_name);
        txtTitle.setText(device.getDeviceInfo().name);

        return convertView;
    }
}
