package com.oxlip.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * A fragment representing a single Device detail screen.
 * This fragment is either contained in a {@link DeviceListActivity}
 * in two-pane mode (on tablets) or a {@link DeviceDetailActivity}
 * on handsets.
 */
public class AuraDetailFragment extends DetailFragment {
    private Device aura;
    private String deviceAddress;
    private TextView txt_ma, txt_mw, txt_volt, txt_rssi;
    private SwitchCompat btn_on;

    private LineChart chart;
    private final int CHART_MAX_VISIBLE_ENTRIES = 20;

    /* Current power state */
    private boolean poweredOn = false;

    /* Cache of power usage information of the connected device. The stored value is reflected in the UI*/
    private PowerUsage powerUsage = new PowerUsage();

    /* Current RSSI of the device*/
    private int rssi = 0;

    /* Timer to gather BLE info and update the UI */
    private Timer timer = new Timer();

    private int BLE_CS_READ_DELAY = 3000;

    private static final String LOG_TAG_AURA_UI = "AURA_UI";

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

        deviceAddress = this.getArguments().getString("deviceAddress");
        aura = DeviceListAdapter.getInstance().getDevice(deviceAddress);

        txt_rssi = (TextView)view.findViewById(R.id.dd_txt_rssi);
        txt_ma = (TextView)view.findViewById(R.id.dd_aura_cs_ma);
        txt_mw = (TextView)view.findViewById(R.id.dd_aura_cs_mw);
        txt_volt = (TextView)view.findViewById(R.id.dd_aura_cs_volt);
        btn_on = (SwitchCompat)view.findViewById(R.id.dd_aura_btn_on_off);

