package com.getastral.astralmobile;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.AsyncTask;
import android.os.ConditionVariable;
import android.util.Log;

import java.util.UUID;

public class Device {
    /** Device specific information populated from database.*/
    private String mName;
    private String mApplianceType;
    private String mApplianceMake;
    private String mApplianceModel;

    /** Memory variables which are not stored in database */
    private int mRssi;

    /** Set to true if the device is stored in database. */
    private boolean isSaved;

    /** BLE MAC address of the device (unique ID).*/
    private String mBleMacAddress;

    /** Bluetooth adapter used for scanning and connecting.*/
    private final BluetoothAdapter mBluetoothAdapter;

    /** Application context. */
    private final Context mContext;

    /* Device object for the given mBleMacAddress(it need not to be in the range) */
    private BluetoothDevice mBleDevice;
    private BluetoothGatt mBleGatt;

    /* Condition which would be open(trigger) only when BLE connection is opened and GATT services are discovered. */
    private final ConditionVariable mBleServicesDiscovered;
    private final ConditionVariable mBleCharacteristicWritten;

    private static final int BLE_GATT_SERVICE_DISCOVER_TIMEOUT = 5000;
    private static final int BLE_GATT_WRITE_TIMEOUT = 3000;

    /** The following UUIDs should in sync with firmware. */
    private static final UUID ASTRAL_UUID_BASE = UUID.fromString("c0f41000-9324-4085-aba0-0902c0e8950a");
    private static final UUID ASTRAL_UUID_INFO = UUID.fromString("c0f41001-9324-4085-aba0-0902c0e8950a");
    private static final UUID ASTRAL_UUID_OUTLET = UUID.fromString("c0f41002-9324-4085-aba0-0902c0e8950a");

    private static final String LOG_TAG_DEVICE = "Device";

    /**
     * Construct a new device.
     * @param bluetoothAdapter Bluetooth Adapter to be used when scanning, writing to the BLE device.
     * @param context Application context for opening the database.
     */
    public Device(BluetoothAdapter bluetoothAdapter, Context context) {
        mBleServicesDiscovered = new ConditionVariable();
        mBleCharacteristicWritten = new ConditionVariable();
        mBluetoothAdapter = bluetoothAdapter;
        mContext = context;
    }

    public String getBleMacAddress() {
        return mBleMacAddress;
    }

    public void setBleMacAddress(String _ble_mac_address) {
        this.mBleMacAddress = _ble_mac_address;
    }

    public String getName() {
        return mName;
    }

    public void setName(String _name) {
        this.mName = _name;
    }

    public String getApplianceType() {
        return mApplianceType;
    }

    public void setApplianceType(String _appliance_type) {
        this.mApplianceType = _appliance_type;
    }

    public String getApplianceMake() {
        return mApplianceMake;
    }

    public void setApplianceMake(String _appliance_make) {
        this.mApplianceMake = _appliance_make;
    }

    public String getApplianceModel() {
        return mApplianceModel;
    }

    public void setApplianceModel(String _appliance_model) {
        this.mApplianceModel = _appliance_model;
    }

    public int getRssi() {
        return this.mRssi;
    }

    public void setRssi(int _rssi) {
        this.mRssi = _rssi;
    }

    /**
     * Saves the device into database.
     */
    public void save() {
        DatabaseHelper db = new DatabaseHelper(this.mContext);
        db.saveDevice(this);
        this.isSaved = true;
    }

    /**
     * Deletes the given device from database.
     */
    public void delete() {
        DatabaseHelper db = new DatabaseHelper(this.mContext);
        db.removeDevice(this);
        this.isSaved = false;
    }

    /**
     * Returns device storage state.
     * @return True if device is saved.
     */
    public boolean isRegistered() {
        return this.isSaved;
    }

    public void setBleDevice(BluetoothDevice _ble_device) {
        this.mBleMacAddress = _ble_device.getAddress();
        this.mBleDevice = _ble_device;
    }

    private BluetoothGatt getBleGatt() {
        return this.mBleGatt;
    }

    private void setBleGatt(BluetoothGatt _ble_gatt) {
        this.mBleGatt = _ble_gatt;
    }

