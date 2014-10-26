package com.getastral.astralmobile;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Appliance Type.
 */
@DatabaseTable(tableName = "ApplianceType")
class ApplianceType {
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
}

/**
 * Appliance Make
 */
@DatabaseTable(tableName = "ApplianceMake")
class ApplianceMake {
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
}
