package com.getastral.astralmobile;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

/**
 * Information such as Name, Type etc that is associated with a device.
 */
@DatabaseTable(tableName = "DeviceInfo")
public class DeviceInfo {
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
