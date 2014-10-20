package com.getastral.astralmobile;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Helper class to access the Astral Database.
 *
 *  The database contains all the devices(Plug/Switch/Touch) that are registered by the user.
 *  Basically it is cached version of CloudServer's user specific data.
 **/
class DatabaseHelper extends SQLiteOpenHelper {

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "Astral";

    // Devices table Name
    private static final String TABLE_DEVICES = "Devices";

    // Devices table's column names
    private static final String FIELD_MAC_ADDRESS = "mac_address";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_APPLIANCE_TYPE = "appliance_type";
    private static final String FIELD_APPLIANCE_MAKE = "appliance_make";
    private static final String FIELD_APPLIANCE_MODEL = "appliance_model";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_CONTACTS_TABLE = "CREATE TABLE " + TABLE_DEVICES + "(" +
                FIELD_MAC_ADDRESS + " TEXT PRIMARY KEY," +
                FIELD_NAME + " TEXT," +
                FIELD_APPLIANCE_TYPE + " TEXT," +
                FIELD_APPLIANCE_MAKE + " TEXT," +
                FIELD_APPLIANCE_MODEL + " TEXT" +
                ")";
        db.execSQL(CREATE_CONTACTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);

        // Create tables again
        onCreate(db);
    }

    /**
     * Saves the device information to database.
     * This should be happening only once(first time when connect button is clicked).
     *
     * @param device Device needs to be saved.
     */
    void saveDevice(Device device) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(FIELD_MAC_ADDRESS, device.getBleMacAddress() );
        values.put(FIELD_NAME, device.getName());
        values.put(FIELD_APPLIANCE_TYPE, device.getApplianceType());
        values.put(FIELD_APPLIANCE_MAKE, device.getApplianceMake());
        values.put(FIELD_APPLIANCE_MODEL, device.getApplianceModel());

        db.insert(TABLE_DEVICES, null, values);
        db.close();
    }

    /**
     * Check whether the given MAC address is already exists in the database.
     *
     * * @param macAddress Mac address needs to be checked.
     */
    Boolean isRegistered(String macAddress) {
        String countQuery = "SELECT  * FROM " + TABLE_DEVICES + "WHERE " + FIELD_MAC_ADDRESS + "==" + macAddress;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();

        return cursor.getCount() > 0;
    }

    /**
     * Returns all the devices that are registered to the user.
     *
     * @param bluetoothAdapter Bluetooth adapter that should be used to discover device.
     *                         This is just passed to Device() constructor. This class does not start bluetooth scanning.
     * @param context Application context needs to be passed to Device() constructor.
     * @return List of devices.
     */
    public List<Device> getDevices(BluetoothAdapter bluetoothAdapter, Context context) {
        List<Device> deviceList = new ArrayList<Device>();
        String selectQuery = "SELECT  * FROM " + TABLE_DEVICES;

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                Device device = new Device(bluetoothAdapter, context);
                device.setBleMacAddress(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_MAC_ADDRESS)));
                device.setName(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_NAME)));
                device.setApplianceType(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_APPLIANCE_TYPE)));
                device.setApplianceMake(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_APPLIANCE_MAKE)));
                device.setApplianceModel(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_APPLIANCE_MODEL)));
                device.setRegistered();

                deviceList.add(device);
            } while (cursor.moveToNext());
        }

        return deviceList;
    }

    /**
     * Delete device from the database.
     * @param device Device needs to be removed.
     */
    public void removeDevice(Device device) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DEVICES, FIELD_MAC_ADDRESS + " = ?",
                new String[] { String.valueOf(device.getBleMacAddress()) });
        db.close();
    }

    /**
     * Returns total number devices in the database.
     *
     * @return Device count.
     */
    public int getCount() {
        String countQuery = "SELECT  * FROM " + TABLE_DEVICES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();

        // return count
        return cursor.getCount();
    }
}
