package com.oxlip.mobile;

import java.util.UUID;

public class BleUuid {

    /** The following UUIDs should in sync with firmware.
     * check nrf51-firmware/app/include/ble_uuids.h
     * */
    public static final UUID BASE = UUID.fromString("c0f41000-9324-4085-aba0-0902c0e8950a");
    public static final UUID DIMMER_SERVICE = UUID.fromString("c0f41001-9324-4085-aba0-0902c0e8950a");
    public static final UUID CS_SERVICE = UUID.fromString("c0f41002-9324-4085-aba0-0902c0e8950a");
    public static final UUID TS_SERVICE = UUID.fromString("c0f41003-9324-4085-aba0-0902c0e8950a");
    public static final UUID HS_SERVICE = UUID.fromString("c0f41004-9324-4085-aba0-0902c0e8950a");
    public static final UUID LS_SERVICE = UUID.fromString("c0f41005-9324-4085-aba0-0902c0e8950a");
    public static final UUID MS_SERVICE = UUID.fromString("c0f41006-9324-4085-aba0-0902c0e8950a");
    public static final UUID BUTTON_SERVICE = UUID.fromString("c0f41007-9324-4085-aba0-0902c0e8950a");


    public static final UUID DIMMER_CHAR = UUID.fromString("c0f42001-9324-4085-aba0-0902c0e8950a");
    public static final UUID CS_CHAR = UUID.fromString("c0f42002-9324-4085-aba0-0902c0e8950a");
    public static final UUID TS_CHAR = UUID.fromString("c0f42003-9324-4085-aba0-0902c0e8950a");
    public static final UUID HS_CHAR = UUID.fromString("c0f42004-9324-4085-aba0-0902c0e8950a");
    public static final UUID LS_CHAR = UUID.fromString("c0f42005-9324-4085-aba0-0902c0e8950a");
    public static final UUID MS_CHAR = UUID.fromString("c0f42006-9324-4085-aba0-0902c0e8950a");
    public static final UUID BUTTON_CHAR = UUID.fromString("c0f42007-9324-4085-aba0-0902c0e8950a");

    public static final UUID DIS_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID DIS_FW_CHAR = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");


    public static final UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_CHAR = UUID.fromString("00002a1b-0000-1000-8000-00805f9b34fb");
}
