package com.oxlip.mobile;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.db.circularcounter.CircularCounter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A fragment representing a single Device detail screen.
 * This fragment is either contained in a {@link DeviceListActivity}
 * in two-pane mode (on tablets) or a {@link DeviceDetailActivity}
 * on handsets.
 */
public class AuraDetailFragment extends DetailFragment {
    private Device device;
    private CircularCounter counter;

    /*Cache of power usage information of the connected device. The stored value is reflected in the UI*/
    private PowerUsage powerUsage = new PowerUsage();
    /* What measurement to show in the UI - 0-Amps 1-Volts 2-Watts*/
    private int powerUsageDisplayMode = 0;
    /* Timer to gather BLE info and update the UI */
    private Timer timer = new Timer();
    /* Handler to update the UI*/
    final Handler myHandler = new Handler();

    private int BLE_CS_READ_DELAY = 3000;

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
        device = DeviceListAdapter.getInstance().getDevice(deviceAddress);
        device.setBleEventCallback(new Device.BleEventCallback() {
            @Override
            public void onBleReadCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                final String firmwareVersion = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                if (characteristic.getUuid().compareTo(BleUuid.DIS_FW_CHAR) == 0) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = (TextView) view.findViewById(R.id.dd_txt_firmware_version);
                            textView.setText(firmwareVersion);
                        }
                    });
                } else if (characteristic.getUuid().compareTo(BleUuid.CS_CHAR) == 0) {
                    byte[] bytes = characteristic.getValue();
                    /*
                        typedef struct {
                            uint16_t current;
                            uint16_t watts;
                            uint8_t volt;
                            uint8_t freq;
                        } ble_cs_info;
                    */
                    if (false) {
                        Log.e("test", "got current sensor values ");
                        for (byte b : bytes) {
                            Log.e("test", " B = " + b);
                        }
                    }
                    int current = (bytes[1] << 8) | bytes[0];
                    int watts = (bytes[3] << 8) | bytes[2];
                    int volt = bytes[4];
                    int freq = bytes[5];
                    powerUsage.now.current = current;
                    powerUsage.now.wattage = watts;
                    powerUsage.now.volt = volt;
                    myHandler.post(myRunnable);
                }
            }
        });

        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(deviceAddress);

        at.markushi.ui.CircleButton circleButton = (at.markushi.ui.CircleButton)view.findViewById(R.id.dd_btn_update_firmware);
        circleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DfuService dfuService = new DfuService();
                dfuService.updateFirmware(deviceInfo);
            }
        });

        TextView textView = (TextView)view.findViewById(R.id.dd_txt_firmware_version);
        textView.setText(device.getFirmwareVersion());

        int[] colors;
        colors = getResources().getIntArray(R.array.dd_counter_colors);
        counter = (CircularCounter)view.findViewById(R.id.dd_counter);
        counter.setFirstWidth(getResources().getDimension(R.dimen.dd_counter_first))
        .setFirstColor(colors[0])
        .setSecondWidth(getResources().getDimension(R.dimen.dd_counter_second))
        .setSecondColor(colors[1])
        .setThirdWidth(getResources().getDimension(R.dimen.dd_counter_third))
                .setThirdColor(colors[2]);

        counter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // select next display mode.
                powerUsageDisplayMode = (powerUsageDisplayMode + 1) % 3;
                myHandler.post(myRunnable);
            }
        });

        BarChart chart = (BarChart) view.findViewById(R.id.ddl_chart);
        setChart(chart);
        chart.animateX(2500);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        this.timer = new Timer();
        /*Create new Timer to update the GUI regularly*/
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                //powerUsage.now.current ++;
                //myHandler.post(myRunnable);
                device.asyncReadCurrentSensorInformation();
            }
        }, 0, BLE_CS_READ_DELAY);
    }

    @Override
    public void onPause() {
        super.onPause();
        this.timer.cancel();
    }

    @Override
    public void onDestroyView() {
        if (device != null) {
            device.setBleEventCallback(null);
        }
        super.onDestroyView();
    }

    /* Runnable to update UI */
    final Runnable myRunnable = new Runnable() {
        public void run() {
            int v1, v2, v3;
            String unitText[] = {"mA", "Volts", "mW"};

            if (powerUsageDisplayMode == 0) {
                v1 = powerUsage.now.current;
                v2 = powerUsage.now.volt;
                v3 = powerUsage.now.wattage;
            } else if (powerUsageDisplayMode == 1) {
                v3 = powerUsage.now.current;
                v1 = powerUsage.now.volt;
                v2 = powerUsage.now.wattage;
            } else {
                v2 = powerUsage.now.current;
                v3 = powerUsage.now.volt;
                v1 = powerUsage.now.wattage;
            }
            counter.setValues(v1, v2, v3);
            counter.setMetricText(unitText[powerUsageDisplayMode]);
        }
    };

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

    /* Simple classes to hold power usage information */
    private class PowerInfo{
        int current; //in mA
        int volt;    //in Volts
        int wattage; //in mW
    }
    private class PowerUsage{
        PowerInfo now = new PowerInfo();
        PowerInfo past5min = new PowerInfo();
        PowerInfo past15min = new PowerInfo();
        PowerInfo past24Hours = new PowerInfo();
        PowerInfo average = new PowerInfo();
    }
}

