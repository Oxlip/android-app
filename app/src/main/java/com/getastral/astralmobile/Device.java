package com.getastral.astralmobile;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.os.ConditionVariable;

public class Device {
    //private variables
    String _name;
    String _appliance_type;
    String _appliance_make;
    String _appliance_model;

    // MAC address of the device.
    String _ble_mac_address;
    // Device object for the given _ble_mac(it need not to be in the range)
    BluetoothDevice _ble_device;
    BluetoothGatt _ble_gatt;

    // condition which would be open(trigger) only when BLE connection is opened and GATT services are discovered.
    ConditionVariable _ble_services_discovered;
    ConditionVariable _ble_characteristic_write;

    int _rssi;
    boolean _is_registered;

    private int BLE_GATT_SERVICE_DISCOVER_TIMEOUT = 2000;


    public Device() {
        _ble_services_discovered = new ConditionVariable();
        _ble_characteristic_write = new ConditionVariable();
    }

    public String getBleMacAddress() {
        return _ble_mac_address;
    }

    public void setBleMacAddress(String _ble_mac_address) {
        this._ble_mac_address = _ble_mac_address;
    }

    public String getName() {
        return _name;
    }

    public void setName(String _name) {
        this._name = _name;
    }

    public String getApplianceType() {
        return _appliance_type;
    }

    public void setApplianceType(String _appliance_type) {
        this._appliance_type = _appliance_type;
    }

    public String getApplianceMake() {
        return _appliance_make;
    }

    public void setApplianceMake(String _appliance_make) {
        this._appliance_make = _appliance_make;
    }

    public String getApplianceModel() {
        return _appliance_model;
    }

    public void setApplianceModel(String _appliance_model) {
        this._appliance_model = _appliance_model;
    }

    public int getRssi() {
        return this._rssi;
    }

    public void setRssi(int _rssi) {
        this._rssi = _rssi;
    }

    public boolean isRegistered() {
        return this._is_registered;
    }

    public void setRegistered() {
        this._is_registered = true;
    }

    public void SetUnregistered() {
        this._is_registered = false;
    }

    /**
     * Returns BluetoothDevice object for the this device.
     * Instantiates new BluetoothDevice if requried.
     *
     * @param bluetoothAdapter
     * @return BluetoothDevice object for the this device.
     */
    public BluetoothDevice getBleDevice(BluetoothAdapter bluetoothAdapter) {
        if (this._ble_device == null) {
            this._ble_device = bluetoothAdapter.getRemoteDevice(this._ble_mac_address);
        }
        return this._ble_device;
    }

    public void setBleDevice(BluetoothDevice _ble_device) {
        this._ble_mac_address = _ble_device.getAddress();
        this._ble_device = _ble_device;
    }

    public BluetoothGatt getBleGatt() {
        return this._ble_gatt;
    }

    public void setBleGatt(BluetoothGatt _ble_gatt) {
        this._ble_gatt = _ble_gatt;
    }

    public void setBleGattServicesDiscovered(boolean discovered) {
        if (discovered) {
            this._ble_services_discovered.open();
        } else {
            this._ble_services_discovered.close();
        }
    }

    /**
     * Wait for BLE service discovery to happen for this device.
     *
     * @return True if BLE service discovery happened.
     *         False if timed out.
     */
    public boolean waitForBleServiceDiscovery() {
        return this._ble_services_discovered.block(BLE_GATT_SERVICE_DISCOVER_TIMEOUT);
    }

    public void setBleCharacteristicWriteCompleted(boolean completed) {
        if (completed) {
            this._ble_characteristic_write.open();
        } else {
            this._ble_characteristic_write.close();
        }
    }
    public boolean waitForBleCharacteristicWriteComplete() {
        return this._ble_characteristic_write.block(BLE_GATT_SERVICE_DISCOVER_TIMEOUT);
    }


    /**
     * Disconnect the BLE device.
     */
    public void bleDisconnect() {
        BluetoothGatt bleGatt;

        bleGatt = getBleGatt();
        setBleGattServicesDiscovered(false);
        bleGatt.disconnect();
        setBleGatt(null);
    }
}