        btn_on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                aura.dimmerControl((byte) (isChecked ? 100 : 0));
            }
        });

        chart = (LineChart) view.findViewById(R.id.ddl_chart);
        setChart(chart);
        chart.animateX(2500);

        return view;
    }

    /**
     * Store current sensor information to database.
     */
    private void addCSInfoToDB(String address, float current, float watts, float volt) {
        Calendar calendar = Calendar.getInstance();
        Date start, end;
        end = calendar.getTime();
        calendar.add(Calendar.SECOND, -3);
        start = calendar.getTime();

        DatabaseHelper.addDeviceDataCurrentSensorValue(aura.getDeviceInfo(), start, end, current, watts, volt);
    }

    /**
     * Helper function to extract CS inforamtion from the given intent.
     * @param intent - Intent that was received.
     */
    private void extractCSInfoFromIntent(Intent intent) {
        String address = intent.getStringExtra(BleService.BLE_SERVICE_IO_DEVICE);
        if (address == null || !address.equals(deviceAddress)) {
            Log.e(LOG_TAG_AURA_UI, "Got address " + address + " for " + deviceAddress);
            return;
        }
        byte[] bytes = intent.getByteArrayExtra(BleService.BLE_SERVICE_IO_VALUE);
        /*
            typedef struct {
                uint16_t current;
                uint16_t watts;
                uint8_t volt;
                uint8_t freq;
            } ble_cs_info;
        */
        int current = byteToint(bytes[1], bytes[0]);
        int watts = byteToint(bytes[3], bytes[2]);
        int volt = byteToint((byte)0, bytes[4]);
        int freq = byteToint((byte)0, bytes[5]);
        powerUsage.now.current = current;
        powerUsage.now.wattage = watts;
        powerUsage.now.volt = volt;

        addCSInfoToDB(address, current, watts, volt);
    }

    private void extractRSSIFromIntent(Intent intent) {
        String address = intent.getStringExtra(BleService.BLE_SERVICE_IO_DEVICE);
        if (address == null || !address.equals(deviceAddress)) {
            Log.e(LOG_TAG_AURA_UI, "Got address " + address + " for " + deviceAddress);
            return;
        }
        rssi = intent.getIntExtra(BleService.BLE_SERVICE_OUT_RSSI, 0);
    }

    /**
     * Update the UI with values from cache.
     */
    private void updateUI() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txt_ma.setText("" + powerUsage.now.current);
                txt_mw.setText("" + powerUsage.now.wattage);
                txt_volt.setText("" + powerUsage.now.volt);

                txt_rssi.setText("" + rssi);

                SimpleDateFormat dateFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());
                if (powerUsage.now.current > 0) {
                    addEntryToChart(chart, dateFormat.format(new Date()), powerUsage.now.current);
                }

                //btn_on.setChecked(poweredOn);
            }
        });
    }

    /**
     * Receives async BLE char RW results.
     */
    private final BroadcastReceiver bleMsgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BleService.BLE_SERVICE_REPLY_CHAR_READ_COMPLETE)) {
                BleCharRWTask.ExecutionStatus result;
                result = (BleCharRWTask.ExecutionStatus)intent.getSerializableExtra(BleService.BLE_SERVICE_OUT_STATUS);
                if (result != BleCharRWTask.ExecutionStatus.SUCCESS) {
                    return;
                }
                UUID charid = (UUID)intent.getSerializableExtra(BleService.BLE_SERVICE_IO_CHAR);
                if (charid.compareTo(BleUuid.CS_CHAR) == 0) {
                    extractCSInfoFromIntent(intent);
                } else if (charid.compareTo(BleUuid.DIMMER_CHAR) == 0) {
                    byte[] bytes = intent.getByteArrayExtra(BleService.BLE_SERVICE_IO_VALUE);
                    poweredOn = bytes[1] != 0;
                }

                updateUI();
            } else if (action.equals(BleService.BLE_SERVICE_MSG_RSSI)) {
                extractRSSIFromIntent(intent);
                updateUI();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_RSSI);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_FOUND);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_GONE);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_HAS_DATA);
        intentFilter.addAction(BleService.BLE_SERVICE_REPLY_CHAR_READ_COMPLETE);

        getActivity().registerReceiver(bleMsgReceiver, intentFilter);

        aura.asyncReadDimmerStatus();

        this.timer = new Timer();
        /*Create new Timer to update the GUI regularly*/
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                aura.asyncReadCurrentSensorInformation();
            }
        }, 0, BLE_CS_READ_DELAY);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(bleMsgReceiver);
        this.timer.cancel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setChart(LineChart chart) {
        ArrayList<Entry> currentVals = new ArrayList<>();

        ArrayList<String> xVals = new ArrayList<>();
        List<DatabaseHelper.DeviceData> deviceDataList;
        String address = this.getArguments().getString("deviceAddress");

        deviceDataList = DatabaseHelper.getDeviceData(address, DatabaseHelper.DeviceData.SENSOR_TYPE_CURRENT, CHART_MAX_VISIBLE_ENTRIES);

        int i = 0;
        SimpleDateFormat dateFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());
        for (DatabaseHelper.DeviceData dds: deviceDataList) {
            xVals.add(dateFormat.format(dds.startDate));
            currentVals.add(new Entry(dds.sensorValue, i));
            i++;
        }

        // create a dataset and give it a type
        LineDataSet currentSet = new LineDataSet(currentVals, "Current");
        //currentSet.setColor(R.color.current);
        //currentSet.setFillColor(R.color.current);
        currentSet.setDrawCubic(true);
        currentSet.setDrawFilled(true);

        ArrayList<LineDataSet> dataSets = new ArrayList<>();
        dataSets.add(currentSet);

        LineData data = new LineData(xVals, dataSets);

        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setData(data);

        chart.getAxisRight().setStartAtZero(false);
        chart.getAxisLeft().setStartAtZero(false);
    }

    private void addEntryToChart(LineChart chart, String xVal, int yVal) {

        LineData data = chart.getData();

        if (data != null) {
            LineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                return;
            }

            // add a new x-value first
            data.addXValue(xVal);
            data.addEntry(new Entry(yVal, set.getEntryCount()), 0);

            // let the chart know it's data has changed
            chart.notifyDataSetChanged();

            // limit the number of visible entries
            chart.setVisibleXRangeMaximum(CHART_MAX_VISIBLE_ENTRIES);

            // move to the latest entry
            chart.moveViewToX(data.getXValCount() - CHART_MAX_VISIBLE_ENTRIES + 1);

            chart.getAxisRight().setStartAtZero(false);
            chart.getAxisLeft().setStartAtZero(false);
        }
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