    /**
     * Set BLE GATT service discovery operation status.
     *
     * @param discovered - True if services were discovered.
     *                     False if discovery is still going on.
     */
    private void setBleGattServicesDiscovered(boolean discovered) {
        if (discovered) {
            this.mBleServicesDiscovered.open();
        } else {
            this.mBleServicesDiscovered.close();
        }
    }

    /**
     * Wait for BLE service discovery to happen for this device.
     *
     * @return True if BLE service discovery happened.
     *         False if timed out.
     */
    private boolean waitForBleServiceDiscovery() {
        boolean result;

        result = this.mBleServicesDiscovered.block(BLE_GATT_SERVICE_DISCOVER_TIMEOUT);
        if (!result) {
            Log.e("BLE", "Connection timed out while discovering BLE services.");
        }
        return result;
    }

    /**
     * Set BLE Characteristic Write operation status.
     *
     * @param completed - True if operation was completed.
     *                    False if operation is still going on.
     */
    private void setBleCharacteristicWriteCompleted(boolean completed) {
        if (completed) {
            this.mBleCharacteristicWritten.open();
        } else {
            this.mBleCharacteristicWritten.close();
        }
    }

    /**
     * Wait for BLE write characteristic to complete.
     *
     * @return True if BLE write completed(either successfully or failed).
     *         False if timed out.
     */
    private boolean waitForBleCharacteristicWriteComplete() {
        boolean result;
        result = this.mBleCharacteristicWritten.block(BLE_GATT_WRITE_TIMEOUT);
        if (!result) {
            Log.e(LOG_TAG_DEVICE, "BLE characteristic write timed out");
        }
        return result;
    }


    /**
     * Disconnect the BLE device.
     */
    private void bleDisconnect() {
        BluetoothGatt bleGatt;

        bleGatt = getBleGatt();
        setBleGattServicesDiscovered(false);
        bleGatt.disconnect();
        setBleGatt(null);
    }

    /**
     * Establishes a BLE connection to the given device.
     *
     * @param device - Device needs to be connected.
     * @return BluetoothGatt object associated with the connection.
     */
    private BluetoothGatt bleConnect(Device device, BluetoothAdapter bluetoothAdapter, Context context) {
        BluetoothGatt bleGatt;

        bleGatt = device.getBleGatt();
        if (bleGatt != null) {
            device.waitForBleServiceDiscovery();
            return bleGatt;
        }
        if (mBleDevice == null) {
            mBleDevice = bluetoothAdapter.getRemoteDevice(this.mBleMacAddress);
        }
        bleGatt = mBleDevice.connectGatt(context , true, mGattCallback);
        device.setBleGatt(bleGatt);
        device.waitForBleServiceDiscovery();

        return bleGatt;
    }

    /**
     * Sets the brightness of the device if applicable.
     * @param brightness Percentage of brightness. (0-Off, 100-Fully on)
     */
    public void dimmerControl(byte brightness) {
        byte[] value = {1, brightness};
        writeBleCharacteristic(ASTRAL_UUID_BASE, ASTRAL_UUID_OUTLET, value);
    }

    /**
     *  Writes the given bytes to given BLE device's characteristic.
     *
     * @param serviceId Bluetooth GATT Service UUID where the Characteristic can be found.
     * @param characteristicId Bluetooth GATT Characteristic UUID.
     * @param value Value to be written to the Characteristic.
     */
    private void writeBleCharacteristic(UUID serviceId, UUID characteristicId,  byte[] value) {
        WriteBleCharacteristicTaskParam p = new WriteBleCharacteristicTaskParam(this, mBluetoothAdapter, mContext, serviceId, characteristicId, value);
        new WriteBleCharacteristicTask().execute(p);
    }

    /**
     * Parameters for WriteBleCharacteristicTask
     */
    private class WriteBleCharacteristicTaskParam {
        final Device device;
        final UUID serviceId;
        final UUID characteristicId;
        final byte[] value;
        final BluetoothAdapter bluetoothAdapter;
        final Context context;
        public WriteBleCharacteristicTaskParam(Device device, BluetoothAdapter bluetoothAdapter,
                                               Context context,  UUID serviceId, UUID characteristicId,  byte[] value) {
            this.device = device;
            this.bluetoothAdapter = bluetoothAdapter;
            this.context = context;
            this.serviceId = serviceId;
            this.characteristicId = characteristicId;
            this.value = value;
        }
    }

