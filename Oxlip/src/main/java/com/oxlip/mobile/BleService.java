package com.oxlip.mobile;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BleService extends Service {
    private  static final  String LOG_TAG = "BLE_SERVICE";

    /* Intent actions to request ble service for certain actions */
    public static final String BLE_SERVICE_REQUEST_SCAN = "BLE_SERVICE_REQUEST_SCAN";
    public static final String BLE_SERVICE_REQUEST_CHAR_WRITE = "BLE_SERVICE_REQUEST_CHAR_WRITE";
    public static final String BLE_SERVICE_REQUEST_CHAR_READ = "BLE_SERVICE_REQUEST_CHAR_READ";

    public static final String BLE_SERVICE_IO_DEVICE = "BLE_SERVICE_IO_DEVICE";
    public static final String BLE_SERVICE_IO_SERVICE = "BLE_SERVICE_IO_SERVICE";
    public static final String BLE_SERVICE_IO_CHAR = "BLE_SERVICE_IO_CHAR";
    public static final String BLE_SERVICE_IO_VALUE = "BLE_SERVICE_IO_VALUE";
    public static final String BLE_SERVICE_IO_CONTEXT = "BLE_SERVICE_IO_CONTEXT";

    /* Intent actions when reply message is send */
    public static final String BLE_SERVICE_REPLY_CHAR_READ_COMPLETE = "BLE_SERVICE_REPLY_CHAR_READ_COMPLETE";
    public static final String BLE_SERVICE_REPLY_CHAR_WRITE_COMPLETE = "BLE_SERVICE_REPLY_CHAR_WRITE_COMPLETE";
    public static final String BLE_SERVICE_OUT_STATUS = "BLE_SERVICE_OUT_STATUS";

    public static final String BLE_SERVICE_MSG_BLE_NOT_ENABLED = "BLE_SERVICE_MSG_BLE_NOT_ENABLED";
    public static final String BLE_SERVICE_MSG_SCAN_STARTED = "BLE_SERVICE_MSG_SCAN_STARTED";
    public static final String BLE_SERVICE_MSG_SCAN_FINISHED = "BLE_SERVICE_MSG_SCAN_FINISHED";
    public static final String BLE_SERVICE_MSG_RSSI = "BLE_SERVICE_MSG_RSSI";
    public static final String BLE_SERVICE_MSG_DEVICE_FOUND = "BLE_SERVICE_MSG_DEVICE_FOUND";
    public static final String BLE_SERVICE_MSG_DEVICE_GONE = "BLE_SERVICE_MSG_DEVICE_GONE";
    public static final String BLE_SERVICE_MSG_DEVICE_HAS_DATA = "BLE_SERVICE_MSG_DEVICE_HAS_DATA";

    public static final String BLE_SERVICE_OUT_DEVICE_NAME = "BLE_SERVICE_OUT_DEVICE_NAME";
    public static final String BLE_SERVICE_OUT_DEVICE_ADDRESS = "BLE_SERVICE_OUT_DEVICE_ADDRESS";
    public static final String BLE_SERVICE_OUT_RSSI = "BLE_SERVICE_OUT_RSSI";


    private BluetoothAdapter mBluetoothAdapter;
    private Handler mBleStopHandler;
    private ConditionVariable mBleScanRunning;

    private static final long BLE_SCAN_PERIOD = 1000; // how long to scan for BLE devices.
    private static final long BLE_SCAN_FREQ = 6000; // time interval between scan stop and next scan

    private LinkedBlockingQueue<BleCharRWTask> bleTaskInfoQueue = null;

    public BleService() {
        bleTaskInfoQueue = new LinkedBlockingQueue<>();
        mBleScanRunning = new ConditionVariable();
        mBleScanRunning.open();
    }

    @Override
    public void onCreate() {
        mBleStopHandler = new Handler();
        BluetoothManager bluetoothManager = (BluetoothManager)this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Start up a new thread and start mainLoop.
        Thread t = new Thread("BleService") {
            @Override
            public void run() {
                while (true) {
                    BleCharRWTask bleTask;

                    startBleScan();
                    mBleScanRunning.block(BLE_SCAN_PERIOD);
                    do {
                        try {
                            bleTask = bleTaskInfoQueue.poll(BLE_SCAN_FREQ, TimeUnit.MILLISECONDS);
                            if (bleTask != null) {
                                bleTask.attachAdapter(mBluetoothAdapter, BleService.this);
                                bleTask.execute();
                            }
                        } catch (InterruptedException e) {
                            bleTask = null;
                        }
                    } while (bleTask != null);
                }
            }
        };
        t.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "Starting BLE Service");

        BleCharRWTask bleCharRWTask = buildBleTask(intent);
        if (bleCharRWTask != null) {
            try {
                bleTaskInfoQueue.put(bleCharRWTask);
            }catch(InterruptedException e){
                Log.e(LOG_TAG, "Failed to add to BLE task.");
            }
        }

        // If we get killed, after returning from here, restart
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    /**
     * Create BLE RW task from the given intent.
     * @param intent
     * @return
     */
    private BleCharRWTask buildBleTask(Intent intent) {
        String action = intent.getAction();
        if (action.equals(BLE_SERVICE_REQUEST_CHAR_READ) ||
            action.equals(BLE_SERVICE_REQUEST_CHAR_WRITE)) {
            String deviceAddress = intent.getStringExtra(BLE_SERVICE_IO_DEVICE);
            String serviceId = intent.getStringExtra(BLE_SERVICE_IO_SERVICE);
            String characteristicId = intent.getStringExtra(BLE_SERVICE_IO_CHAR);
            Boolean isWrite = action.equals(BLE_SERVICE_REQUEST_CHAR_WRITE);
            String appContext = intent.getStringExtra(BLE_SERVICE_IO_CONTEXT);
            byte []characteristicValue = null;

            if (isWrite) {
                characteristicValue = intent.getByteArrayExtra(BLE_SERVICE_IO_VALUE);
            }
            return new BleCharRWTask(deviceAddress, serviceId, characteristicId, characteristicValue, isWrite, appContext);
        }
        return null;
    }

    /**
     * Start BLE scan.
     */
    private void startBleScan() {
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(LOG_TAG, "BLE adpater is not enabled");
            sendBroadcast(new Intent(BLE_SERVICE_MSG_BLE_NOT_ENABLED));
            return;
        }

        // Stop scanning after a pre-defined scan period.
        mBleStopHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mBleScanRunning.open();
                Log.i(LOG_TAG, "Stopping BLE scan");
                sendBroadcast(new Intent(BLE_SERVICE_MSG_SCAN_FINISHED));
            }
        }, BLE_SCAN_PERIOD);

        Log.i(LOG_TAG, "Starting BLE scan");
        mBleScanRunning.close();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
        sendBroadcast(new Intent(BLE_SERVICE_MSG_SCAN_STARTED));
    }

    // Device scan callback.
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bleDevice, final int rssi, byte[] scanRecord) {
            Log.i(LOG_TAG, "Found new BLE device " + bleDevice + " with RSSI " + rssi + " Scan record: " + scanRecord);
            Intent intent = new Intent(BLE_SERVICE_MSG_DEVICE_FOUND);
            intent.putExtra(BLE_SERVICE_OUT_DEVICE_NAME, bleDevice.getName());
            intent.putExtra(BLE_SERVICE_OUT_DEVICE_ADDRESS, bleDevice.getAddress());
            intent.putExtra(BLE_SERVICE_OUT_RSSI, rssi);
            sendBroadcast(intent);
        }
    };

    private static void startRWBleCharacteristic(String bleAddress, UUID serviceId, UUID charId, byte[] writeValue) {
        Context context = ApplicationGlobals.getAppContext();
        Intent intent = new Intent(context, BleService.class);
        if (writeValue == null) {
            intent.setAction(BleService.BLE_SERVICE_REQUEST_CHAR_READ);
        } else {
            intent.setAction(BleService.BLE_SERVICE_REQUEST_CHAR_WRITE);
        }
        intent.putExtra(BleService.BLE_SERVICE_IO_DEVICE, bleAddress);
        intent.putExtra(BleService.BLE_SERVICE_IO_SERVICE, serviceId.toString());
        intent.putExtra(BleService.BLE_SERVICE_IO_CHAR, charId.toString());
        intent.putExtra(BleService.BLE_SERVICE_IO_VALUE, writeValue);

        context.startService(intent);
    }

    public static void startReadBleCharacteristic(String bleAddress, UUID serviceId, UUID charId) {
        startRWBleCharacteristic(bleAddress, serviceId, charId, null);
    }

    public static void startWriteBleCharacteristic(String bleAddress, UUID serviceId, UUID charId, byte[] value) {
        startRWBleCharacteristic(bleAddress, serviceId, charId, value);
    }
}
