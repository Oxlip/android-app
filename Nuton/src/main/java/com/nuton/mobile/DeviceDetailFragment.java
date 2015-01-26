package com.nuton.mobile;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

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
public class DeviceDetailFragment extends Fragment {
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public DeviceDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_device_detail, container, false);

        final EditText txtName = (EditText) rootView.findViewById(R.id.dd_name);
        final Spinner sprApplianceType = (Spinner) rootView.findViewById(R.id.dd_lst_connected_device);
        String deviceAddress = this.getArguments().getString("deviceAddress");
        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(deviceAddress);

        txtName.setText(deviceInfo.name);

        List<DatabaseHelper.ApplianceType> applianceTypeList = DatabaseHelper.getApplianceTypeList();

        DeviceTypeAdapter adapter = new DeviceTypeAdapter(R.layout.spinner_appliance_type, applianceTypeList);
        adapter.setDropDownViewResource(R.layout.spinner_appliance_type);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item );
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

        at.markushi.ui.CircleButton circleButton = (at.markushi.ui.CircleButton)rootView.findViewById(R.id.dd_btn_update_firmware);
        circleButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DfuService dfuService = new DfuService();
                dfuService.updateFirmware(deviceInfo);
            }
        });

        Button btnTest = (Button) rootView.findViewById(R.id.dd_btn_test_data);
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

        BarChart chart = (BarChart) rootView.findViewById(R.id.ddl_chart);
        setChart(chart);
        chart.animateX(2500);

        return rootView;
    }

    public void onBackPressed(String deviceAddress)
    {
        View view = getView();
        final EditText txtName = (EditText) view.findViewById(R.id.dd_name);
        final Spinner sprApplianceType = (Spinner) view.findViewById(R.id.dd_lst_connected_device);

        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(deviceAddress);
        deviceInfo.name = txtName.getText().toString();
        deviceInfo.applianceType = sprApplianceType.getSelectedItem().toString();
        DatabaseHelper.saveDeviceInfo(deviceInfo);
        DeviceListAdapter.getInstance().notifyDataSetChanged();
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

    public class DeviceTypeAdapter extends ArrayAdapter<DatabaseHelper.ApplianceType>
    {
        List<DatabaseHelper.ApplianceType> applianceTypeList = null;

        public DeviceTypeAdapter(int resource, List<DatabaseHelper.ApplianceType> applianceTypeList)
        {
            super(ApplicationGlobals.getAppContext(), resource, applianceTypeList);
            this.applianceTypeList = applianceTypeList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {   // Ordinary view in Spinner, we use android.R.layout.simple_spinner_item
            return getDropDownView(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent)
        {
            DatabaseHelper.ApplianceType applianceType = applianceTypeList.get(position);
            if (applianceType == null)
            {
                return null;
            }

            if(convertView == null)
            {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                convertView = inflater.inflate(R.layout.spinner_appliance_type, parent, false);
            }
            TextView txtView = (TextView)convertView.findViewById(R.id.spinner_appliance_type_name);
            txtView.setText(applianceType.name);

            int imgId = getResources().getIdentifier(applianceType.imageName, "drawable", getActivity().getPackageName());
            ImageView img = (ImageView) convertView.findViewById(R.id.spinner_appliance_image);
            Drawable imgDrawable = getResources().getDrawable(imgId);
            imgDrawable.mutate().setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP);
            img.setImageDrawable(imgDrawable);

            return convertView;
        }

    }
}
