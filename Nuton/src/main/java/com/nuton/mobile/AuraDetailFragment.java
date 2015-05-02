package com.nuton.mobile;

import android.app.Fragment;
import android.app.NotificationManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * A fragment representing a single Device detail screen.
 * This fragment is either contained in a {@link DeviceListActivity}
 * in two-pane mode (on tablets) or a {@link DeviceDetailActivity}
 * on handsets.
 */
public class AuraDetailFragment extends Fragment {
    private final BroadcastReceiver mDfuUpdateReceiver = new BroadcastReceiver() {
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
    public AuraDetailFragment() {
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_aura_detail, container, false);

        String deviceAddress = this.getArguments().getString("deviceAddress");
        Device device = DeviceListAdapter.getInstance().getDevice(deviceAddress);
        device.setBleEventCallback(new Device.BleEventCallback() {
            @Override
            public void onBleReadCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                final String firmwareVersion = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                if (characteristic.getUuid().compareTo(Device.BLE_UUID_DIS_FW_CHAR) == 0) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = (TextView) view.findViewById(R.id.dd_txt_firmware_version);
                            textView.setText(firmwareVersion);
                        }
                    });
                }
            }
        });

        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(deviceAddress);

        at.markushi.ui.CircleButton circleButton = (at.markushi.ui.CircleButton)view.findViewById(R.id.dd_btn_update_firmware);
        circleButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DfuService dfuService = new DfuService();
                dfuService.updateFirmware(deviceInfo);
            }
        });

        TextView textView = (TextView)view.findViewById(R.id.dd_txt_firmware_version);
        textView.setText(device.getFirmwareVersion());

        Button btnTest = (Button) view.findViewById(R.id.dd_btn_test_data);
        btnTest.setTag(deviceAddress);
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Calendar c = Calendar.getInstance();
                Date start, end = c.getTime();
                c.add(Calendar.DATE, -31);
                start = c.getTime();
                String deviceAddress = (String)view.getTag();
                DatabaseHelper.testInsertDeviceData(deviceAddress, start, end);
            }
        });

        BarChart chart = (BarChart) view.findViewById(R.id.ddl_chart);
        setChart(chart);
        chart.animateX(2500);

        return view;
    }

    @Override
    public void onDestroyView() {
        String deviceAddress = this.getArguments().getString("deviceAddress");
        Device device = DeviceListAdapter.getInstance().getDevice(deviceAddress);
        device.setBleEventCallback(null);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setChart(BarChart chart) {
        ArrayList<BarEntry> yVals = new ArrayList<BarEntry>();
        ArrayList<String> xVals = new ArrayList<String>();
        List<DatabaseHelper.DeviceDataSummary> deviceDataList;

        deviceDataList = DatabaseHelper.getDeviceDataSummaryListForPastMonth(this.getArguments().getString("deviceAddress"));

        int i = 0;
        for (DatabaseHelper.DeviceDataSummary dds: deviceDataList) {
            yVals.add(new BarEntry(dds.sensorValueSum, i));
            xVals.add(dds.date.getDate() + "");
            i++;
        }

        // create a dataset and give it a type
        BarDataSet set1 = new BarDataSet(yVals, "Consumption");
        // add a lot of colors
        set1.setColors(getResources().getIntArray(R.array.main_chart));

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);


        BarData data = new BarData(xVals, dataSets);

        chart.setDrawGridBackground(false);
        chart.setDrawValueAboveBar(false);
        chart.setDrawBarShadow(false);
        chart.setDrawLegend(false);
        chart.setDrawYValues(false);
        chart.setData(data);
    }
}
