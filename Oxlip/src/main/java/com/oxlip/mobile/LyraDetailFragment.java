package com.oxlip.mobile;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.makeramen.RoundedImageView;

import java.util.List;
import java.util.UUID;


/**
 * A fragment representing a single Device detail screen.
 * This fragment is either contained in a {@link DeviceListActivity}
 * in two-pane mode (on tablets) or a {@link DeviceDetailActivity}
 * on handsets.
 */
public class LyraDetailFragment extends DetailFragment {
    Device lyra;
    ProgressBar progressBarBattery;
    TextView textViewBatteryLevel;
    TextView textViewRssi;

    int rssi;
    int batteryLevel;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public LyraDetailFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_lyra_detail, container, false);
        String[] button_number = {"I", "II", "III"};
        String deviceAddress = this.getArguments().getString("deviceAddress");
        lyra = DeviceListAdapter.getInstance().getDevice(deviceAddress);


        RecyclerView recyclerView;
        LinearLayoutManager layoutManager;
        RecyclerView.Adapter adapter;

        progressBarBattery = (ProgressBar) view.findViewById(R.id.dv_battery_level);
        textViewBatteryLevel = (TextView) view.findViewById(R.id.dv_battery_level_text);
        textViewRssi = (TextView) view.findViewById(R.id.dv_rssi);

        // ------------- Button Recycler view
        recyclerView = (RecyclerView) view.findViewById(R.id.rcv_buttons);
        recyclerView.setHasFixedSize(true);

        layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new LyraButtonAdapter(lyra, this.getActivity(), button_number);
        recyclerView.setAdapter(adapter);

        // ------------- Device Recycler view
        recyclerView = (RecyclerView) view.findViewById(R.id.rcv_devices);
        recyclerView.setHasFixedSize(true);

        layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new AuraListAdapter(DatabaseHelper.getDevices());
        recyclerView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Helper function to extract battery level from the given intent.
     * @param intent - Intent that was received.
     */
    private void extractBatteryLevelFromIntent(Intent intent) {
        String address = intent.getStringExtra(BleService.BLE_SERVICE_IO_DEVICE);
        if (address == null || !address.equals(lyra.getDeviceInfo().address)) {
            return;
        }
        byte[] bytes = intent.getByteArrayExtra(BleService.BLE_SERVICE_IO_VALUE);
        batteryLevel = bytes[0];
    }
    private void extractRSSIFromIntent(Intent intent) {
        String address = intent.getStringExtra(BleService.BLE_SERVICE_IO_DEVICE);
        if (address == null || !address.equals(lyra.getDeviceInfo().address)) {
            return;
        }
        rssi = intent.getIntExtra(BleService.BLE_SERVICE_OUT_RSSI, 0);
    }

    /**
     * Receives async BLE char RW results.
     */
    private final BroadcastReceiver bleMsgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BleService.BLE_SERVICE_REPLY_CHAR_READ_COMPLETE)) {
                BleCharRWTask.ExecutionResult result;
                result = (BleCharRWTask.ExecutionResult)intent.getSerializableExtra(BleService.BLE_SERVICE_OUT_STATUS);
                if (result != BleCharRWTask.ExecutionResult.SUCCESS) {
                    return;
                }
                UUID charid = (UUID)intent.getSerializableExtra(BleService.BLE_SERVICE_IO_CHAR);
                if (charid.compareTo(BleUuid.BATTERY_CHAR) == 0) {
                    extractBatteryLevelFromIntent(intent);
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

        lyra.asyncReadBatteryLevel();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(bleMsgReceiver);
    }

    /**
     * Update the UI with values from cache.
     */
    private void updateUI() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewBatteryLevel.setText("" + batteryLevel);
                textViewRssi.setText("" + rssi);
                progressBarBattery.setProgress(batteryLevel);
            }
        });
    }

}

