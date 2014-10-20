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
    // information about the device.
    private String _name;
    private String _appliance_type;
    private String _appliance_make;
    private String _appliance_model;
    // MAC address of the device (unique ID).
    private String _ble_mac_address;

    // Bluetooth adapter used for scanning and connecting.
    private final BluetoothAdapter _bluetoothAdapter;

    // Application context.
    private final Context _context;

    // Device object for the given _ble_mac(it need not to be in the range)
    private BluetoothDevice _ble_device;
    private BluetoothGatt _ble_gatt;

    // condition which would be open(trigger) only when BLE connection is opened and GATT services are discovered.
    private final ConditionVariable _ble_services_discovered;
    private final ConditionVariable _ble_characteristic_write;

    private int _rssi;
    private boolean _is_registered;

    private static final int BLE_GATT_SERVICE_DISCOVER_TIMEOUT = 5000;
    private static final int BLE_GATT_WRITE_TIMEOUT = 3000;

    private static final UUID ASTRAL_UUID_BASE = UUID.fromString("c0f41000-9324-4085-aba0-0902c0e8950a");
    private static final UUID ASTRAL_UUID_INFO = UUID.fromString("c0f41001-9324-4085-aba0-0902c0e8950a");
    private static final UUID ASTRAL_UUID_OUTLET = UUID.fromString("c0f41002-9324-4085-aba0-0902c0e8950a");

    public Device(BluetoothAdapter bluetoothAdapter, Context context) {
        _ble_services_discovered = new ConditionVariable();
        _ble_characteristic_write = new ConditionVariable();
        _bluetoothAdapter = bluetoothAdapter;
        _context = context;
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
        DatabaseHelper db = new DatabaseHelper(this._context);
        db.saveDevice(this);
        this._is_registered = true;
    }

    public void SetUnregistered() {
        this._is_registered = false;
    }

    public void setBleDevice(BluetoothDevice _ble_device) {
        this._ble_mac_address = _ble_device.getAddress();
        this._ble_device = _ble_device;
    }

    private BluetoothGatt getBleGatt() {
        return this._ble_gatt;
    }

    private void setBleGatt(BluetoothGatt _ble_gatt) {
        this._ble_gatt = _ble_gatt;
    }

    /**
     * Set BLE GATT service discovery operation status.
     *
     * @param discovered - True if services were discovered.
     *                     False if discovery is still going on.
     */
    private void setBleGattServicesDiscovered(boolean discovered) {
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
    private boolean waitForBleServiceDiscovery() {
        boolean result;

        result = this._ble_services_discovered.block(BLE_GATT_SERVICE_DISCOVER_TIMEOUT);
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
            this._ble_characteristic_write.open();
        } else {
            this._ble_characteristic_write.close();
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
        result = this._ble_characteristic_write.block(BLE_GATT_WRITE_TIMEOUT);
        if (!result) {
            Log.e("BLE", "Connection timed out while writing BLE characteristics.");
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
        if (_ble_device == null) {
            _ble_device = bluetoothAdapter.getRemoteDevice(this._ble_mac_address);
        }
        bleGatt = _ble_device.connectGatt(context , true, mGattCallback);
        device.setBleGatt(bleGatt);
        device.waitForBleServiceDiscovery();

        return bleGatt;
    }

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
        WriteBleCharacteristicTaskParam p = new WriteBleCharacteristicTaskParam(this, _bluetoothAdapter, _context, serviceId, characteristicId, value);
        new WriteBleCharacteristicTask().execute(p);
    }

    private class WriteBleCharacteristicTaskParam {
        final Device device;
        final UUID serviceId;
        final UUID characteristicId;
        final byte[] value;
        final BluetoothAdapter bluetoothAdapter;
        final Context context;
        public WriteBleCharacteristicTaskParam(Device device, BluetoothAdapter bluetoothAdapter, Context context,  UUID serviceId, UUID characteristicId,  byte[] value) {
            this.device = device;
            this.bluetoothAdapter = bluetoothAdapter;
            this.context = context;
            this.serviceId = serviceId;
            this.characteristicId = characteristicId;
            this.value = value;
        }
    }

    private class WriteBleCharacteristicTask extends AsyncTask<WriteBleCharacteristicTaskParam, Integer, Long> {
        protected Long doInBackground(WriteBleCharacteristicTaskParam... params) {
            int count = params.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                WriteBleCharacteristicTaskParam param = params[i];
                publishProgress((int) ((i / (float) count) * 100));
                // Escape early if cancel() is called
                if (isCancelled()) break;

                Device device = param.device;
                BluetoothAdapter bluetoothAdapter = param.bluetoothAdapter;
                Context context = param.context;
                UUID serviceId = param.serviceId;
                UUID characteristicId = param.characteristicId;
                byte[] value = param.value;

                BluetoothGatt gatt;
                BluetoothGattService service;
                BluetoothGattCharacteristic characteristic;

                gatt = bleConnect(device, bluetoothAdapter, context);
                service = gatt.getService(serviceId);
                if (service == null) {
                    Log.e("DLF", "service not found " + characteristicId);
                    return 0L;
                }

                characteristic = service.getCharacteristic(characteristicId);
                if (characteristic == null) {
                    Log.e("DLF", "characteristic not found " + characteristicId);
                    return 0L;
                }
                characteristic.setValue(value);
                device.setBleCharacteristicWriteCompleted(false);
                gatt.writeCharacteristic(characteristic);
                device.waitForBleCharacteristicWriteComplete();

                device.bleDisconnect();
            }
            return totalSize;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(Long result) {
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d("BLE", "onCharacteristicChanged ( characteristic : " + characteristic + ")");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "onCharacteristicRead ( characteristic :"
                        + characteristic + " ,status, : " + status + ")");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "onCharacteristicWrite ( characteristic :"
                        + characteristic + " ,status : " + status + ")");
            }
            DeviceListAdapter deviceListAdapter = DeviceListAdapter.getInstance();
            if (deviceListAdapter == null) {
                Log.d("DLA", "Empty deviceListAdapter");
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

            Log.d("BLE", "onConnectionStateChange (device : " + device
                    + ", status : " + status + " , newState :  " + newState
                    + ")");

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
            Log.d("BLE", "onDescriptorRead (device : " + device + " , status :  "
                    + status + ")");
            super.onDescriptorRead(gatt, device, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor arg0, int status) {
            Log.d("BLE", "onDescriptorWrite (arg0 : " + arg0 + " , status :  "
                    + status + ")");
            super.onDescriptorWrite(gatt, arg0, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.d("BLE", "onReliableWriteCompleted (gatt : " + status
                    + " , status :  " + status + ")");
            super.onReliableWriteCompleted(gatt, status);
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            BluetoothDevice device = gatt.getDevice();

            Log.d("BLE", "onReadRemoteRssi (device : " + device + " , rssi :  "
                    + rssi + " , status :  " + status + ")");

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d("BLE", "onServicesDiscovered");
            DeviceListAdapter deviceListAdapter = DeviceListAdapter.getInstance();
            if (deviceListAdapter == null) {
                Log.d("DLA", "Empty deviceListAdapter");
                return;
            }
            Device device = deviceListAdapter.getDevice(gatt.getDevice());
            if (device != null){
                device.setBleGattServicesDiscovered(true);
            }
        }
    };

}
