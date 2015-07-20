package com.oxlip.mobile;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


/**
 * Helper class for downloading firmware hex files from cloud server to local.
 *
 * The downloading hex file part is done using Android's Download manager.
 * Download manager works in the following way:
 *   1) Enqueue our URL to download.
 *   2) Download manager will send a notification once download is complete.
 */
public class FirmwareDownloadHelper {
    private DownloadManager dm;
    private long downloadId = -1;
    private String downloadCompleteIntentName = DownloadManager.ACTION_DOWNLOAD_COMPLETE;
    private IntentFilter downloadCompleteIntentFilter = new IntentFilter(downloadCompleteIntentName);
    //result should be stored here - null on failure.
    private String downloadedPackageUriString = null;
    // for download complete notification.
    private final Object downloadSync = new Object();
    private final String LOG_TAG = "FWDM";

    private void notifyDownloadComplete(Context context, boolean success, String errorStr) {
        if (!success) {
            Log.e(LOG_TAG, "Firmware download failed - " + errorStr);
            Toast.makeText(context, "Firmware download failed - " + errorStr, Toast.LENGTH_SHORT).show();
        }
        synchronized(downloadSync) {
            downloadSync.notify();
        }
    }

    // Handler for Downloader manager's broadcast event.
    private BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            // if some other download ignore it.
            if (id != downloadId) {
                return;
            }

            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);

            Cursor cursor = dm.query(query);
            // it shouldn't be empty, but just in case
            if (!cursor.moveToFirst()) {

                notifyDownloadComplete(context, false, "unknown reason");
                return;
            }

            // check for failures
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL != cursor.getInt(statusIndex)) {
                notifyDownloadComplete(context, false, "" + cursor.getInt(statusIndex));
                return;
            }

            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            downloadedPackageUriString = cursor.getString(uriIndex);

            notifyDownloadComplete(context, true, "");
        }
    };

    /**
     * Constructor
     */
    public FirmwareDownloadHelper() {
        Context context = ApplicationGlobals.getAppContext();
        dm = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        /* Receive Download complete intents on a separate thread */
        HandlerThread handlerThread = new HandlerThread("ht");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);
        context.registerReceiver(downloadCompleteReceiver, downloadCompleteIntentFilter, null, handler);
    }

    /**
     * Start the firmware download.
     * @param firmwareUrl Location of the firmware.
     * @param name        Name to display while downloading.
     * @return            Local URI of the file.
     */
    public String download(Uri firmwareUrl, String name) {
        DownloadManager.Request request = new DownloadManager.Request(firmwareUrl);

        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setAllowedOverRoaming(true);
        request.setTitle("Oxlip Firmware update");
        request.setDescription("Firmware update for " + name);
        request.setVisibleInDownloadsUi(false);
        request.setDestinationInExternalPublicDir("/OxlipFirmware", "aura.bin");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        downloadId = dm.enqueue(request);
        synchronized (downloadSync) {
            try {
                Log.i(LOG_TAG, "waiting");
                // wait for 20 second - our firmware is less than 100K so download should finish before this timeout.
                downloadSync.wait(20 * 1000);
            } catch (InterruptedException e) {
                Toast.makeText(ApplicationGlobals.getAppContext(), "Firmware download timed out", Toast.LENGTH_SHORT).show();
                Log.e(LOG_TAG, "Firmware download timed out.");
                e.printStackTrace();
            }
        }
        Log.i(LOG_TAG, "finished - " + downloadedPackageUriString);

        return downloadedPackageUriString;
    }
}
