package com.getastral.astralmobile;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Database helper is used to manage the creation and upgrading of your database.
 * This class also provides the DAOs used by the other classes.
 */
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    private static DatabaseHelper mInstance = null;

    private static final String LOG_TAG_DATABASE_HELPER = "DatabaseHelper";

    // name of the database file
    private static final String DATABASE_NAME = "astralthings.db";
    private static final int DATABASE_VERSION = 1;

    // the DAO objects for various tables
    private Dao<DeviceInfo, String> deviceInfoDao = null;
    private RuntimeExceptionDao<DeviceInfo, String> deviceInfoRuntimeDao = null;
    private Dao<ApplianceType, String> applianceTypeDao = null;
    private Dao<ApplianceMake, String> applianceMakeDao = null;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Returns the existing database helper instance or creates new one if required.
     * @return DatabaseHelper instance.
     */
    public static DatabaseHelper getInstance() {
        if (mInstance == null) {
            mInstance = new DatabaseHelper(ApplicationGlobals.getAppContext());
        }
        return mInstance;
    }

    private void populateTables() {
        try {
            Dao<ApplianceMake, String> applianceMakeDao = getApplianceMakeDao();
            Dao<ApplianceType, String> applianceTypeDao = getApplianceTypeDao();

            InputStream is = ApplicationGlobals.getAppContext().getResources().openRawResource(R.raw.populate_db);
            DataInputStream in = new DataInputStream(is);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                applianceMakeDao.updateRaw(strLine);
            }
            in.close();
        } catch (Exception e) {
            Log.d(LOG_TAG_DATABASE_HELPER, "Can't populate database");
            e.printStackTrace();
        }
    }

    /**
     * Called when the database is first created.
     * Creates required tables.
     */
    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        try {
            Log.i(DatabaseHelper.class.getName(), "onCreate");
            TableUtils.createTable(connectionSource, DeviceInfo.class);
            TableUtils.createTable(connectionSource, ApplianceMake.class);
            TableUtils.createTable(connectionSource, ApplianceType.class);
            populateTables();
        } catch (SQLException e) {
            Log.e(LOG_TAG_DATABASE_HELPER, "Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Called when the application is upgraded and it has a higher version number.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            Log.i(DatabaseHelper.class.getName(), "onUpgrade");
            TableUtils.dropTable(connectionSource, DeviceInfo.class, true);
            // after we drop the old databases, we create the new ones
            onCreate(db, connectionSource);
        } catch (SQLException e) {
            Log.e(DatabaseHelper.class.getName(), "Can't drop databases", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the Database Access Object (DAO) for our DeviceInfo class. It will create it or just give the cached
     * value.
     */
    public Dao<DeviceInfo, String> getDeviceInfoDao() throws SQLException {
        if (deviceInfoDao == null) {
            deviceInfoDao = getDao(DeviceInfo.class);
        }
        return deviceInfoDao;
    }

    /**
     * Returns the RuntimeExceptionDao (Database Access Object) version of a Dao for our DeviceInfo class. It will
     * create it or just give the cached value. RuntimeExceptionDao only through RuntimeExceptions.
     */
    public RuntimeExceptionDao<DeviceInfo, String> getDeviceInfoRuntimeExceptionDao() {
        if (deviceInfoRuntimeDao == null) {
            deviceInfoRuntimeDao = getRuntimeExceptionDao(DeviceInfo.class);
        }
        return deviceInfoRuntimeDao;
    }

    /**
     * Returns all the devices that are registered to the user.
     *
     * @param bluetoothAdapter Bluetooth adapter that should be used to discover device.
     *                         This is just passed to Device() constructor. This class does not start bluetooth scanning.
     * @return List of devices.
     */
    public static List<Device> getDevices(BluetoothAdapter bluetoothAdapter) {
        List<Device> deviceList = new ArrayList<Device>();
        try {
            List<DeviceInfo> deviceInfoList = getInstance().getDeviceInfoDao().queryForAll();
            for(DeviceInfo deviceInfo : deviceInfoList) {
                Device device = new Device(bluetoothAdapter);
                device.setDeviceInfo(deviceInfo, true);
                deviceList.add(device);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return deviceList;
    }

    /**
     * Returns the Database Access Object (DAO) for ApplianceType.
     * It will create it or just give the cached value.
     */
    public Dao<ApplianceType, String> getApplianceTypeDao() throws SQLException {
        if (applianceTypeDao == null) {
            applianceTypeDao = getDao(ApplianceType.class);
        }
        return applianceTypeDao;
    }

    /**
     * Returns the Database Access Object (DAO) for ApplianceMake.
     * It will create it or just give the cached value.
     */
    public Dao<ApplianceMake, String> getApplianceMakeDao() throws SQLException {
        if (applianceMakeDao == null) {
            applianceMakeDao = getDao(ApplianceMake.class);
        }
        return applianceMakeDao;
    }
    /**
     * Close the database connections and clear any cached DAOs.
     */
    @Override
    public void close() {
        super.close();
        deviceInfoDao = null;
        deviceInfoRuntimeDao = null;
    }
}