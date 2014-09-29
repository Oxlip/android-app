package com.getastral.astralmobile;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

public class Device {
    //private variables
    String _uuid;
    String _name;
    String _appliance_type;
    String _appliance_make;
    String _appliance_model;

    BluetoothDevice _ble_device;
    BluetoothGatt _ble_gatt;
    boolean _ble_gatt_services_discovered;
    int _rssi;
    boolean _is_registered;

    public String getUuid() {
        return _uuid;
    }

    public void setUuid(String _uuid) {
        this._uuid = _uuid;
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

    public BluetoothDevice getBleDevice() {
        return this._ble_device;
    }

    public void setBleDevice(BluetoothDevice _ble_device) {
        this._ble_device = _ble_device;
    }

    public BluetoothGatt getBleGatt() {
        return this._ble_gatt;
    }

    public void setBleGatt(BluetoothGatt _ble_gatt) {
        this._ble_gatt = _ble_gatt;
    }

    public boolean isBleGattServicesDiscovered() {
        return this._ble_gatt_services_discovered;
    }

    public void setBleGattServicesDiscovered(boolean discovered) {
        this._ble_gatt_services_discovered = discovered;
    }

    // Empty constructor
    public Device() {
    }

}
