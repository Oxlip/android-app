package com.getastral.astralmobile;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.List;

/**
 * A list fragment representing a list of Devices. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link DeviceDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class DeviceListFragment extends Fragment {

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    // Adapter to hold all the devices to be displayed.
    private DeviceListAdapter mListAdapter;

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
        public void onItemSelected(String id);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(String id) {
        }
    };

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
        //getActionBar().setTitle(R.string.title_activity_ble_scan);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(activity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(activity, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_list, null, false);
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
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        scanLeDevice(true);
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
    public void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice bleDevice, final int rssi, byte[] scanRecord) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean result = mListAdapter.associateBleDevice(bleDevice, rssi);
                        if (!result) {
                            Device device = new Device();
                            device.setName(bleDevice.getName());
                            device.setUuid(bleDevice.getAddress());
                            device.setRssi(rssi);
                            mListAdapter.addDevice(device);
                        }
                    }
                });
            }
    };

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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        DeviceListActivity activity = (DeviceListActivity)getActivity();
        mListAdapter = new DeviceListAdapter(activity, activity.db.getDevices());
        ListView listview = (ListView) getView().findViewById(R.id.fdl_list);
        listview.setAdapter(mListAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mCallbacks.onItemSelected(Integer.toString(position));
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
        ListView listview = (ListView) getView().findViewById(R.id.fdl_list);
        listview.setChoiceMode(activateOnItemClick ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        ListView listview = (ListView) getView().findViewById(R.id.fdl_list);
        if (position == ListView.INVALID_POSITION) {
            listview.setItemChecked(mActivatedPosition, false);
        } else {
            listview.setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    // Adapter for holding devices.
    public class DeviceListAdapter extends BaseAdapter {

        Context context;
        List<Device> rowItem;

        DeviceListAdapter(Context context, List<Device> rowItem) {
            this.context = context;
            this.rowItem = rowItem;
        }

        /*
         * Associate the given ble device with matching device uuid.
         *  Returns true if a device with given uuid is found and bleDevice is associated.
         */
        public boolean associateBleDevice(BluetoothDevice bleDevice, int rssi) {
            for (int i = 0; i < rowItem.size(); i++) {
                Device device = rowItem.get(i);
                if (!device.getUuid().equals(bleDevice.getAddress())) {
                    continue;
                }
                device.setBleDevice(bleDevice);
                if (device.getRssi() != rssi) {
                    device.setRssi(rssi);
                    this.notifyDataSetInvalidated();
                }
                return true;
            }
            return false;
        }

        /**
         * Add newly discovered device.
         * @param device - New device.
         */
        public void addDevice(Device device) {
            rowItem.add(device);
            this.notifyDataSetInvalidated();
        }

        @Override
        public int getCount() {

            return rowItem.size();
        }

        @Override
        public Object getItem(int position) {

            return rowItem.get(position);
        }

        @Override
        public long getItemId(int position) {

            return rowItem.indexOf(getItem(position));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            Device device = rowItem.get(position);

            if (convertView == null) {
                LayoutInflater mInflater = (LayoutInflater) context
                        .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(R.layout.item_device_list, null);
            }

            ImageView imgIcon = (ImageView) convertView.findViewById(R.id.dl_image);
            TextView txtTitle = (TextView) convertView.findViewById(R.id.dl_name);
            ToggleButton btnOn = (ToggleButton)convertView.findViewById(R.id.dl_btn_on_off);
            Button btnConnect = (Button)convertView.findViewById(R.id.dl_btn_connect);

            /* Store the device in the buttons so that it can be retrieved when the button is clicked*/
            btnOn.setTag(device);
            btnConnect.setTag(device);

            btnOn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ToggleButton btnOn = (ToggleButton)v;
                    Device device = (Device)v.getTag();
                    Log.d("Button", device.getName());
                    if (!btnOn.isChecked()) {
                        Log.d("Button", "Sending BLE Turn off message");
                    } else {
                        Log.d("Button", "Sending BLE Turn on message");
                    }
                }
            });

            btnConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Device device = (Device)v.getTag();
                    DatabaseHandler db = new DatabaseHandler(getActivity().getApplicationContext());
                    db.connectDevice(device);
                    device.setRegistered();
                    mListAdapter.notifyDataSetInvalidated();
                }
            });

            /* "connect" button should be visible only for the first time.
             *  "On/Off" button should be visible only if the device is connected.
             */
            if (device.isRegistered()) {
                btnOn.setVisibility(View.VISIBLE);
                btnConnect.setVisibility(View.INVISIBLE);
            } else {
                btnOn.setVisibility(View.INVISIBLE);
                btnConnect.setVisibility(View.VISIBLE);
            }

            // setting the image resource and title
            imgIcon.setImageResource(R.drawable.ic_launcher);
            txtTitle.setText(device.getName());

            return convertView;
        }
    }
}
