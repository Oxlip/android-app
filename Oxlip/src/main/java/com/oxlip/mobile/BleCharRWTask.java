package com.oxlip.mobile;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.ConditionVariable;
import android.util.Log;

import java.util.UUID;

public class BleCharRWTask {
    private String mDeviceAddress;
    private boolean mIsWrite;
    private UUID mServiceId;
    private UUID mCharacteristicId;
    private byte[] mCharacteristicValue;
    private ExecutionResult mResult;
    private String mAppContext;

    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;

    private BluetoothGatt mBleGatt;
    private BluetoothDevice mBleDevice;

    /* Condition which would be open(trigger) only when BLE connection is opened and GATT services are discovered. */
    private final ConditionVariable mBleServicesDiscovered;
    private final ConditionVariable mBleCharacteristicRwOperation;


    private final String LOG_TAG_BLE_CHAR_RW = "BLE_RW_TASK";

    private static final int BLE_GATT_SERVICE_DISCOVER_TIMEOUT = 5000;
    private static final int BLE_GATT_WRITE_TIMEOUT = 3000;


    public enum ExecutionResult {
        SUCCESS,
        CONNECTION_TIMEOUT,
        SERVICE_NOT_FOUND,
        CHAR_NOT_FOUND,
        RW_TIMEOUT,
        FAILURE
    }

    public BleCharRWTask(String deviceAddress, String serviceId, String characteristicId,
                         byte[] characteristicValue, boolean isWrite, String appContext) {
        this.mDeviceAddress = deviceAddress;
        this.mServiceId =  UUID.fromString(serviceId);
        this.mCharacteristicId = UUID.fromString(characteristicId);
        this.mCharacteristicValue = characteristicValue;
        this.mIsWrite = isWrite;
        this.mAppContext = appContext;

        this.mBleServicesDiscovered = new ConditionVariable();
        this.mBleCharacteristicRwOperation = new ConditionVariable();
    }

    public void attachAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        this.mBluetoothAdapter = bluetoothAdapter;
        this.mContext = context;
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
     * Set BLE Characteristic Read/Write operation status.
     *
     * @param completed - True if operation was completed.
     *                    False if operation is still going on.
     */
    private void setBleCharacteristicRWCompleted(boolean completed) {
        if (completed) {
            mBleCharacteristicRwOperation.open();
        } else {
            mBleCharacteristicRwOperation.close();
        }
    }

    /**
     * Read/write characteristic in the BLE device.
     *
     * @param characteristic - Characteristic to read.
     * @return Success or Timeout
     */
    private ExecutionResult rwBleCharacteristic(BluetoothGattCharacteristic characteristic) {
        setBleCharacteristicRWCompleted(false);
        if (mIsWrite) {
            characteristic.setValue(mCharacteristicValue);
            this.mBleGatt.writeCharacteristic(characteristic);
        } else {
            this.mBleGatt.readCharacteristic(characteristic);
        }

        boolean result;
        result = mBleCharacteristicRwOperation.block(BLE_GATT_WRITE_TIMEOUT);
        if (!result) {
            Log.e(LOG_TAG_BLE_CHAR_RW, "BLE characteristic RW timed out");
            return ExecutionResult.RW_TIMEOUT;
        }

        return mResult;
    }

    /**
     * Connect to the ble device.
     *
     * @return Sucess or Connection Timeout
     */
    private ExecutionResult bleConnect() {
        boolean result;

        mBleDevice = mBluetoothAdapter.getRemoteDevice(this.mDeviceAddress);
        this.mBleGatt = mBleDevice.connectGatt(mContext , true, mGattCallback);
        // connect will trigger service discovery - wait for it to complete.
        result = mBleServicesDiscovered.block(BLE_GATT_SERVICE_DISCOVER_TIMEOUT);
        if (!result) {
            Log.e(LOG_TAG_BLE_CHAR_RW, "Connection timed out while discovering BLE services.");
            return ExecutionResult.CONNECTION_TIMEOUT;
        }
        return ExecutionResult.SUCCESS;

    }

    /**
     * Disconnect the BLE device.
     */
    private void bleDisconnect() {
        setBleGattServicesDiscovered(false);
        this.mBleGatt.disconnect();
        this.mBleGatt = null;
    }