class LyraButtonAdapter extends RecyclerView.Adapter<LyraButtonAdapter.ButtonViewHolder> {
    private String[] mButtonNumbers;
    private Context mContext;
    private Device mLyra;
    private final String[] actionTypes= {"On", "Off", "Toggle"};


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public class ButtonViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        private TextView mTextViewNumber;
        private LinearLayout mLayoutDeviceInfoList;
        public ButtonViewHolder(View v) {
            super(v);
            mTextViewNumber = (TextView) v.findViewById(R.id.lyra_cfg_button_number);
            mLayoutDeviceInfoList = (LinearLayout) v.findViewById(R.id.lyra_cfg_button_device_info_list);

            mLayoutDeviceInfoList.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    final int button = (int) mTextViewNumber.getTag();

                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which){
                                case DialogInterface.BUTTON_POSITIVE:
                                    mLyra.deleteActions(button);
                                    //Yes button clicked
                                    drawActionList(button, ButtonViewHolder.this);
                                    break;

                                case DialogInterface.BUTTON_NEGATIVE:
                                    //No button clicked
                                    break;
                            }
                        }
                    };
                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setMessage("Are you sure to reset button " + button + "?").setPositiveButton("Yes", dialogClickListener)
                            .setNegativeButton("No", dialogClickListener).show();

                    return false;
                }
            });
        }
    }

    public LyraButtonAdapter(Device lyra, Context context, String[] buttonNumbers) {
        mContext = context;
        mButtonNumbers = buttonNumbers;
        mLyra = lyra;
    }

    /**
     * Assign given appliance to the given button. The action needs to be taken will be prompted to user.
     */
    private void assignApplianceToButton(final Device device, final ButtonViewHolder vh) {
        AlertDialog.Builder b = new AlertDialog.Builder(this.mContext);
        b.setTitle("What you want todo?");
        b.setItems(actionTypes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int button = (int) vh.mTextViewNumber.getTag();
                mLyra.addAction(button, device.getDeviceInfo().address, which, 0);
                drawActionList(button, vh);
                dialog.dismiss();
            }
        });
        b.show();
    }

    private void drawActionList(int button, ButtonViewHolder vh) {
        LinearLayout linearLayout = vh.mLayoutDeviceInfoList;
        if(linearLayout.getChildCount() > 0) {
            linearLayout.removeAllViews();
        }
        List<DatabaseHelper.DeviceAction> actionList = mLyra.getDeviceActions(button);
        for(DatabaseHelper.DeviceAction actionItem: actionList) {
            final Context context = this.mContext;
            DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(actionItem.targetDevice);
            DatabaseHelper.ApplianceType applianceType  = DatabaseHelper.getApplianceTypeByName(deviceInfo.applianceType);
            View v = LayoutInflater.from(context).inflate(R.layout.lyra_cfg_button_device_info_list_item, vh.mLayoutDeviceInfoList, false);
            TextView tv = (TextView)v.findViewById(R.id.lyra_cfg_button_device_info_list_item_text);
            RoundedImageView riv = (RoundedImageView)v.findViewById(R.id.lyra_cfg_button_device_info_list_item_image);

            if (applianceType != null) {
                int imgId =  context.getResources().getIdentifier(applianceType.imageName, "drawable", context.getPackageName());
                riv.setImageResource(imgId);
            }

            tv.setText(actionTypes[actionItem.actionType]);
            vh.mLayoutDeviceInfoList.addView(v);
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ButtonViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.lyra_cfg_button_list_item, parent, false);
        final ButtonViewHolder vh = new ButtonViewHolder(v);

        // catch drop events
        v.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                int action = event.getAction();
                int startColor, enterColor, endColor;
                final Resources res = ApplicationGlobals.getAppContext().getResources();

                startColor = res.getColor(R.color.blue);
                enterColor = res.getColor(R.color.green_blue);
                endColor = res.getColor(R.color.background_material_dark);
                switch (action) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        v.setBackgroundColor(startColor);
                        // do nothing
                        break;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setBackgroundColor(enterColor);
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
                        v.setBackgroundColor(startColor);
                        break;
                    case DragEvent.ACTION_DROP:
                        assignApplianceToButton((Device) event.getLocalState(), vh);
                        break;
                    case DragEvent.ACTION_DRAG_ENDED:
                        v.setBackgroundColor(endColor);
                    default:
                        break;
                }
                return true;
            }
        });

        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ButtonViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.mTextViewNumber.setText(mButtonNumbers[position]);
        holder.mTextViewNumber.setTag(position);
        drawActionList(position, holder);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mButtonNumbers.length;
    }
}

class AuraListAdapter extends RecyclerView.Adapter<AuraListAdapter.ViewHolder> {
    private List<Device> mDataset;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        private TextView mTextView;
        private RoundedImageView mImageView;
        public ViewHolder(View v) {
            super(v);
            mTextView = (TextView) v.findViewById(R.id.btn_cfg_txt_device_name);
            mImageView = (RoundedImageView) v.findViewById(R.id.lyra_cfg_dl_image);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public AuraListAdapter(List<Device> dataset) {
        mDataset = dataset;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public AuraListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.lyra_cfg_device_list_item, parent, false);
        final ViewHolder vh = new ViewHolder(v);

        // start dragging
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                    v.startDrag(data, shadowBuilder, vh.mTextView.getTag(), 0);
                    return true;
                } else {
                    return false;
                }

            }
        });

        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        DatabaseHelper.DeviceInfo deviceInfo = mDataset.get(position).getDeviceInfo();
        holder.mTextView.setText(deviceInfo.name);
        holder.mTextView.setTag(mDataset.get(position)); //this tag is used by drag and drop
        ApplianceImage.transformImage(holder.mImageView, R.drawable.ic_three_button, R.id.lyra_cfg_dl_image);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}