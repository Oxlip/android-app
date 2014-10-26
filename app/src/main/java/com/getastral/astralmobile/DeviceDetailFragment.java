package com.getastral.astralmobile;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

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
        Activity activity = getActivity();
        Spinner spinner = (Spinner) rootView.findViewById(R.id.dd_lst_connected_device);

        List<DatabaseHelper.ApplianceType> applianceTypeList = DatabaseHelper.getApplianceTypeList();

        final ArrayAdapter<DatabaseHelper.ApplianceType> adapter =
                new ArrayAdapter<DatabaseHelper.ApplianceType>(getActivity().getApplicationContext(),
                        android.R.layout.simple_spinner_dropdown_item, applianceTypeList);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);

        return rootView;
    }
}
