package com.oxlip.mobile;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RawRowMapper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.PreparedQuery;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Database helper is used to manage the creation and upgrading of your database.
 * This class also provides the DAOs used by the other classes.
 */
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    private static DatabaseHelper mInstance = null;

    private static final String LOG_TAG_DATABASE_HELPER = "DatabaseHelper";

    // name of the database file
    private static final String DATABASE_NAME = "oxlip.db";
    private static final int DATABASE_VERSION = 1;

    // the DAO objects for various tables
    private Dao<DeviceInfo, String> deviceInfoDao = null;
    private RuntimeExceptionDao<DeviceInfo, String> deviceInfoRuntimeDao = null;
    private Dao<ApplianceType, String> applianceTypeDao = null;
    private Dao<ApplianceMake, String> applianceMakeDao = null;
    private Dao<DeviceAction, String> deviceActionDao = null;
    private Dao<DeviceData, String> deviceDataDao = null;

    // cached copy of appliance type and make
    private static List<ApplianceType> applianceTypeList = null;
    private static List<ApplianceMake> applianceMakeList = null;

    // Hash table for faster lookup
    private static final Map<String, ApplianceType> applianceTypeMap = new HashMap<>();

    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        for (DatabaseHelper.ApplianceType applianceType: getApplianceTypeList()) {
            applianceTypeMap.put(applianceType.name, applianceType);
        }
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
            TableUtils.createTable(connectionSource, DeviceData.class);
            TableUtils.createTable(connectionSource, DeviceAction.class);
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
     * Returns the Database Access Object (DAO) for DeviceData.
     * It will create it or just give the cached value.
     */
    public Dao<DeviceData, String> getDeviceDataDao() throws SQLException {
        if (deviceDataDao == null) {
            deviceDataDao = getDao(DeviceData.class);
        }
        return deviceDataDao;
    }

    /**
     * Returns the Database Access Object (DAO) for DeviceAction.
     * It will create it or just give the cached value.
     */
    public Dao<DeviceAction, String> getDeviceActionDao() throws SQLException {
        if (deviceActionDao == null) {
            deviceActionDao = getDao(DeviceAction.class);
        }
        return deviceActionDao;
    }

    /**
     * Returns DeviceInfo for a given deviceAddress
     */
    public static DeviceInfo getDeviceInfo(String deviceAddress) {
        try {
            return getInstance().getDeviceInfoDao().queryForId(deviceAddress);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves given DeviceInfo for a given deviceAddress
     */
    public static void saveDeviceInfo(DeviceInfo deviceInfo) {
        try {
            if (getInstance().getDeviceInfoDao().queryForId(deviceInfo.address) != null) {
                getInstance().getDeviceInfoDao().update(deviceInfo);
            } else {
                getInstance().getDeviceInfoDao().create(deviceInfo);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete given DeviceInfo
     */
    public static void deleteDeviceInfo(DeviceInfo deviceInfo) {
        try {
            getInstance().getDeviceInfoDao().delete(deviceInfo);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns all the devices that are registered to the user.
     *
     * @return List of devices.
     */
    public static List<Device> getDevices() {
        List<Device> deviceList = new ArrayList<>();
        try {
            List<DeviceInfo> deviceInfoList = getInstance().getDeviceInfoDao().queryForAll();
            for(DeviceInfo deviceInfo : deviceInfoList) {
                Device device = new Device(deviceInfo);
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
        applianceTypeList = new ArrayList<>();
        try {
            applianceTypeList = getInstance().getApplianceTypeDao().queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return applianceTypeList;
    }

    /**
     * Returns Appliance Type associated with the given name.
     * @param name - Name of the appliance
     * @return Appliance Type.
     */
    public static ApplianceType getApplianceTypeByName(String name) {
        return applianceTypeMap.get(name);
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
        applianceMakeList = new ArrayList<>();
        try {
            applianceMakeList = getInstance().getApplianceMakeDao().queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return applianceMakeList;
    }

    /**
     * Add given sensor data to the DeviceData table.
     * @param deviceInfo        Which device generated this data.
     * @param startDate         When the sensor data was started recording.
     * @param endDate           When the sensor data was stopped recording.
     * @param valueType         Value type - current, watts, volts etc.
     * @param sensorValue       Actual value.
     */
    public static void addDeviceData(DeviceInfo deviceInfo, Date startDate, Date endDate, int valueType, float sensorValue) {
        DatabaseHelper.DeviceData deviceData = new DatabaseHelper.DeviceData();
        deviceData.deviceInfo = deviceInfo;
        deviceData.startDate = startDate;
        deviceData.endDate = endDate;
        deviceData.valueType = valueType;
        deviceData.sensorValue = sensorValue;

        try {
            Dao<DatabaseHelper.DeviceData, String> deviceDataDao = getInstance().getDeviceDataDao();
            deviceDataDao.create(deviceData);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper function to call addDeviceData() since current sensor information comes in tuple.
     */
    public static void addDeviceDataCurrentSensorValue(DeviceInfo deviceInfo, Date start, Date end, float current, float watts, float volt) {
        DatabaseHelper.addDeviceData(deviceInfo, start, end, DatabaseHelper.DeviceData.SENSOR_TYPE_CURRENT, current);
        DatabaseHelper.addDeviceData(deviceInfo, start, end, DatabaseHelper.DeviceData.SENSOR_TYPE_WATTS, watts);
        DatabaseHelper.addDeviceData(deviceInfo, start, end, DatabaseHelper.DeviceData.SENSOR_TYPE_VOLTS, volt);
    }
    /*
     * Add an action for the given device.
     * For example when button 1 is press turn on light.
     */
    public static void addAction(String address, int subAddress, String target, int actionType, int value) {
        DatabaseHelper.DeviceAction deviceAction = new DatabaseHelper.DeviceAction();
        deviceAction.address = address;
        deviceAction.subAddress = subAddress;
        deviceAction.actionType = actionType;
        deviceAction.targetDevice = target;
        deviceAction.value = value;
        deviceAction.synced = 0;

        try {
            Dao<DatabaseHelper.DeviceAction, String> deviceActionsDao = getInstance().getDeviceActionDao();
            deviceActionsDao.create(deviceAction);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set syncd field to True once all the action item for a given subaddress is transferred to device.
     * @param address  Address of the device(lyra).
     * @param subAddress Subaddress(button number) of the device.
     */
    public static void setActionSynced(String address, int subAddress) {
        try {
            List<DeviceAction> list = getDeviceAction(address, subAddress);
            for(DeviceAction deviceAction:list) {
                deviceAction.synced = 1;
                Dao<DatabaseHelper.DeviceAction, String> deviceActionsDao = getInstance().getDeviceActionDao();
                deviceActionsDao.update(deviceAction);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get device actions associated with the given device.
     * @param address Address of the device.
     * @param subAddress subaddress of the device(button number).
     * @return List of DeviceData for the given range.
     */
    public static List<DeviceAction> getDeviceAction(String address, int subAddress) {
        try {
            QueryBuilder<DeviceAction, String> queryBuilder = getInstance().getDeviceActionDao().queryBuilder();
            Where<DeviceAction, String> whereQuery;
            whereQuery = queryBuilder.where().eq("address", address).and().eq("subAddress", subAddress);

            return getInstance().getDeviceActionDao().query(whereQuery.prepare());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Delete all action associated with the device for given subTarget.
     * For example when button 1 is press turn on light.
     */
    public static void deleteDeviceAction(String address, int subAddress) {
        try {
            QueryBuilder<DeviceAction, String> queryBuilder = getInstance().getDeviceActionDao().queryBuilder();
            Where<DeviceAction, String> whereQuery;
            whereQuery = queryBuilder.where().eq("address", address).and().eq("subAddress", subAddress);
            Collection<DeviceAction> collection;
            collection = getInstance().getDeviceActionDao().query(whereQuery.prepare());
            getInstance().getDeviceActionDao().delete(collection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get lat N number of sensor data for given device.
     * @param address - Address of the device.
     * @param sensorType - Sensor type.
     * @param count - Last N number of deviceData to return.
     * @return List of DeviceData.
     */
    public static List<DeviceData> getDeviceData(String address, int sensorType, long count) {
        try {
            Dao<DeviceData, String> deviceDataDao = getInstance().getDeviceDataDao();
            QueryBuilder<DeviceData, String> queryBuilder = deviceDataDao.queryBuilder();
            Where<DeviceData, String> where = queryBuilder.where();

            where.eq(DeviceData.FIELD_DEVICE, address).and().
                    eq(DeviceData.FIELD_VALUE_TYPE, sensorType);

            queryBuilder.orderBy(DeviceData.FIELD_START_DATE, true);
            queryBuilder.limit(count);
            queryBuilder.groupByRaw("DATE(" + DeviceData.FIELD_START_DATE + ")");
            queryBuilder.groupBy(DeviceData.FIELD_DEVICE);

            PreparedQuery<DeviceData> preparedQuery = queryBuilder.prepare();
            return deviceDataDao.query(preparedQuery);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static class DeviceDataSummary {
        final String deviceName;
        final String deviceAddress;
        final Date date;
        final Float sensorValueSum;

        DeviceDataSummary(String deviceName, String deviceAddress, Date date, Float sensorValueSum) {
            this.deviceName = deviceName;
            this.deviceAddress = deviceAddress;
            this.date = date;
            this.sensorValueSum = sensorValueSum;
        }
    }

    /**
     * Get sensor data summary for the given date range for a given device.
     * @param address Address of the device. (if null all device data for the given time range will be returned).
     * @param startDate Start date.
     * @param endDate End date.
     * @param valueType Sensor value type - watts, amps, volts etc.
     * @return List of DeviceData summary for the given range.
     */
    public static List<DeviceDataSummary> getDeviceDataSummaryList(String address, Date startDate, Date endDate, int valueType) {
        final int COLUMN_DEVICE = 0;
        final int COLUMN_DATE = 1;
        final int COLUMN_VALUE = 2;

        try {
            Dao<DeviceData, String> deviceDataDao = getInstance().getDeviceDataDao();
            QueryBuilder<DeviceData, String> queryBuilder = deviceDataDao.queryBuilder();

            queryBuilder.selectRaw(DeviceData.FIELD_DEVICE);
            queryBuilder.selectRaw("DATE(" + DeviceData.FIELD_START_DATE + ")");
            queryBuilder.selectRaw("SUM(" + DeviceData.FIELD_SENSOR_VALUE + ")");

            Where<DeviceData, String> where = queryBuilder.where();
            where.and(where.ge(DeviceData.FIELD_START_DATE, startDate),
                      where.le(DeviceData.FIELD_END_DATE, endDate));
            where.and(where.eq(DeviceData.FIELD_VALUE_TYPE, valueType), null);
            if (address != null) {
                where.and(where.eq(DeviceData.FIELD_DEVICE, address), null);
            }

            queryBuilder.groupByRaw("DATE(" + DeviceData.FIELD_START_DATE + "), " + DeviceData.FIELD_DEVICE);

            GenericRawResults<DeviceDataSummary> rawResults;

            RawRowMapper<DeviceDataSummary> rawRowMapper = new RawRowMapper<DeviceDataSummary>() {
                public DeviceDataSummary mapRow(String[] columnNames, String[] resultColumns) {
                    DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(resultColumns[COLUMN_DEVICE]);
                    Float value = Float.parseFloat(resultColumns[COLUMN_VALUE]);
                    Date date;
                    try {
                        date = simpleDateFormat.parse(resultColumns[COLUMN_DATE]);
                    } catch (ParseException e) {
                        Log.e(LOG_TAG_DATABASE_HELPER, "Date ParseException " + resultColumns[COLUMN_DATE]);
                        return null;
                    }

                    return new DeviceDataSummary(deviceInfo.name, deviceInfo.address, date, value);
                }
            };
            rawResults =  deviceDataDao.queryRaw(queryBuilder.prepareStatementString(), rawRowMapper,
                                                 simpleDateFormat.format(startDate),
                                                 simpleDateFormat.format(endDate));
            return rawResults.getResults();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get sensor data summary for the given date range for a given device..
     * @param address Address of the device. (if null all device data for the given time range will be returned).
     * @param days - Number of previous days to retrieve from today.
     * @param valueType Sensor value type - watts, amps, volts etc.
     * @return List of DeviceData for the given number of days.
     */
    public static List<DeviceDataSummary> getDeviceDataSummaryList(String address, int days, int valueType) {
        Date startDate, endDate;
        Calendar c = Calendar.getInstance();

        endDate = c.getTime();
        c.add(Calendar.DATE, -days);
        startDate = c.getTime();

        return getDeviceDataSummaryList(address, startDate, endDate, valueType);
    }

    /**
     * Get sensor data summary for last month.
     * @param address Address of the device.
     * @param valueType Sensor value type - watts, amps, volts etc.
     * @return List of DeviceData for the given number of days.
     */
    public static List<DeviceDataSummary> getDeviceDataSummaryList(String address, int valueType) {
        Date startDate, endDate;
        Calendar c = Calendar.getInstance();

        endDate = c.getTime();
        c.add(Calendar.MONTH, -1);
        startDate = c.getTime();

        return getDeviceDataSummaryList(address, startDate, endDate, valueType);
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
        @DatabaseField(canBeNull = true)
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

        /*
         * Device type - Aura, Lyra etc
         */
        public static final int DEVICE_TYPE_AURA = 1;
        public static final int DEVICE_TYPE_LYRA = 2;
        @DatabaseField
        Integer deviceType;

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
     * Device Actions.
     */
    @DatabaseTable(tableName = "DeviceAction")
    public static class DeviceAction {
        /**
         * Device which initiates the action. (BLE Address and Button number)
         */
        @DatabaseField (canBeNull = false)
        String address;
        @DatabaseField (canBeNull = false)
        int subAddress;

        @DatabaseField(generatedId = true)
        private int dummyId;

        /**
         * Device on which the action should be applied.
         */
        @DatabaseField(canBeNull = false)
        String targetDevice;

        /**
         * Action type.         *
         */
        public static final int ACTION_TYPE_ON = 0;
        public static final int ACTION_TYPE_OFF = 1;
        public static final int ACTION_TYPE_TOGGLE = 2;
        @DatabaseField(canBeNull = false)
        int actionType;

        /**
         * Value(if any).
         */
        @DatabaseField(canBeNull = true)
        int value;

        /**
         * Is this action synced with device.
         */
        @DatabaseField(canBeNull = false)
        int synced;
    }

    /**
     * Sensor Data from all devices.
     * Currently expects only current sensor data.
     */
    @DatabaseTable(tableName = "DeviceData")
    public static class DeviceData {
        public static final String FIELD_DEVICE = "device_id";
        public static final String FIELD_START_DATE = "start_date";
        public static final String FIELD_END_DATE = "end_date";
        public static final String FIELD_SENSOR_VALUE = "sensor_value";
        public static final String FIELD_VALUE_TYPE = "value_type";

        /**
         * Device which generated this data.
         */
        @DatabaseField(foreign = true, columnName = FIELD_DEVICE)
        DeviceInfo deviceInfo;

        /**
         * Time when the sensor data recording was started.
         */
        @DatabaseField(canBeNull = false, columnName = FIELD_START_DATE)
        Date startDate;

        /**
         * Time when the sensor data recording was ended.
         */
        @DatabaseField(canBeNull = false, columnName = FIELD_END_DATE)
        Date endDate;

        /**
         * Sensor value.
         */
        @DatabaseField(canBeNull = false, columnName = FIELD_SENSOR_VALUE)
        float sensorValue;

        /**
         * value type (W=watts or V=volts).
         */
        public static final int SENSOR_TYPE_CURRENT = 1;
        public static final int SENSOR_TYPE_WATTS = 2;
        public static final int SENSOR_TYPE_VOLTS = 3;
        @DatabaseField(canBeNull = false, columnName = FIELD_VALUE_TYPE)
        int valueType;

        @Override
        public String toString() {
            return "Device Data " + startDate + " To " + endDate + " [" + sensorValue +  "] [" + valueType + "]";
        }
    }
}
