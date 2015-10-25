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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BleService extends Service {
    private  static final  String LOG_TAG = "BLE_SERVICE";

    /* Intent actions to request ble service for certain actions */
    public static final String BLE_SERVICE_REQUEST_SCAN = "BLE_SERVICE_REQUEST_SCAN";
    public static final String BLE_SERVICE_REQUEST_SERVICE_PAUSE = "BLE_SERVICE_REQUEST_SERVICE_PAUSE";
    public static final String BLE_SERVICE_REQUEST_SERVICE_RESUME = "BLE_SERVICE_REQUEST_SERVICE_RESUME";
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


    private static BluetoothAdapter bluetoothAdapter;
    private static Handler handler = new Handler();
    private static ConditionVariable cvBleScan = new ConditionVariable();
    private static boolean isBleScanRunning = false;
    private static boolean isServicePaused = false;

    private static Map<String, byte[]> bleScanFoundDevices = new HashMap<>();

    private static final long BLE_SCAN_PERIOD = 500; // how long to scan for BLE devices.
    private static final long BLE_SCAN_FREQ = 2000; // time interval between scan stop and next scan
    private static final long BLE_SCAN_PAUSE_TIMEOUT = 30 * 1000;

    private static LinkedBlockingQueue<BleCharRWTask> bleTaskInfoQueue = new LinkedBlockingQueue<>();

    // Since only one instance of this class can be created - this field refers to that instance.
    private static BleService oneInstance;

    static {
        cvBleScan.open();
    }

    public BleService() {
        oneInstance = this;
    }


    public boolean verifyBleEnabledStatus()
    {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BLE_SERVICE_MSG_BLE_NOT_ENABLED);
            oneInstance.sendBroadcast(intent);

            return false;
        }
        return true;
    }

    @Override
    public void onCreate() {
        BluetoothManager bluetoothManager = (BluetoothManager)this.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Start up a new thread and start mainLoop.
        Thread t = new Thread("BleService") {
            @Override
            public void run() {
                while (true) {
                    BleCharRWTask bleTask;

                    verifyBleEnabledStatus();

                    if (!isServicePaused) {
                        startBleScan();
                        cvBleScan.block(BLE_SCAN_PERIOD);
                    }

                    do {
                        if (isServicePaused) {
                            break;
                        }
                        verifyBleEnabledStatus();
                        try {
                            bleTask = bleTaskInfoQueue.poll(BLE_SCAN_FREQ, TimeUnit.MILLISECONDS);
                            if (bleTask != null) {
                                bleTask.attachAdapter(bluetoothAdapter, BleService.this);
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
        String action = intent.getAction();
        Log.d(LOG_TAG, "Starting BLE Service - " + action);

        if (action.equals(BLE_SERVICE_REQUEST_CHAR_READ) || action.equals(BLE_SERVICE_REQUEST_CHAR_WRITE)) {
            createBleTask(intent);
        } else if (action.equals(BLE_SERVICE_REQUEST_SERVICE_PAUSE)) {
            pause();
        } else if (action.equals(BLE_SERVICE_REQUEST_SERVICE_RESUME)) {
            resume();
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
     * @param intent Intent from which bleTask has to be created.
     * @return Newly allocated BleCharRWTask - this is not yet added to the bleTaskInfoQueue
     */
    private static BleCharRWTask bleTaskFromIntent(Intent intent) {
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
     * Create BLE RW task from the given intent and put that in the queue for execution.
     * @param intent Intent from which bleTask has to be created.
     */
    private static void createBleTask(Intent intent) {
        BleCharRWTask bleCharRWTask = bleTaskFromIntent(intent);
        if (bleCharRWTask == null) {
            Log.e(LOG_TAG, "Failed to build BLE task.");
            return;
        }
        try {
            bleTaskInfoQueue.put(bleCharRWTask);
        }catch(InterruptedException e){
            Log.e(LOG_TAG, "Failed to add to BLE task.");
        }
    }

    /**
     * Start BLE scan.
     */
    private static void startBleScan() {
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(LOG_TAG, "BLE adapter is not enabled");
            oneInstance.sendBroadcast(new Intent(BLE_SERVICE_MSG_BLE_NOT_ENABLED));
            return;
        }

        // Stop scanning after a pre-defined scan period.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopBleScan();
            }
        }, BLE_SCAN_PERIOD);

        Log.i(LOG_TAG, "Starting BLE scan");
        bleScanFoundDevices.clear();
        cvBleScan.close();
        bluetoothAdapter.startLeScan(mLeScanCallback);
        isBleScanRunning = true;
        oneInstance.sendBroadcast(new Intent(BLE_SERVICE_MSG_SCAN_STARTED));
    }

    private static void stopBleScan() {
        if (!isBleScanRunning) {
            return;
        }
        bluetoothAdapter.stopLeScan(mLeScanCallback);
        isBleScanRunning = false;
        cvBleScan.open();
        Log.i(LOG_TAG, "Stopping BLE scan");
        oneInstance.sendBroadcast(new Intent(BLE_SERVICE_MSG_SCAN_FINISHED));
    }

    private static void pause() {
        Log.i(LOG_TAG, "Pausing BLE service");
        isServicePaused = true;
        stopBleScan();
        // resume scanning after a pre-defined scan period.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                resume();
            }
        }, BLE_SCAN_PAUSE_TIMEOUT);
    }

    private static void resume() {
        Log.i(LOG_TAG, "Resuming BLE service");
        isServicePaused = false;
    }

    // Device scan callback.
    private static final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bleDevice, final int rssi, byte[] scanRecord) {
            if (bleScanFoundDevices.get(bleDevice.getAddress()) != null) {
                Log.v(LOG_TAG, "Ignoring device since it is already found" + bleDevice);
                return;
            }

            bleScanFoundDevices.put(bleDevice.getAddress(), scanRecord);

            Log.v(LOG_TAG, "Found new BLE device " + bleDevice + " with RSSI " + rssi);
            BleTrigger.analyzeScanRecord(bleDevice.getAddress(), scanRecord);

            Intent intent = new Intent(BLE_SERVICE_MSG_DEVICE_FOUND);
            intent.putExtra(BLE_SERVICE_OUT_DEVICE_NAME, bleDevice.getName());
            intent.putExtra(BLE_SERVICE_OUT_DEVICE_ADDRESS, bleDevice.getAddress());
            intent.putExtra(BLE_SERVICE_OUT_RSSI, rssi);
            oneInstance.sendBroadcast(intent);
        }
    };

    /**
     * Initiates a Read/Write to BLE device.
     * @param bleAddress - Address of the BLE device.
     * @param serviceId - Service from which to read/write
     * @param charId - Characteristics from which to read/write
     * @param writeValue - If operation is write the value to be written.
     */
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

        // create and add a BLE task.
        createBleTask(intent);

        // stop the BLE scan if running.
        stopBleScan();
    }

    public static void startReadBleCharacteristic(String bleAddress, UUID serviceId, UUID charId) {
        startRWBleCharacteristic(bleAddress, serviceId, charId, null);
    }

    public static void startWriteBleCharacteristic(String bleAddress, UUID serviceId, UUID charId, byte[] value) {
        startRWBleCharacteristic(bleAddress, serviceId, charId, value);
    }

    /**
     * Pause BLE Services (Scan, Char RW).
     * This is needed only for DFU.
     */
    public static void pauseService() {
        Context context = ApplicationGlobals.getAppContext();
        Intent intent = new Intent(context, BleService.class);
        intent.setAction(BLE_SERVICE_REQUEST_SERVICE_PAUSE);
        context.startService(intent);
    }

    /**
     * Resume BLE Services (Scan, Char RW).
     */
    public static void resumeService() {
        Context context = ApplicationGlobals.getAppContext();
        Intent intent = new Intent(context, BleService.class);
        intent.setAction(BLE_SERVICE_REQUEST_SERVICE_RESUME);
        context.startService(intent);
    }
}
