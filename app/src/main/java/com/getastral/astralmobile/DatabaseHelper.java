package com.getastral.astralmobile;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTable;
import com.j256.ormlite.table.TableUtils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
    private Dao<DeviceData, String> deviceDataDao = null;

    // cached copy of appliance type and make
    private static List<ApplianceType> applianceTypeList = null;
    private static List<ApplianceMake> applianceMakeList = null;

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
     * Returns the Database Access Object (DAO) for ApplianceMake.
     * It will create it or just give the cached value.
     */
    public Dao<DeviceData, String> getDeviceDataDao() throws SQLException {
        if (deviceDataDao == null) {
            deviceDataDao = getDao(DeviceData.class);
        }
        return deviceDataDao;
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
     * Returns list of appliance types.
     *
     * @return List of appliance types.
     */
    public static List<ApplianceType> getApplianceTypeList() {
        if (applianceTypeList != null) {
            return applianceTypeList;
        }
        applianceTypeList = new ArrayList<ApplianceType>();
        try {
            applianceTypeList = getInstance().getApplianceTypeDao().queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return applianceTypeList;
    }

    /**
     * Returns list of appliance makes.
     *
     * @return List of appliance makes.
     */
    public static List<ApplianceMake> getApplianceMakeList() {
        if (applianceMakeList != null) {
            return applianceMakeList;
        }
        applianceMakeList = new ArrayList<ApplianceMake>();
        try {
            applianceMakeList = getInstance().getApplianceMakeDao().queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return applianceMakeList;
    }

    /**
     * Get sensor data for the given date range for a given device.
     * @param address Address of the device. (if null all device data for the given time range will be returned).
     * @param startDate Start date.
     * @param endDate End date.
     * @return List of DeviceData for the given range.
     */
    public static List<DeviceData> getDeviceDataListForDateRange(String address, Date startDate, Date endDate) {
        try {
            QueryBuilder<DeviceData, String> queryBuilder = getInstance().getDeviceDataDao().queryBuilder();
            Where<DeviceData, String> whereQuery;
            whereQuery = queryBuilder.where().ge("startDate", startDate).and().le("endDate", endDate);
            if (address != null) {
                whereQuery = whereQuery.and().eq("address", address);
            }

            return getInstance().getDeviceDataDao().query(whereQuery.prepare());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get all sensor data for the given date range.
     * @param address Address of the device. (if null all device data for the given time range will be returned).
     * @param startDate - Start date from when to count.
     * @param days - Number of days to retrieve.
     * @return List of DeviceData for the given number of days.
     */
    public static List<DeviceData> getDeviceDataListForDateRange(String address, Date startDate, int days) {
        Date endDate;
        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.add(Calendar.DATE, days);
        endDate = c.getTime();
        return getDeviceDataListForDateRange(address, startDate, endDate);
    }

    /**
     * Get all sensor data for the given date range.
     * @param address Address of the device. (if null all device data for the given time range will be returned).
     * @param startDate - Start date from when to count.
     * @param months - Number of months from startDate.
     * @return List of DeviceData for the given number of days.
     */
    public static List<DeviceData> getDeviceDataListForMonthRange(String address, Date startDate, int months) {
        Date endDate;
        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.add(Calendar.MONTH, months);
        endDate = c.getTime();
        return getDeviceDataListForDateRange(address, startDate, endDate);
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

    /**
     * Appliance Make
     */
    @DatabaseTable(tableName = "ApplianceMake")
    static class ApplianceMake {
        /**
         * Unique Appliance Manufacturers name.
         */
        @DatabaseField(id = true)
        String name;

        /**
         * Icon resource name.
         */
        @DatabaseField(canBeNull = false)
        String imageName;

        ApplianceMake() {
            // needed by ormlite
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Appliance Type.
     */
    @DatabaseTable(tableName = "ApplianceType")
    static class ApplianceType {
        /**
         * Unique type name - Light, Fan, TV etc.
         */
        @DatabaseField(id = true)
        String name;

        /**
         * True if this appliance is dimmable.
         */
        @DatabaseField(canBeNull = false)
        boolean isDimmable;

        /**
         * Icon resource name.
         */
        @DatabaseField(canBeNull = true)
        String imageName;

        ApplianceType() {
            // needed by ormlite
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Information such as Name, Type etc that is associated with a device.
     */
    @DatabaseTable(tableName = "DeviceInfo")
    public static class DeviceInfo {
        /**
         * Unique address of the device(BLE MAC address).
         */
        @DatabaseField(id = true)
        String address;

        /**
         * Custom name given by the user.
         */
        @DatabaseField(index = true)
        String name;

        /**
         * Type of the connected appliance - Light, Fan, TV etc
         */
        @DatabaseField
        String applianceType;

        /**
         * Appliance manufacturer.
         */
        @DatabaseField
        String applianceMake;

        /**
         * Appliance manufacturer's model number.
         */
        @DatabaseField
        String applianceModel;

        /**
         * When this appliance was bought.
         */
        @DatabaseField
        Date applianceYear;

        DeviceInfo() {
            // needed by ormlite
        }
    }

    /**
     * Sensor Data from all devices.
     * Currently expects only current sensor data.
     */
    @DatabaseTable(tableName = "DeviceData")
    public static class DeviceData {
        /**
         * Device which generated this data.
         */
        @DatabaseField(foreign = true)
        DeviceInfo deviceInfo;

        /**
         * Time when the sensor data recording was started.
         */
        @DatabaseField(canBeNull = false)
        Date startDate;

        /**
         * Time when the sensor data recording was ended.
         */
        @DatabaseField
        Date endDate;

        /**
         * Sensor value.
         */
        @DatabaseField(canBeNull = false)
        float sensorValue;

        /**
         * value type (W=watts or V=volts).
         */
        @DatabaseField(canBeNull = false)
        String valueType;
    }
}
