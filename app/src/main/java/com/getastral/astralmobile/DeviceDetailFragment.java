package com.getastral.astralmobile;

import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.LimitLine;

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
        Button btnTest = (Button) rootView.findViewById(R.id.dd_btn_test_data);
        Button btnSave = (Button) rootView.findViewById(R.id.dd_btn_save);
        LineChart chart = (LineChart) rootView.findViewById(R.id.ddl_chart);
        ListView lstDeviceData = (ListView) rootView.findViewById(R.id.dd_lst_device_data);
        String deviceAddress = this.getArguments().getString("deviceAddress");
        final DatabaseHelper.DeviceInfo deviceInfo = DatabaseHelper.getDeviceInfo(deviceAddress);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);

        List<DatabaseHelper.ApplianceType> applianceTypeList = DatabaseHelper.getApplianceTypeList();

        final ArrayAdapter<DatabaseHelper.ApplianceType> adapter =
                new ArrayAdapter<DatabaseHelper.ApplianceType>(getActivity(), android.R.layout.simple_spinner_dropdown_item, applianceTypeList);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        sprApplianceType.setAdapter(adapter);

        ArrayAdapter<DatabaseHelper.DeviceData> ddAdapter = new ArrayAdapter<DatabaseHelper.DeviceData>(getActivity(),
                android.R.layout.simple_list_item_1, DatabaseHelper.getDeviceDataListForDateRange(deviceAddress, c.getTime(), 31));
        lstDeviceData.setAdapter(ddAdapter);

        txtName.setText(deviceInfo.name);
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

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deviceInfo.name = txtName.getText().toString();
                deviceInfo.applianceType = sprApplianceType.getSelectedItem().toString();
                DatabaseHelper.saveDeviceInfo(deviceInfo);

                // close this fragment and go back.
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
                    getActivity().onBackPressed();
                }
            }
        });

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

        setData(chart);
        chart.animateX(2500);

        return rootView;
    }
    private void setData(LineChart chart) {
        int count = 24;
        float range = 100;

        ArrayList<String> xVals = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            xVals.add((i) + "");
        }

        ArrayList<Entry> yVals = new ArrayList<Entry>();

        for (int i = 0; i < count; i++) {
            float mult = (range + 1);
            float val = (float) (Math.random() * mult) + 3;// + (float)
            // ((mult *
            // 0.1) / 10);
            yVals.add(new Entry(val, i));
        }

        // create a dataset and give it a type
        LineDataSet set1 = new LineDataSet(yVals, "DataSet 1");
        // set1.setFillAlpha(110);
        // set1.setFillColor(Color.RED);

        // set the line to be drawn like this "- - - - - -"
        set1.enableDashedLine(10f, 5f, 0f);
        set1.setColor(Color.BLACK);
        set1.setCircleColor(Color.BLACK);
        set1.setLineWidth(1f);
        set1.setCircleSize(4f);
        set1.setFillAlpha(65);
        set1.setFillColor(Color.BLACK);
        // set1.setShader(new LinearGradient(0, 0, 0, mChart.getHeight(),
        // Color.BLACK, Color.WHITE, Shader.TileMode.MIRROR));

        ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
        dataSets.add(set1); // add the datasets

        // create a data object with the datasets
        LineData data = new LineData(xVals, dataSets);

        LimitLine ll1 = new LimitLine(130f);
        ll1.setLineWidth(4f);
        ll1.enableDashedLine(10f, 10f, 0f);
        ll1.setDrawValue(true);
        ll1.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT);

        LimitLine ll2 = new LimitLine(-30f);
        ll2.setLineWidth(4f);
        ll2.enableDashedLine(10f, 10f, 0f);
        ll2.setDrawValue(true);
        ll2.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT);

        data.addLimitLine(ll1);
        data.addLimitLine(ll2);

        // set data
        chart.setData(data);
    }
}
