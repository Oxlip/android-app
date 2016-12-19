package com.oxlip.mobile;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class Device {
    /** Device information such as name, address etc*/
    private DatabaseHelper.DeviceInfo mDeviceInfo;

    /** Memory variables which are not stored in database */
    private int mRssi;

    /** Set to true if the device is stored in database. */
    private boolean isSaved;

    /* Firmware version of the device */
    private String firmwareVersion = null;

    /**
     * Construct a new device.
     */
    public Device() {
        this.mDeviceInfo = new DatabaseHelper.DeviceInfo();
        this.isSaved = false;
    }

    /**
     * Construct a new device based on given DeviceInfo.
     * @param deviceInfo Device Info to use.
     */
    public Device(DatabaseHelper.DeviceInfo deviceInfo) {
        this();
        this.mDeviceInfo = deviceInfo;
        this.isSaved = true;
    }

    /**
     * Construct a new device based on given BLE Device.
     */
    public Device(String address, String name, int rssi) {
        this();
        this.mDeviceInfo.address = address;
        this.mDeviceInfo.name = name;
        this.mRssi = rssi;

        if (this.mDeviceInfo.name.startsWith("Aura") ) {
            this.mDeviceInfo.deviceType = DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA;
        } else if (this.mDeviceInfo.name.startsWith("Lyra") ) {
            this.mDeviceInfo.deviceType = DatabaseHelper.DeviceInfo.DEVICE_TYPE_LYRA;
        } else {
            this.mDeviceInfo.deviceType = DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA;
        }
    }

    public int getRssi() {
        return this.mRssi;
    }

    public void setRssi(int _rssi) {
        this.mRssi = _rssi;
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
        DatabaseHelper.saveDeviceInfo(this.mDeviceInfo);
        this.isSaved = true;
    }

    /**
     * Deletes the given device from database.
     */
    public void delete() {
        DatabaseHelper.deleteDeviceInfo(this.mDeviceInfo);
        this.isSaved = false;
    }

    /**
     * Returns device storage state.
     * @return True if device is saved.
     */
    public boolean isRegistered() {
        return this.isSaved;
    }


    /**
     * Gets current firmware version using BLE Device Information Service.
     * @return firmware version.
     */
    public String getFirmwareVersion() {
        if (firmwareVersion != null) {
            return firmwareVersion;
        }

        BleService.startReadBleCharacteristic(mDeviceInfo.address, BleUuid.DIS_SERVICE, BleUuid.DIS_FW_CHAR);
        return null;
    }

    /**
     * Sets the brightness of the device if applicable.
     * @param brightness Percentage of brightness. (0-Off, 100-Fully on)
     */
    public void dimmerControl(byte brightness) {
        byte[] value = {1, brightness};
        BleService.startWriteBleCharacteristic(this.getDeviceInfo().address, BleUuid.DIMMER_SERVICE, BleUuid.DIMMER_CHAR, value);
    }

    /**
     * Starts reading of dimmer characteristics.
     */
    public void asyncReadDimmerStatus() {
        BleService.startReadBleCharacteristic(this.getDeviceInfo().address, BleUuid.DIMMER_SERVICE, BleUuid.DIMMER_CHAR);
    }

    /**
     * Get CS information asynchronously.
     * When the BLE read completes it will trigger bleEventCallback().
     */
    public void asyncReadCurrentSensorInformation() {
        BleService.startReadBleCharacteristic(this.getDeviceInfo().address, BleUuid.CS_SERVICE, BleUuid.CS_CHAR);
    }

    /**
     * Read battery level.
     */
    public void asyncReadBatteryLevel() {
        BleService.startReadBleCharacteristic(this.getDeviceInfo().address, BleUuid.BATTERY_SERVICE , BleUuid.BATTERY_CHAR);
    }

    public static byte[] hexStringToByteArray(String macAddress) {
        String[] numbers = macAddress.split(":");

        // convert hex string to byte values
        byte[] result = new byte[numbers.length];
        for(int i=0; i < result.length; i++){
            Integer hex = Integer.parseInt(numbers[i], 16);
            result[i] = hex.byteValue();
        }
        return result;
    }
    /*
     * Add an action for the given device.
     * For example when button 1 is press turn on light.
     */
    public void addAction(byte subAddress, String target, byte actionIndex, byte actionType, byte value) {
        // send it to the ble device
        /*
        typedef struct lyra_button_char_event_ {
            uint8_t action;
            uint8_t button_number;
            uint8_t action_index;
            uint8_t address[6];
            uint8_t device_type;
            uint8_t value;
            uint8_t padding;
        } lyra_button_char_event_t;
         */

        byte[] addr = hexStringToByteArray(target);
        byte[] charValue = {1, subAddress, actionIndex, addr[5], addr[4], addr[3], addr[2], addr[1], addr[0], actionType, value};
        StringBuilder sb = new StringBuilder((6*2)+6);
        for (byte b : addr) {
            if (sb.length() > 0)
                sb.append(':');
            sb.append(String.format("%02x", b));
        }
        Log.d("Lyra", "addAction target " + target + "==>" + sb);
        BleService.startWriteBleCharacteristic(this.getDeviceInfo().address, BleUuid.BUTTON_SERVICE, BleUuid.BUTTON_CHAR, charValue);

        //add to the local database
        DatabaseHelper.addAction(this.getDeviceInfo().address, subAddress, target, actionType, value);
    }

    /*
     * Returns list of device actions.
     */
    public List<DatabaseHelper.DeviceAction> getDeviceActions(int subAddress) {
        return DatabaseHelper.getDeviceAction(this.getDeviceInfo().address, subAddress);
    }

    /*
     * Delete all action associated with the device for given subTarget.
     * For example when button 1 is press turn on light.
     */
    public void deleteActions(int subAddress) {
        byte[] charValue = {1, (byte)subAddress, 2};
        BleService.startWriteBleCharacteristic(this.getDeviceInfo().address, BleUuid.BUTTON_SERVICE, BleUuid.BUTTON_CHAR, charValue);

        DatabaseHelper.deleteDeviceAction(getDeviceInfo().address, subAddress);
    }
}
