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

import java.util.Arrays;
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


    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private ConditionVariable mBleScan;
    private boolean mBleScanRunning = false;
    private boolean mServicePaused = false;

    private static final long BLE_SCAN_PERIOD = 1000; // how long to scan for BLE devices.
    private static final long BLE_SCAN_FREQ = 6000; // time interval between scan stop and next scan
    private static final long BLE_SCAN_PAUSE_TIMEOUT = 30 * 1000;

    private LinkedBlockingQueue<BleCharRWTask> bleTaskInfoQueue = null;

    public BleService() {
        bleTaskInfoQueue = new LinkedBlockingQueue<>();
        mBleScan = new ConditionVariable();
        mBleScan.open();
    }

    @Override
    public void onCreate() {
        mHandler = new Handler();
        BluetoothManager bluetoothManager = (BluetoothManager)this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Start up a new thread and start mainLoop.
        Thread t = new Thread("BleService") {
            @Override
            public void run() {
                while (true) {
                    BleCharRWTask bleTask;

                    if (!mServicePaused) {
                        startBleScan();
                        mBleScan.block(BLE_SCAN_PERIOD);
                    }

                    do {
                        if (mServicePaused) {
                            break;
                        }
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
        String action = intent.getAction();
        Log.d(LOG_TAG, "Starting BLE Service - " + action);

        if (action.equals(BLE_SERVICE_REQUEST_CHAR_READ) || action.equals(BLE_SERVICE_REQUEST_CHAR_WRITE))
        {
            BleCharRWTask bleCharRWTask = buildBleTask(intent);
            if (bleCharRWTask != null) {
                try {
                    bleTaskInfoQueue.put(bleCharRWTask);
                }catch(InterruptedException e){
                    Log.e(LOG_TAG, "Failed to add to BLE task.");
                }
            }
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
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopBleScan();
            }
        }, BLE_SCAN_PERIOD);

        Log.i(LOG_TAG, "Starting BLE scan");
        mBleScan.close();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
        mBleScanRunning = true;
        sendBroadcast(new Intent(BLE_SERVICE_MSG_SCAN_STARTED));
    }

    private void stopBleScan() {
        if (!mBleScanRunning) {
            return;
        }
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        mBleScanRunning = false;
        mBleScan.open();
        Log.i(LOG_TAG, "Stopping BLE scan");
        sendBroadcast(new Intent(BLE_SERVICE_MSG_SCAN_FINISHED));
    }

    private void pause() {
        Log.i(LOG_TAG, "Pausing BLE service");
        mServicePaused = true;
        stopBleScan();
        // resume scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                resume();
            }
        }, BLE_SCAN_PAUSE_TIMEOUT);
    }

    private void resume() {
        Log.i(LOG_TAG, "Resuming BLE service");
        mServicePaused = false;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void parseAdvertisementPacket(final byte[] scanRecord) {

        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        Log.d(LOG_TAG, "Adv : " + bytesToHex(scanRecord));

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    offset += (len - 1);
                    Log.d(LOG_TAG, "Advertisement has 16 bit UUIDs");
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    offset += (len - 1);
                    Log.d(LOG_TAG, "Advertisement has 32 bit UUIDs");
                    break;
                case 0xFF:  // Manufacturer Specific Data
                    Log.d(LOG_TAG, "Manufacturer Specific Data size:" + len + " bytes");
                    int i=0;
                    byte[] mfgData = new byte[32];
                    while (len > 1) {
                        if (i < 32) {
                            mfgData[i++] = advertisedData[offset++];
                        }
                        len -= 1;
                    }
                    Log.d(LOG_TAG, "Manufacturer Specific Data saved." + mfgData.toString());
                    break;
                default:
                    Log.d(LOG_TAG, "Unknown Advertisement type - " + type);
                    offset += (len - 1);
                    break;
            }
        }
    }
    // Device scan callback.
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bleDevice, final int rssi, byte[] scanRecord) {
            Log.i(LOG_TAG, "Found new BLE device " + bleDevice + " with RSSI " + rssi);
            parseAdvertisementPacket(scanRecord);
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
