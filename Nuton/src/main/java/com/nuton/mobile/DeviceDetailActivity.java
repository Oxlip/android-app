package com.nuton.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.List;


/**
 * An activity representing a single Device detail screen. This
 * activity is only used on handset devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link DeviceListActivity}.
 * <p>
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a {@link AuraDetailFragment}.
 */
public class DeviceDetailActivity extends ActionBarActivity {

    private DetailFragment mFragment;
    private String mDeviceAddress;
    private int mDeviceType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        mDeviceAddress = getIntent().getExtras().getString("deviceAddress");
        mDeviceType =  getIntent().getExtras().getInt("deviceType");
        populateActionBar();

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            if (mDeviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA) {
                mFragment = new AuraDetailFragment();
            } else if (mDeviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_LYRA) {
                mFragment = new LyraDetailFragment();
            } else {
                return;
            }

            Bundle arguments = new Bundle();
            arguments.putString("deviceAddress", mDeviceAddress);

            mFragment.setArguments(arguments);
            getFragmentManager().beginTransaction()
                    .add(R.id.device_detail_container, mFragment)
                    .commit();
        }
    }

    private void populateApplianceType(View view, DatabaseHelper.DeviceInfo deviceInfo) {
        /* Fill appliance type */
        List<DatabaseHelper.ApplianceType> applianceTypeList = DatabaseHelper.getApplianceTypeList();
        DeviceTypeAdapter adapter = new DeviceTypeAdapter(applianceTypeList);
        adapter.setDropDownViewResource(R.layout.spinner_appliance_type);

        final Spinner sprApplianceType = (Spinner) view.findViewById(R.id.action_bar_device_type);
        sprApplianceType.setAdapter(adapter);

        int position = 0;
        if (deviceInfo.applianceType != null) {
            for (DatabaseHelper.ApplianceType applianceType: applianceTypeList) {
                if (applianceType.name.equals(deviceInfo.applianceType)) {
                    break;
                }
                position++;
            }
        }
        sprApplianceType.setSelection(position);
    }

    /**
     * Fill the action bar with Text box for appliance name and spinner for appliance type.
     */
    private void populateActionBar() {
        /*Inflate the custom view*/
        ActionBar actionBar = getSupportActionBar();
        actionBar.setCustomView(R.layout.actionbar_device_detail);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setDisplayHomeAsUpEnabled(true);

        View view = actionBar.getCustomView();
        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(mDeviceAddress);

        final EditText txtName = (EditText) view.findViewById(R.id.action_bar_device_name);
        txtName.setText(deviceInfo.name);

        // populate spinner with home appliances(tv, lamp etc) for Aura
        if (mDeviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA) {
            populateApplianceType(view, deviceInfo);
        }
    }

    // when back is pressed update the information in database
    @Override
    public void onBackPressed() {
        super.onBackPressed();


        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        View view = actionBar.getCustomView();
        final EditText txtName = (EditText) view.findViewById(R.id.action_bar_device_name);

        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(mDeviceAddress);
        deviceInfo.name = txtName.getText().toString();
        if (mDeviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA) {
            final Spinner sprApplianceType = (Spinner) view.findViewById(R.id.action_bar_device_type);
            deviceInfo.applianceType = sprApplianceType.getSelectedItem().toString();
        }

        DatabaseHelper.saveDeviceInfo(deviceInfo);
        DeviceListAdapter.getInstance().notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(new Intent(this, DeviceListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
