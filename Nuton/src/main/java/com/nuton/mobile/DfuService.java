package com.nuton.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import no.nordicsemi.android.dfu.DfuBaseService;

/** Helper class for OTA update of firmware.
 */
public class DfuService extends DfuBaseService{
    public DfuService() {
    }

    /**
     * Returns complete URI including query string to download firmware for a given device.
     * @param deviceInfo    Device for which the URI is required.
     * @return              URL with complete query string to retrieve the firmware.
     */
    private Uri getFirmwareUrl(DatabaseHelper.DeviceInfo deviceInfo) {
        /*TODO - write code for other devices. */
        String url = "http://nuton.in/download/firmware?dt=aura&hw=v1";
        return Uri.parse(url);
    }

    /**
     * Updates the firmware of a given device using Nordic's DFU library.
     * @param deviceInfo    Device which should be upgraded.
     */
    public void updateFirmware(DatabaseHelper.DeviceInfo deviceInfo) {
        /* TODO - Add check here whether this device needs update or not. */
        FirmwareDownloadHelper downloadHelper = new FirmwareDownloadHelper();
        String firmwareUri = downloadHelper.download(getFirmwareUrl(deviceInfo), deviceInfo.name);
        DfuIntent.start(deviceInfo, firmwareUri);
    }

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        /*TODO - write code to show progress*/
        return null;
    }
}
class DfuIntent {
    /**
     * Helper function to start DFU.
     *
     * @param deviceInfo   Device which needs firmware update.
     * @param firmwarePath Local hex file URI path.
     */
    public static void start(DatabaseHelper.DeviceInfo deviceInfo, String firmwarePath) {
        Context context = ApplicationGlobals.getAppContext();
        Intent service = new Intent(context, DfuService.class);
        service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, deviceInfo.address);
        service.putExtra(DfuService.EXTRA_DEVICE_NAME, deviceInfo.name);
        service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, DfuService.MIME_TYPE_OCTET_STREAM);
        service.putExtra(DfuService.EXTRA_FILE_TYPE, DfuService.TYPE_APPLICATION);
        //service.putExtra(DfuBaseService.EXTRA_FILE_PATH, firmwarePath);
        service.putExtra(DfuService.EXTRA_FILE_URI, firmwarePath);

        /*
        service.putExtra(DfuService.EXTRA_INIT_FILE_PATH, mInitFilePath);
        service.putExtra(DfuService.EXTRA_INIT_FILE_URI, mInitFileStreamUri);
        service.putExtra(DfuService.EXTRA_RESTORE_BOND, mRestoreBond);
        */

        context.startService(service);
    }

}