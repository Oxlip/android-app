package com.nuton.mobile;

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

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class Device {
    /** Device information such as name, address etc*/
    private DatabaseHelper.DeviceInfo mDeviceInfo;

    /** Memory variables which are not stored in database */
    private int mRssi;

    /** Set to true if the device is stored in database. */
    private boolean isSaved;

    /* Device object for the given mBleMacAddress(it need not to be in the range) */
    private BluetoothDevice mBleDevice;
    private BluetoothGatt mBleGatt;

    /* Condition which would be open(trigger) only when BLE connection is opened and GATT services are discovered. */
    private final ConditionVariable mBleServicesDiscovered;
    private final ConditionVariable mBleCharacteristicRwOperation;

    /* Firmware version of the device */
    private String firmwareVersion = null;

    private DatabaseHelper databaseHelper = null;

    public interface BleEventCallback {
        void onBleReadCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristicId);
    };
    private BleEventCallback bleEventCallback;

    private static final int BLE_GATT_SERVICE_DISCOVER_TIMEOUT = 5000;
    private static final int BLE_GATT_WRITE_TIMEOUT = 3000;

    /** The following UUIDs should in sync with firmware.
     * check nrf51-firmware/app/include/ble_uuids.h
     * */
    public static final UUID BLE_ASTRAL_UUID_BASE = UUID.fromString("c0f41000-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_DIMMER_SERVICE = UUID.fromString("c0f41001-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_CS_SERVICE = UUID.fromString("c0f41002-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_HS_SERVICE = UUID.fromString("c0f41003-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_LS_SERVICE = UUID.fromString("c0f41004-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_MS_SERVICE = UUID.fromString("c0f41005-9324-4085-aba0-0902c0e8950a");

    public static final UUID BLE_UUID_DIMMER_CHAR = UUID.fromString("c0f42001-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_CS_CHAR = UUID.fromString("c0f42002-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_HS_CHAR = UUID.fromString("c0f42003-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_LS_CHAR = UUID.fromString("c0f42004-9324-4085-aba0-0902c0e8950a");
    public static final UUID BLE_UUID_MS_CHAR = UUID.fromString("c0f42005-9324-4085-aba0-0902c0e8950a");

    public static final UUID BLE_UUID_DIS_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID BLE_UUID_DIS_FW_CHAR = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");

    private static final String LOG_TAG_DEVICE = "Device";

    /**
     * Construct a new device.
     */
    public Device() {
        mDeviceInfo = new DatabaseHelper.DeviceInfo();
        mBleServicesDiscovered = new ConditionVariable();
        mBleCharacteristicRwOperation = new ConditionVariable();
    }

    /**
     * Construct a new device based on given BLE Device.
     */
    public Device(BluetoothDevice bleDevice, int rssi) {
        this();
        this.mBleDevice = bleDevice;
        this.mDeviceInfo.name = bleDevice.getName();
        if (this.mDeviceInfo.name.startsWith("Aura") ) {
            this.mDeviceInfo.deviceType = DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA;
        } else if (this.mDeviceInfo.name.startsWith("Lyra") ) {
            this.mDeviceInfo.deviceType = DatabaseHelper.DeviceInfo.DEVICE_TYPE_LYRA;
        } else {
            this.mDeviceInfo.deviceType = DatabaseHelper.DeviceInfo.DEVICE_TYPE_UNKNOWN;
        }
        this.mDeviceInfo.address = bleDevice.getAddress();
        this.setRssi(rssi);
    }

    public void setBleEventCallback(BleEventCallback bleEventCallback) {
        this.bleEventCallback = bleEventCallback;
    }

    public int getRssi() {
        return this.mRssi;
    }

    public void setRssi(int _rssi) {
        this.mRssi = _rssi;
    }

    /**
     * You'll need this in your class to get the helper from the manager once per class.
     */
    private DatabaseHelper getHelper() {
        if (databaseHelper == null) {
            databaseHelper = OpenHelperManager.getHelper(ApplicationGlobals.getAppContext(), DatabaseHelper.class);
        }
        return databaseHelper;
    }

    /**
     * Setter for mDeviceInfo
     * @param deviceInfo Device Info to set
     * @param isSavedInDatabase True if this information is already stored in database.
     */
    public void setDeviceInfo(DatabaseHelper.DeviceInfo deviceInfo, boolean isSavedInDatabase) {
        this.mDeviceInfo = deviceInfo;
        this.isSaved = isSavedInDatabase;
    }

    /**
     * Getter for mDeviceInfo
     * @return mDeviceInfo
     */
    public DatabaseHelper.DeviceInfo getDeviceInfo() {
        return this.mDeviceInfo;
    }

    /**
     * Saves the device into database.
     */
    public void save() {
        try {
            Dao<DatabaseHelper.DeviceInfo, String> deviceInfoDao = getHelper().getDeviceInfoDao();
            if (!this.isSaved) {
                deviceInfoDao.create(this.mDeviceInfo);
            } else {
                deviceInfoDao.update(this.mDeviceInfo);
            }
            this.isSaved = true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes the given device from database.
     */
    public void delete() {
        try {
            Dao<DatabaseHelper.DeviceInfo, String> deviceInfoDao = getHelper().getDeviceInfoDao();
            deviceInfoDao.delete(this.mDeviceInfo);
            this.isSaved = false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns device storage state.
     * @return True if device is saved.
     */
    public boolean isRegistered() {
        return this.isSaved;
    }

    public void setBleDevice(BluetoothDevice _ble_device) {
        this.mDeviceInfo.address = _ble_device.getAddress();
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
     * Set BLE Characteristic Read/Write operation status.
     *
     * @param completed - True if operation was completed.
     *                    False if operation is still going on.
     */
    private void setBleCharacteristicRWCompleted(boolean completed) {
        if (completed) {
            this.mBleCharacteristicRwOperation.open();
        } else {
            this.mBleCharacteristicRwOperation.close();
        }
    }

    /**
     * Wait for BLE Read/Write characteristic operation to complete.
     *
     * @return True if BLE Read/Write operation was completed(either successfully or failed).
     *         False if timed out.
     */
    private boolean waitForBleCharacteristicRWComplete() {
        boolean result;
        result = this.mBleCharacteristicRwOperation.block(BLE_GATT_WRITE_TIMEOUT);
        if (!result) {
            Log.e(LOG_TAG_DEVICE, "BLE characteristic RW timed out");
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
            // Already connection was established and service discovery might be happening now.
            if (device.waitForBleServiceDiscovery()) {
                return bleGatt;
            }
        }
        if (mBleDevice == null) {
            mBleDevice = bluetoothAdapter.getRemoteDevice(this.mDeviceInfo.address);
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
        writeBleCharacteristic(BLE_UUID_DIMMER_SERVICE, BLE_UUID_DIMMER_CHAR, value);
    }

    /*
     * Returns list of device actions.
     */
    public List<DatabaseHelper.DeviceActions> getDeviceActions(int subAddress) {
        return DatabaseHelper.getDeviceActions(this.getDeviceInfo().address, subAddress);
    }

    /*
     * Add an action for the given device.
     * For example when button 1 is press turn on light.
     */
    public void addAction(int subAddress, String target, int actionType, int value) {
        DatabaseHelper.DeviceActions deviceActions = new DatabaseHelper.DeviceActions();
        deviceActions.address = this.getDeviceInfo().address;
        deviceActions.subAddress = subAddress;
        deviceActions.actionType = actionType;
        deviceActions.targetDevice = target;
        deviceActions.value = value;

        try {
            Dao<DatabaseHelper.DeviceActions, String> deviceActionsDao = getHelper().getDeviceActionsDao();
            deviceActionsDao.create(deviceActions);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets current firmware version using BLE Device Information Service.
     * @return firmware version.
     */
    public String getFirmwareVersion() {
        if (firmwareVersion != null) {
            return firmwareVersion;
        }
        readBleCharacteristic(BLE_UUID_DIS_SERVICE, BLE_UUID_DIS_FW_CHAR);
        return null;
    }

    /**
     *  Reads given BLE device's characteristic.
     *
     * @param serviceId Bluetooth GATT Service UUID where the Characteristic can be found.
     * @param characteristicId Bluetooth GATT Characteristic UUID.
     */
    private void readBleCharacteristic(UUID serviceId, UUID characteristicId) {
        BleCharRwTaskParam p = new BleCharRwTaskParam(this, serviceId, characteristicId, null, false);
        BleCharRwTask bleCharRwTask = new BleCharRwTask();
        bleCharRwTask.execute(p);
    }

    /**
     *  Writes the given bytes to given BLE device's characteristic.
     *
     * @param serviceId Bluetooth GATT Service UUID where the Characteristic can be found.
     * @param characteristicId Bluetooth GATT Characteristic UUID.
     * @param value Value to be written to the Characteristic.
     */
    private void writeBleCharacteristic(UUID serviceId, UUID characteristicId,  byte[] value) {
        BleCharRwTaskParam p = new BleCharRwTaskParam(this, serviceId, characteristicId, value, true);
        new BleCharRwTask().execute(p);
    }

    /**
     * Parameters for BleCharRwTask
     */
    private class BleCharRwTaskParam {
        final boolean isWrite;
        final Device device;
        final UUID serviceId;
        final UUID characteristicId;
        byte[] value;

        public BleCharRwTaskParam(Device device, UUID serviceId, UUID characteristicId, byte[] value, boolean isWrite) {
            this.device = device;
            this.serviceId = serviceId;
            this.characteristicId = characteristicId;
            this.isWrite = isWrite;
            this.value = value;
        }
    }

    /**
     * AsyncTask to read/write to BLE Characteristic.
     *
     * All BLE operations are async, so handling it in UI thread would create a unpleasant experience.
     * Instead we create a new thread for each BLE write which would execute asynchronously and update
     * the UI if needed.
     */
    private class BleCharRwTask extends AsyncTask<BleCharRwTaskParam, Integer, Long> {
        private Long rwCharacteristic(BleCharRwTaskParam param){
            BluetoothAdapter bluetoothAdapter = ApplicationGlobals.getBluetoothAdapter();
            BluetoothGatt gatt;
            BluetoothGattService service;
            BluetoothGattCharacteristic characteristic;
            Device device = param.device;

            gatt = bleConnect(device, bluetoothAdapter, ApplicationGlobals.getAppContext());
            service = gatt.getService(param.serviceId);
            if (service == null) {
                Log.e(LOG_TAG_DEVICE, "BLE service not found " + param.serviceId );
                return 0L;
            }

            characteristic = service.getCharacteristic(param.characteristicId);
            if (characteristic == null) {
                Log.e(LOG_TAG_DEVICE, "BLE characteristic not found " + param.characteristicId);
                return 0L;
            }
            device.setBleCharacteristicRWCompleted(false);
            if (param.isWrite) {
                characteristic.setValue(param.value);
                gatt.writeCharacteristic(characteristic);
            } else {
                gatt.readCharacteristic(characteristic);
            }
            device.waitForBleCharacteristicRWComplete();

            device.bleDisconnect();

            return 0L;
        }

        protected Long doInBackground(BleCharRwTaskParam... params) {
            // total number of BLE write requests(currently we support only one).
            int count = params.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                BleCharRwTaskParam param = params[i];
                publishProgress((int) ((i / (float) count) * 100));
                // Escape early if cancel() is called
                if (isCancelled()) {
                    break;
                }

                totalSize += rwCharacteristic(param);
            }
            return totalSize;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(Long result) {
        }
    }

    private void onCharacteristicRW(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status, boolean isWrite) {
        Log.v(LOG_TAG_DEVICE, "onCharacteristicRW " + (isWrite ? "Write" : "READ") +  " ( characteristic :" + characteristic + " ,status, : " + status + ")");
        DeviceListAdapter deviceListAdapter = DeviceListAdapter.getInstance();
        if (deviceListAdapter == null) {
            return;
        }
        Device device = deviceListAdapter.getDevice(gatt.getDevice());
        if (device != null){
            device.setBleCharacteristicRWCompleted(true);
        }

        if (characteristic.getUuid().compareTo(Device.BLE_UUID_DIS_FW_CHAR) == 0) {
            byte[] bytes = characteristic.getValue();
            firmwareVersion = new String(bytes, StandardCharsets.UTF_8);
        }
        if (!isWrite & bleEventCallback != null) {
            bleEventCallback.onBleReadCharacteristic(gatt, characteristic);
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
            super.onCharacteristicRead(gatt, characteristic, status);
            onCharacteristicRW(gatt, characteristic, status, false);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            onCharacteristicRW(gatt, characteristic, status, true);
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
                Log.v(LOG_TAG_DEVICE, "empty device list");
                return;
            }
            Device device = deviceListAdapter.getDevice(gatt.getDevice());
            if (device != null){
                device.setBleGattServicesDiscovered(true);
            } else {
                Log.v(LOG_TAG_DEVICE, "Device not found");
            }

        }
    };
}
