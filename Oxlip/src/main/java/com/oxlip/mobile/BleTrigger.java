package com.oxlip.mobile;

import android.util.Log;
import java.util.List;

public class BleTrigger {

    private static final String LOG_TAG = "BleTrigger";
    private static final int SD_OFFSET_SERVICE_UUID = 0;
    private static final int SD_OFFSET_BUTTON_PRESSED_BITMAP = 2;
    private static final int SD_OFFSET_BUTTON_HELD_BITMAP = 2;

    public static boolean isDeviceHasRule(String bleAddress) {
        return true;
    }

    public static void analyzeScanRecord(String bleAddress, byte[] scanRecord){

        if (!isDeviceHasRule(bleAddress)) {
            return;
        }

        List<BleAdvertisementRecord> advRecords = BleAdvertisementRecord.parseScanRecord(scanRecord);
        for(BleAdvertisementRecord advRecord: advRecords){
            if (advRecord.getType() != BleAdvertisementRecord.SERVICE_DATA) {
                continue;
            }
            byte[] data = advRecord.getData();
            if (data == null || data.length < 4) {
                Log.e(LOG_TAG, "Invalid service data");
                continue;
            }

            //write to the button to confirm that we have taken care of this trigger.
            BleService.startWriteBleCharacteristic(bleAddress, BleUuid.BUTTON_SERVICE, BleUuid.BUTTON_CHAR, data);

            int button_pressed_bitmap = data[SD_OFFSET_BUTTON_PRESSED_BITMAP];
            for(int i=0; i < 8; i++) {
                if ((button_pressed_bitmap & (1 << i)) != 0) {
                    Log.i(LOG_TAG, "Button pressed " + i);
                    CommonIntents.toggleMusic();
                }
            }
        }
    }
}

