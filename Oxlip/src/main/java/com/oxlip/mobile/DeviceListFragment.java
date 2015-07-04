package com.oxlip.mobile;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.utils.Legend;

import java.util.ArrayList;
import java.util.List;

/**
 * A list fragment representing a list of Devices. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link AuraDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class DeviceListFragment extends Fragment {

    private boolean mScanning = false;
    private BleServiceUpdateReceiver bleServiceUpdateReceiver;

    private static final int REQUEST_ENABLE_BT = 1;

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        void onItemSelected(Device device);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static final Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(Device device) {
        }
    };

    private static final String LOG_TAG_DLF = "DeviceListFragment";

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public DeviceListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Activity activity = getActivity();
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(activity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        BluetoothAdapter  bluetoothAdapter = ApplicationGlobals.getBluetoothAdapter();
        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter == null) {
            Toast.makeText(activity, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_list, container, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem menuSync = menu.findItem(R.id.main_action_bar_sync);
        if (!mScanning) {
            menu.findItem(R.id.main_action_bar_sync).setIcon(R.drawable.ic_action_sync);
        } else {
            MenuItemCompat.setActionView(menuSync, R.layout.progress_ble_scan);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (bleServiceUpdateReceiver== null)  {
            bleServiceUpdateReceiver = new BleServiceUpdateReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_SCAN_STARTED);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_SCAN_FINISHED);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_FOUND);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_FOUND);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_GONE);
        intentFilter.addAction(BleService.BLE_SERVICE_MSG_DEVICE_HAS_DATA);
        intentFilter.addAction(BleService.BLE_SERVICE_REPLY_CHAR_READ_COMPLETE);
        getActivity().registerReceiver(bleServiceUpdateReceiver, intentFilter);

        Context context = ApplicationGlobals.getAppContext();
        Intent intent = new Intent(context, BleService.class);
        intent.setAction(BleService.BLE_SERVICE_REQUEST_SCAN);
        context.startService(intent);

        View view = getView();
        if (view == null) {
            return;
        }
        PieChart chart = (PieChart) view.findViewById(R.id.fdl_header_chart);
        setChartData(chart);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (bleServiceUpdateReceiver != null) {
            getActivity().unregisterReceiver(bleServiceUpdateReceiver);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            getActivity().finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }

    private void setChartData(PieChart chart) {
        if (chart == null) {
            return;
        }

        int foregroundColor = getResources().getColor(R.color.foreground);
        ArrayList<Entry> yVals = new ArrayList<>();
        ArrayList<String> xVals = new ArrayList<>();
        List<DatabaseHelper.DeviceDataSummary> summaryList;
        boolean noData;

        summaryList = DatabaseHelper.getDeviceDataSummaryListForPastNDays(null, 30);

        int i = 0;
        for (DatabaseHelper.DeviceDataSummary dds: summaryList) {
            yVals.add(new Entry(dds.sensorValueSum, i));
            xVals.add(dds.deviceName);
            i++;
        }

        noData = yVals.size() == 0;
        if (noData) {
            yVals.add(new Entry(100, 1));
            xVals.add("");
        }
        PieDataSet pieDataSet = new PieDataSet(yVals, "");
        pieDataSet.setSliceSpace(3f);

        // add a lot of colors
        pieDataSet.setColors(getResources().getIntArray(R.array.main_chart));

        PieData data = new PieData(xVals, pieDataSet);
        chart.setData(data);

        chart.setDrawXValues(false);

        chart.setHoleColor(getResources().getColor(R.color.background));

        chart.getPaint(PieChart.PAINT_CENTER_TEXT).setColor(foregroundColor);

        //chart.setDrawCenterText(!noData);
        chart.setDrawYValues(!noData);
        chart.setDrawLegend(!noData);

        chart.setCenterTextSize(24f);
        if (noData) {
            chart.setCenterText( "NA");
        } else {
            chart.setCenterText( "Energy\nUsage");
        }

        chart.setUsePercentValues(true);

        Legend l = chart.getLegend();
        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART_CENTER);
        l.setXEntrySpace(7f);
        l.setYEntrySpace(5f);
        l.setTextSize(12f);

        chart.setDescription("");

        chart.getPaint(PieChart.PAINT_LEGEND_LABEL).setColor(foregroundColor);
        chart.setTransparentCircleRadius(0);

        // undo all highlights
        chart.highlightValues(null);

        chart.animateXY(1500, 1500);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        DeviceListActivity activity = (DeviceListActivity)getActivity();
        final ListView listview;
        View view = getView();
        if (view == null) {
            Log.e(LOG_TAG_DLF, "view is null");
            return;
        }
        listview = (ListView) view.findViewById(R.id.fdl_list);

        LayoutInflater inflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View headerView = inflater.inflate(R.layout.header_device_list, listview, false);
        PieChart chart = (PieChart) headerView.findViewById(R.id.fdl_header_chart);
        setChartData(chart);
        listview.addHeaderView(headerView, null, true);

        listview.setAdapter(DeviceListAdapter.getInstance(activity));
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int headerCount = listview.getHeaderViewsCount();
                position = position - headerCount;
                if (position < 0) {
                    return;
                }
                Device device = (Device) DeviceListAdapter.getInstance().getItem(position);
                mCallbacks.onItemSelected(device);
            }
        });


    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        ListView listview;
        View view = getView();
        if (view == null) {
            Log.e(LOG_TAG_DLF, "view is null");
            return;
        }

        listview = (ListView) view.findViewById(R.id.fdl_list);
        listview.setChoiceMode(activateOnItemClick ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        ListView listview;
        View view = getView();
        if (view == null) {
            Log.e(LOG_TAG_DLF, "view is null");
            return;
        }

        listview = (ListView) view.findViewById(R.id.fdl_list);
        if (position == ListView.INVALID_POSITION) {
            listview.setItemChecked(mActivatedPosition, false);
        } else {
            listview.setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    private void addDevice(String bleAddress, String name, int rssi) {
        DeviceListAdapter listAdapter = DeviceListAdapter.getInstance();
        boolean result = listAdapter.associateBleDevice(bleAddress, rssi);
        if (!result && name != null) {
            Device device = new Device(bleAddress, name, rssi);
            listAdapter.addDevice(device);
        }
    }

    private class BleServiceUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BleService.BLE_SERVICE_MSG_BLE_NOT_ENABLED)) {
                // BLE is not enabled on the phone - start system service to enable.
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else if (intent.getAction().equals(BleService.BLE_SERVICE_MSG_SCAN_STARTED)) {
                mScanning = true;
                getActivity().invalidateOptionsMenu();
            } else if (intent.getAction().equals(BleService.BLE_SERVICE_MSG_SCAN_FINISHED)) {
                mScanning = false;
                getActivity().invalidateOptionsMenu();
            } else if (intent.getAction().equals(BleService.BLE_SERVICE_MSG_DEVICE_FOUND)) {
                int rssi = intent.getIntExtra(BleService.BLE_SERVICE_OUT_RSSI, 0);
                String name = intent.getStringExtra(BleService.BLE_SERVICE_OUT_DEVICE_NAME);
                String address  = intent.getStringExtra(BleService.BLE_SERVICE_OUT_DEVICE_ADDRESS);

                addDevice(address, name, rssi);
            }
        }
    }
}
