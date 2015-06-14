package com.oxlip.mobile;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.crashlytics.android.Crashlytics;

import io.fabric.sdk.android.Fabric;

/**
 * Helper class to store global variables. Only once instance can be created.
 */
public class ApplicationGlobals extends Application {

    private static Context mContext;
    private static BluetoothAdapter mBluetoothAdapter;

    public void onCreate(){
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        ApplicationGlobals.mContext = getApplicationContext();
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) getAppContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public static Context getAppContext() {
        return ApplicationGlobals.mContext;
    }

    public static BluetoothAdapter getBluetoothAdapter() {
        return ApplicationGlobals.mBluetoothAdapter;
    }
}
