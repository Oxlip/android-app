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
    private ExecutionStatus mResult;
    private String mAppContext;

    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;

    private BluetoothGatt mBleGatt;

    /* Condition which would be open(trigger) only when BLE connection is opened and GATT services are discovered. */
    private final ConditionVariable mBleServicesDiscovered;
    private final ConditionVariable mBleCharacteristicRwOperation;


    private final String LOG_TAG_BLE_CHAR_RW = "BLE_RW_TASK";

    private static final int BLE_GATT_SERVICE_DISCOVER_TIMEOUT = 2000;
    private static final int BLE_GATT_WRITE_TIMEOUT = 2000;

    public enum ExecutionStatus {
        SUCCESS,
        CONNECTION_TIMEOUT,
        SERVICE_NOT_FOUND,
        CHAR_NOT_FOUND,
        RW_TIMEOUT,
        FAILURE
    };

    public class ExecutionResult {
        ExecutionStatus status;
        byte[] readValue;

        public ExecutionResult(ExecutionStatus status) {
            this.status = status;
        }
    };

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
    private ExecutionStatus rwBleCharacteristic(BluetoothGattCharacteristic characteristic) {
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
            return ExecutionStatus.RW_TIMEOUT;
        }

        return mResult;
    }

    /**
     * Connect to the ble device.
     *
     * @return Sucess or Connection Timeout
     */
    private ExecutionStatus bleConnect() {
        boolean result;

        BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(this.mDeviceAddress);
        this.mBleGatt = bluetoothDevice.connectGatt(mContext, false, mGattCallback);
        // connect will trigger service discovery - wait for it to complete.
        result = mBleServicesDiscovered.block(BLE_GATT_SERVICE_DISCOVER_TIMEOUT);
        if (!result) {
            return ExecutionStatus.CONNECTION_TIMEOUT;
        }
        return ExecutionStatus.SUCCESS;

    }

    /**
     * Disconnect the BLE device.
     */
    private void bleDisconnect() {
        setBleGattServicesDiscovered(false);
        if (this.mBleGatt != null) {
            this.mBleGatt.disconnect();
            this.mBleGatt = null;
        }
    }

    private ExecutionResult _finishExecution(ExecutionStatus status, byte[] readValue, String msg) {
        bleDisconnect();
        if (msg != null) {
            Log.e(LOG_TAG_BLE_CHAR_RW, msg);
        }

        ExecutionResult result = new ExecutionResult(status);
        result.readValue = readValue;
        return result;
    }

    private ExecutionResult _execute() {
        BluetoothGattService service;
        BluetoothGattCharacteristic characteristic;
        byte[] readValue = null;

        Log.d(LOG_TAG_BLE_CHAR_RW, "Executing BleCharRWTask");

        try{
            ExecutionStatus status;
            status = bleConnect();
            if (status != ExecutionStatus.SUCCESS) {
                return _finishExecution(status, null, "Failed to connect.");
            }

            service = this.mBleGatt.getService(mServiceId);
            if (service == null) {
                return _finishExecution(ExecutionStatus.SERVICE_NOT_FOUND, null, "Service not found " + mServiceId);
            }

            characteristic = service.getCharacteristic(mCharacteristicId);
            if (characteristic == null) {
                return _finishExecution(ExecutionStatus.CHAR_NOT_FOUND, null, "Characteristic not found " + mCharacteristicId);
            }

            status = rwBleCharacteristic(characteristic);
            if (status == ExecutionStatus.SUCCESS && !mIsWrite) {
                readValue = characteristic.getValue();
            }

        } catch (Exception e) {
            return _finishExecution(ExecutionStatus.FAILURE, null, "Exception while executing bleTask " + e);
        }

        return _finishExecution(ExecutionStatus.SUCCESS, readValue, null);
    }


    public ExecutionResult execute() {
        ExecutionResult result;

        result = _execute();

        Intent intent;
        if (mIsWrite) {
            intent = new Intent(BleService.BLE_SERVICE_REPLY_CHAR_WRITE_COMPLETE);
        } else {
            intent = new Intent(BleService.BLE_SERVICE_REPLY_CHAR_READ_COMPLETE);
        }
        intent.putExtra(BleService.BLE_SERVICE_IO_DEVICE, this.mDeviceAddress);
        intent.putExtra(BleService.BLE_SERVICE_IO_SERVICE, mServiceId);
        intent.putExtra(BleService.BLE_SERVICE_IO_CHAR, mCharacteristicId);
        intent.putExtra(BleService.BLE_SERVICE_OUT_STATUS, result.status);
        intent.putExtra(BleService.BLE_SERVICE_IO_VALUE, result.readValue);
        intent.putExtra(BleService.BLE_SERVICE_IO_CONTEXT, mAppContext);
        mContext.sendBroadcast(intent);

        return result;
    }


    private void onCharacteristicRW(BluetoothGattCharacteristic characteristic, int status, boolean isWrite) {
        Log.v(LOG_TAG_BLE_CHAR_RW, "onCharacteristicRW " + (isWrite ? "Write" : "READ") + " ( characteristic :" + characteristic + " ,status, : " + status + ")");

        if (status == BluetoothGatt.GATT_SUCCESS) {
            mResult = ExecutionStatus.SUCCESS;
            if (!isWrite) {
                this.mCharacteristicValue = characteristic.getValue();
            }
        } else {
            mResult = ExecutionStatus.FAILURE;
        }

        setBleCharacteristicRWCompleted(true);
    }


    /**
     * BLE Async Callback functions.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

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

            //Log.e(LOG_TAG_BLE_CHAR_RW, "onConnectionStateChange (device : " + device + ", status : " + status + " , newState :  " + newState + ")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.v(LOG_TAG_BLE_CHAR_RW, "Connected");
                    // Automatically discover service once BLE connection is established
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.v(LOG_TAG_BLE_CHAR_RW, "Disconnect");
                    gatt.close();
                }
            } else {
                Log.e(LOG_TAG_BLE_CHAR_RW, "Connect error " + status);
            }

            super.onConnectionStateChange(gatt, status, newState);
        }


        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Intent intent = new Intent();
                intent.setAction(BleService.BLE_SERVICE_MSG_RSSI);
                intent.putExtra(BleService.BLE_SERVICE_IO_DEVICE, gatt.getDevice().getAddress());
                intent.putExtra(BleService.BLE_SERVICE_OUT_RSSI, rssi);
                mContext.sendBroadcast(intent);
            }
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            gatt.readRemoteRssi();

            setBleGattServicesDiscovered(true);
            super.onServicesDiscovered(gatt, status);
        }
    };
}
