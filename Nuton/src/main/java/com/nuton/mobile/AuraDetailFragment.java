package com.nuton.mobile;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

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
public class AuraDetailFragment extends DetailFragment {

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
