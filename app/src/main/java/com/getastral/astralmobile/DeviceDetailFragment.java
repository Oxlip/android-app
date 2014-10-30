package com.getastral.astralmobile;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_device_detail, container, false);
        final EditText txtName = (EditText) rootView.findViewById(R.id.dd_name);
        final Spinner sprApplianceType = (Spinner) rootView.findViewById(R.id.dd_lst_connected_device);
        Button btnTest = (Button) rootView.findViewById(R.id.dd_btn_test_data);
        Button btnSave = (Button) rootView.findViewById(R.id.dd_btn_save);
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

        return rootView;
    }
}
