package com.oxlip.mobile;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BleAdvertisementRecord {

    public static final String LOG_TAG = "BleAdvRecord";

    public static int ADV_TYPE_FLAGS = 0x01;
    public static int INCOMPLETE_LIST_16BIT_SERVICE_IDS = 0x02;
    public static int COMPLETE_LIST_16BIT_SERVICE_IDS = 0x03;
    public static int INCOMPLETE_LIST_32BIT_SERVICE_IDS = 0x04;
    public static int COMPLETE_LIST_32BIT_SERVICE_IDS = 0x05;
    public static int INCOMPLETE_LIST_128BIT_SERVICE_IDS = 0x06;
    public static int COMPLETE_LIST_128BIT_SERVICE_IDS = 0x07;
    public static int SHORTENED_LOCAL_NAME = 0x08;
    public static int COMPLETE_LOCAL_NAME = 0x09;
    public static int TX_POWER_LEVEL = 0x0A;
    public static int DEVICE_ID = 0x10;
    public static int SLAVE_CONNECTION_INTERVAL_RANGE = 0x12;
    public static int SERVICE_DATA = 0x16;
    public static int APPEARANCE = 0x19;
    public static int ADVERTISING_INTERVAL = 0x1A;
    public static int MANUFACTURER_SPECIFIC_DATA = 0xFF;

    private int length;
    private int type;
    private byte[] data;
    public BleAdvertisementRecord(int length, int type, byte[] data) {
        this.length = length;
        this.type = type;
        this.data = data;

        Log.d(LOG_TAG, "Length: " + length + " Type : " + type + " Data : " + ByteArrayToString(data));
    }

    public int getLength()
    {
        return this.length;
    }

    public int getType()
    {
        return this.type;
    }

    public byte[] getData()
    {
        return this.data;
    }

    public static String ByteArrayToString(byte[] ba)
    {
        StringBuilder hex = new StringBuilder(ba.length * 2);
        for (byte b : ba)
            hex.append(b + " ");

        return hex.toString();
    }

    public static List<BleAdvertisementRecord> parseScanRecord(byte[] scanRecord) {
        List<BleAdvertisementRecord> records = new ArrayList<>();

        int index = 0;
        while (index < scanRecord.length) {
            int length = scanRecord[index++];
            //Done once we run out of records
            if (length == 0) break;

            int type = scanRecord[index];
            //Done if our record isn't a valid type
            if (type == 0) break;

            byte[] data = Arrays.copyOfRange(scanRecord, index + 1, index + length);

            records.add(new BleAdvertisementRecord(length, type, data));
            //Advance
            index += length;
        }

        return records;
    }
}
