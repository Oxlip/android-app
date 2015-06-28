package com.oxlip.mobile;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

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
    private TextView txt_ma, txt_mw, txt_volt, txt_rssi;
    private SwitchCompat btn_on;
    private SeekBar seekBar;

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

    private int byteToint(byte upper, byte lower) {
        return ((upper & 0xff) << 8) | (lower & 0xFF);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_aura_detail, container, false);

        String deviceAddress = this.getArguments().getString("deviceAddress");
        device = DeviceListAdapter.getInstance().getDevice(deviceAddress);
        device.setBleEventCallback(new Device.BleEventCallback() {
            @Override
            public void onBleReadCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                if (characteristic.getUuid().compareTo(BleUuid.CS_CHAR) == 0) {
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
                            Log.e("test", " B = " + (short)(b & 0xff));
                        }
                    }
                    int current = byteToint(bytes[1], bytes[0]);
                    int watts = byteToint(bytes[3], bytes[2]);
                    int volt = byteToint((byte)0, bytes[4]);
                    int freq = byteToint((byte)0, bytes[5]);
                    powerUsage.now.current = current;
                    powerUsage.now.wattage = watts;
                    powerUsage.now.volt = volt;
                } else if (characteristic.getUuid().compareTo(BleUuid.DIMMER_CHAR) == 0) {
                    final byte[] bytes = characteristic.getValue();
                    final byte percentage = bytes[1];

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            seekBar.setProgress(percentage);
                            btn_on.setChecked(percentage > 0);
                        }
                    });
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txt_ma.setText("" + powerUsage.now.current);
                        txt_mw.setText("" + powerUsage.now.wattage);
                        txt_volt.setText("" + powerUsage.now.volt);

                        txt_rssi.setText("" + device.getRssi());
                    }
                });
            }
        });

        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(deviceAddress);

        txt_rssi = (TextView)view.findViewById(R.id.dd_txt_rssi);
        txt_ma = (TextView)view.findViewById(R.id.dd_aura_cs_ma);
        txt_mw = (TextView)view.findViewById(R.id.dd_aura_cs_mw);
        txt_volt = (TextView)view.findViewById(R.id.dd_aura_cs_volt);
        btn_on = (SwitchCompat)view.findViewById(R.id.dd_aura_btn_on_off);
        seekBar = (SeekBar) view.findViewById(R.id.dd_aura_seekbar);


        btn_on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                device.dimmerControl((byte) (isChecked ? 100 : 0));
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                device.dimmerControl((byte)seekBar.getProgress());
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

        device.asyncReadDimmerStatus();

        this.timer = new Timer();
        /*Create new Timer to update the GUI regularly*/
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
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