    public ExecutionResult execute() {
        BluetoothGattService service;
        BluetoothGattCharacteristic characteristic;
        ExecutionResult result;

        Log.d(LOG_TAG_BLE_CHAR_RW, "Starting");

        result = bleConnect();
        if (result != ExecutionResult.SUCCESS) {
            return result;
        }

        service = this.mBleGatt.getService(mServiceId);
        if (service == null) {
            Log.e(LOG_TAG_BLE_CHAR_RW, "Service not found " + mServiceId);
            bleDisconnect();
            return ExecutionResult.SERVICE_NOT_FOUND;
        }

        characteristic = service.getCharacteristic(mCharacteristicId);
        if (characteristic == null) {
            Log.e(LOG_TAG_BLE_CHAR_RW, "Characteristic not found " + mCharacteristicId);
            bleDisconnect();
            return ExecutionResult.CHAR_NOT_FOUND;
        }

        result = rwBleCharacteristic(characteristic);

        bleDisconnect();

        Log.d(LOG_TAG_BLE_CHAR_RW, "Result = " + result);

        Intent intent;
        if (mIsWrite) {
            intent = new Intent(BleService.BLE_SERVICE_REPLY_CHAR_WRITE_COMPLETE);
        } else {
            intent = new Intent(BleService.BLE_SERVICE_REPLY_CHAR_READ_COMPLETE);
        }
        intent.putExtra(BleService.BLE_SERVICE_IO_SERVICE, mServiceId);
        intent.putExtra(BleService.BLE_SERVICE_IO_CHAR, mCharacteristicId);
        intent.putExtra(BleService.BLE_SERVICE_OUT_STATUS, result);
        if (!mIsWrite && result == ExecutionResult.SUCCESS) {
            intent.putExtra(BleService.BLE_SERVICE_IO_VALUE, characteristic.getValue());
        }
        intent.putExtra(BleService.BLE_SERVICE_IO_CONTEXT, mAppContext);
        mContext.sendBroadcast(intent);

        return result;
    }


    private void onCharacteristicRW(BluetoothGattCharacteristic characteristic, int status, boolean isWrite) {
        Log.v(LOG_TAG_BLE_CHAR_RW, "onCharacteristicRW " + (isWrite ? "Write" : "READ") + " ( characteristic :" + characteristic + " ,status, : " + status + ")");

        if (status == BluetoothGatt.GATT_SUCCESS) {
            mResult = ExecutionResult.SUCCESS;
            if (!isWrite) {
                this.mCharacteristicValue = characteristic.getValue();
            }
        } else {
            mResult = ExecutionResult.FAILURE;
        }

        setBleCharacteristicRWCompleted(true);
    }


    /**
     * BLE Async Callback functions.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.v(LOG_TAG_BLE_CHAR_RW, "onCharacteristicChanged ( characteristic : " + characteristic + ")");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            onCharacteristicRW(characteristic, status, false);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            onCharacteristicRW(characteristic, status, true);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BluetoothDevice device = gatt.getDevice();

            Log.v(LOG_TAG_BLE_CHAR_RW, "onConnectionStateChange (device : " + device + ", status : " + status + " , newState :  " + newState + ")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Automatically discover service once BLE connection is established
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor device, int status) {
            Log.v(LOG_TAG_BLE_CHAR_RW, "onDescriptorRead (device : " + device + " , status :  " + status + ")");
            super.onDescriptorRead(gatt, device, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor arg0, int status) {
            Log.v(LOG_TAG_BLE_CHAR_RW, "onDescriptorWrite (arg0 : " + arg0 + " , status :  " + status + ")");
            super.onDescriptorWrite(gatt, arg0, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.v(LOG_TAG_BLE_CHAR_RW, "onReliableWriteCompleted (gatt : " + gatt + " , status :  " + status + ")");
            super.onReliableWriteCompleted(gatt, status);
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            BluetoothDevice device = gatt.getDevice();

            Log.v(LOG_TAG_BLE_CHAR_RW, "onReadRemoteRssi (device : " + device + " , rssi :  " + rssi + " , status :  " + status + ")");
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.v(LOG_TAG_BLE_CHAR_RW, "onServicesDiscovered");
            setBleGattServicesDiscovered(true);
        }
    };
}
