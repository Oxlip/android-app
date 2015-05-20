package com.nuton.mobile;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.makeramen.RoundedImageView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * A fragment representing a single Device detail screen.
 * This fragment is either contained in a {@link DeviceListActivity}
 * in two-pane mode (on tablets) or a {@link DeviceDetailActivity}
 * on handsets.
 */
public class LyraDetailFragment extends DetailFragment {
    private RecyclerView mButtonRecyclerView;
    private RecyclerView.Adapter mButtonAdapter;
    private LinearLayoutManager mButtonLayoutManager;

    private RecyclerView mDevicesRecyclerView;
    private RecyclerView.Adapter mDevicesAdapter;
    private LinearLayoutManager mDevicesLayoutManager;

    private Device mDevice;
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public LyraDetailFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        String deviceAddress = this.getArguments().getString("deviceAddress");
        mDevice = DeviceListAdapter.getInstance().getDevice(deviceAddress);

        final View view = inflater.inflate(R.layout.fragment_lyra_detail, container, false);

        mButtonRecyclerView = (RecyclerView) view.findViewById(R.id.rcv_buttons);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mButtonRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mButtonLayoutManager = new LinearLayoutManager(getActivity());
        mButtonLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mButtonRecyclerView.setLayoutManager(mButtonLayoutManager);

        String[] button_number = {"I", "II", "III"};
        mButtonAdapter = new LyraButtonAdapter(mDevice, this.getActivity(), button_number);
        mButtonRecyclerView.setAdapter(mButtonAdapter);

        mDevicesRecyclerView = (RecyclerView) view.findViewById(R.id.rcv_devices);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mDevicesRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mDevicesLayoutManager = new LinearLayoutManager(getActivity());
        mDevicesLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mDevicesRecyclerView.setLayoutManager(mDevicesLayoutManager);

        // specify an adapter (see also next example)
        mDevicesAdapter = new AuraListAdapter(DatabaseHelper.getDevices());
        mDevicesRecyclerView.setAdapter(mDevicesAdapter);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }
}

class LyraButtonAdapter extends RecyclerView.Adapter<LyraButtonAdapter.ViewHolder> {
    private String[] mDataset;
    private Context mContext;
    private Device mDevice;
    private final String[] actionTypes= {"Toggle", "On", "Off", "Increase", "Decrease"};


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        private TextView mTextViewNumber;
        private LinearLayout mLayoutDeviceInfoList;
        public ViewHolder(View v) {
            super(v);
            mTextViewNumber = (TextView) v.findViewById(R.id.lyra_cfg_button_number);
            mLayoutDeviceInfoList = (LinearLayout) v.findViewById(R.id.lyra_cfg_button_device_info_list);
        }
    }

    public LyraButtonAdapter(Device device, Context context, String[] myDataset) {
        mContext = context;
        mDataset = myDataset;
        mDevice = device;
    }

    /**
     * Assign given appliance to the given button. The action needs to be taken will be prompted to user.
     */
    private void assignApplianceToButton(final Device device, final ViewHolder vh) {
        AlertDialog.Builder b = new AlertDialog.Builder(this.mContext);
        b.setTitle("What you want todo?");
        b.setItems(actionTypes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int button = (int) vh.mTextViewNumber.getTag();
                mDevice.addAction(button, device.getDeviceInfo().address, which, 0);
                drawActionList(button, vh);
                dialog.dismiss();
            }
        });
        b.show();
    }

    private void drawActionList(int button, ViewHolder vh) {
        LinearLayout linearLayout = vh.mLayoutDeviceInfoList;
        if(linearLayout.getChildCount() > 0) {
            linearLayout.removeAllViews();
        }
        List<DatabaseHelper.DeviceAction> actionList = mDevice.getDeviceActions(button);
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
    public LyraButtonAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.lyra_cfg_button_list_item, parent, false);
        final ViewHolder vh = new ViewHolder(v);

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
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.mTextViewNumber.setText(mDataset[position]);
        holder.mTextViewNumber.setTag(position);
        drawActionList(position, holder);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.length;
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