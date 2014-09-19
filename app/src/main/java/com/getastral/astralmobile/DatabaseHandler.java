package com.getastral.astralmobile;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHandler extends SQLiteOpenHelper {

    // All Static variables
    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "Astral";

    // Contacts table name
    private static final String TABLE_DEVICES = "Devices";

    // Contacts Table Columns names
    private static final String FIELD_UUID = "uuid";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_APPLIANCE_TYPE = "appliance_type";
    private static final String FIELD_APPLIANCE_MAKE = "appliance_make";
    private static final String FIELD_APPLIANCE_MODEL = "appliance_model";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_CONTACTS_TABLE = "CREATE TABLE " + TABLE_DEVICES + "(" +
                FIELD_UUID + " TEXT PRIMARY KEY," +
                FIELD_NAME + " TEXT," +
                FIELD_APPLIANCE_TYPE + " TEXT," +
                FIELD_APPLIANCE_MAKE + " TEXT," +
                FIELD_APPLIANCE_MODEL + " TEXT" +
                ")";
        db.execSQL(CREATE_CONTACTS_TABLE);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);

        // Create tables again
        onCreate(db);
    }

    /**
     * All CRUD(Create, Read, Update, Delete) Operations
     */

    // Connect new device by saving the device information to database
    void connectDevice(Device device) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(FIELD_UUID, device.getUuid());
        values.put(FIELD_NAME, device.getName());
        values.put(FIELD_APPLIANCE_TYPE, device.getApplianceType());
        values.put(FIELD_APPLIANCE_MAKE, device.getApplianceMake());
        values.put(FIELD_APPLIANCE_MODEL, device.getApplianceModel());

        db.insert(TABLE_DEVICES, null, values);
        db.close();
    }

    // Check whether the given UUID is already exists in the database.
    Boolean isRegistered(String uuid) {
        String countQuery = "SELECT  * FROM " + TABLE_DEVICES + "WHERE " + FIELD_UUID + "==" + uuid;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();

        return cursor.getCount() > 0;
    }

    // Return all registered devices.
    public List<Device> getDevices() {
        List<Device> deviceList = new ArrayList<Device>();
        // Select All Query
        String selectQuery = "SELECT  * FROM " + TABLE_DEVICES;

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                Device device = new Device();
                device.setUuid(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_UUID)));
                device.setName(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_NAME)));
                device.setApplianceType(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_APPLIANCE_TYPE)));
                device.setApplianceMake(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_APPLIANCE_MAKE)));
                device.setApplianceModel(cursor.getString(cursor.getColumnIndexOrThrow(FIELD_APPLIANCE_MODEL)));

                deviceList.add(device);
            } while (cursor.moveToNext());
        }

        return deviceList;
    }

    // Unregister the given device.
    public void unregisterDevice(Device device) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DEVICES, FIELD_UUID + " = ?",
                new String[] { String.valueOf(device.getUuid()) });
        db.close();
    }

    // Getting registered devices Count
    public int getCount() {
        String countQuery = "SELECT  * FROM " + TABLE_DEVICES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();

        // return count
        return cursor.getCount();
    }
}
