package com.oxlip.mobile;

import android.app.Fragment;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;


/**
 * A fragment representing a single Device detail screen.
 * This fragment is either contained in a {@link DeviceListActivity}
 * in two-pane mode (on tablets) or a {@link DeviceDetailActivity}
 * on handsets.
 */
public class DetailFragment extends Fragment {
    protected final BroadcastReceiver mDfuUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // DFU is in progress or an error occurred
            final String action = intent.getAction();

            if (DfuService.BROADCAST_PROGRESS.equals(action)) {
                final int progress = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                final int currentPart = intent.getIntExtra(DfuService.EXTRA_PART_CURRENT, 1);
                final int totalParts = intent.getIntExtra(DfuService.EXTRA_PARTS_TOTAL, 1);
                //updateProgressBar(progress, currentPart, totalParts, false);
            } else if (DfuService.BROADCAST_ERROR.equals(action)) {
                final int error = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                //updateProgressBar(error, 0, 0, true);
                Toast.makeText(context, "DFU update failed", Toast.LENGTH_LONG).show();

                // We have to wait a bit before canceling notification. This is called before DfuService creates the last notification.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
            }
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public DetailFragment() {
    }

    private static IntentFilter makeDfuUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DfuService.BROADCAST_PROGRESS);
        intentFilter.addAction(DfuService.BROADCAST_ERROR);
        intentFilter.addAction(DfuService.BROADCAST_LOG);
        return intentFilter;
    }

    @Override
    public void onResume() {
        super.onResume();

        // We are using LocalBroadcastReceiver instead of normal BroadcastReceiver for optimization purposes
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getActivity());
        broadcastManager.registerReceiver(mDfuUpdateReceiver, makeDfuUpdateIntentFilter());
    }

    @Override
    public void onPause() {
        super.onPause();

        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getActivity());
        broadcastManager.unregisterReceiver(mDfuUpdateReceiver);
    }


    @Override
    public void onDestroyView() {
        String deviceAddress = this.getArguments().getString("deviceAddress");
        Device device = DeviceListAdapter.getInstance().getDevice(deviceAddress);
        device.setBleEventCallback(null);
        super.onDestroyView();
    }
}