    /**
     * AsyncTask to write to BLE Characteristic.
     *
     * All BLE operations are async, so handling it in UI thread would create a unpleasant experience.
     * Instead we create a new thread for each BLE write which would execute asynchronously and update
     * the UI if needed.
     */
    private class WriteBleCharacteristicTask extends AsyncTask<WriteBleCharacteristicTaskParam, Integer, Long> {
        private Long writeCharacteristic(Context context, BluetoothAdapter bluetoothAdapter,
                                         Device device, UUID serviceId, UUID characteristicId,  byte[] value){
            BluetoothGatt gatt;
            BluetoothGattService service;
            BluetoothGattCharacteristic characteristic;

            gatt = bleConnect(device, bluetoothAdapter, context);
            service = gatt.getService(serviceId);
            if (service == null) {
                Log.e(LOG_TAG_DEVICE, "BLE service not found " + serviceId );
                return 0L;
            }

            characteristic = service.getCharacteristic(characteristicId);
            if (characteristic == null) {
                Log.e(LOG_TAG_DEVICE, "BLE characteristic not found " + characteristicId);
                return 0L;
            }
            characteristic.setValue(value);
            device.setBleCharacteristicWriteCompleted(false);
            gatt.writeCharacteristic(characteristic);
            device.waitForBleCharacteristicWriteComplete();

            device.bleDisconnect();

            return 0L;
        }

        protected Long doInBackground(WriteBleCharacteristicTaskParam... params) {
            // total number of BLE write requests(currently we support only one).
            int count = params.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                WriteBleCharacteristicTaskParam param = params[i];
                publishProgress((int) ((i / (float) count) * 100));
                // Escape early if cancel() is called
                if (isCancelled()) {
                    break;
                }

                totalSize += writeCharacteristic(param.context, param.bluetoothAdapter, param.device, param.serviceId, param.characteristicId, param.value);
            }
            return totalSize;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(Long result) {
        }
    }

    /**
     * BLE Async Callback functions.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.v(LOG_TAG_DEVICE, "onCharacteristicChanged ( characteristic : " + characteristic + ")");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.v(LOG_TAG_DEVICE, "onCharacteristicRead ( characteristic :" + characteristic + " ,status, : " + status + ")");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            Log.v(LOG_TAG_DEVICE, "onCharacteristicWrite ( characteristic :" + characteristic + " ,status, : " + status + ")");

            DeviceListAdapter deviceListAdapter = DeviceListAdapter.getInstance();
            if (deviceListAdapter == null) {
                return;
            }
            Device device = deviceListAdapter.getDevice(gatt.getDevice());
            if (device != null){
                device.setBleCharacteristicWriteCompleted(true);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BluetoothDevice device = gatt.getDevice();

            Log.v(LOG_TAG_DEVICE, "onConnectionStateChange (device : " + device + ", status : " + status + " , newState :  " + newState + ")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Automatically discover service once BLE connection is established
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    //gatt.close();
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor device, int status) {
            Log.v(LOG_TAG_DEVICE, "onDescriptorRead (device : " + device + " , status :  " + status + ")");
            super.onDescriptorRead(gatt, device, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor arg0, int status) {
            Log.v(LOG_TAG_DEVICE, "onDescriptorWrite (arg0 : " + arg0 + " , status :  " + status + ")");
            super.onDescriptorWrite(gatt, arg0, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.v(LOG_TAG_DEVICE, "onReliableWriteCompleted (gatt : " + gatt + " , status :  " + status + ")");
            super.onReliableWriteCompleted(gatt, status);
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            BluetoothDevice device = gatt.getDevice();

            Log.v(LOG_TAG_DEVICE, "onReadRemoteRssi (device : " + device + " , rssi :  " + rssi + " , status :  " + status + ")");

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.v(LOG_TAG_DEVICE, "onServicesDiscovered");
            DeviceListAdapter deviceListAdapter = DeviceListAdapter.getInstance();
            if (deviceListAdapter == null) {
                return;
            }
            Device device = deviceListAdapter.getDevice(gatt.getDevice());
            if (device != null){
                device.setBleGattServicesDiscovered(true);
            }
        }
    };
}
