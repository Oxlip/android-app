package com.oxlip.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.util.List;

public class DeviceTypeAdapter extends ArrayAdapter<DatabaseHelper.ApplianceType>
{
    List<DatabaseHelper.ApplianceType> applianceTypeList = null;

    public DeviceTypeAdapter(List<DatabaseHelper.ApplianceType> applianceTypeList)
    {
        super(ApplicationGlobals.getAppContext(), R.layout.spinner_appliance_type, applianceTypeList);
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
        Context context = ApplicationGlobals.getAppContext();
        Resources resources = context.getResources();
        DatabaseHelper.ApplianceType applianceType = applianceTypeList.get(position);
        if (applianceType == null)
        {
            return null;
        }

        if(convertView == null)
        {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.spinner_appliance_type, parent, false);
        }

        int imgId =  resources.getIdentifier(applianceType.imageName, "drawable", context.getPackageName());
        ImageView img = (ImageView) convertView.findViewById(R.id.spinner_appliance_image);
        Drawable imgDrawable = resources.getDrawable(imgId);
        imgDrawable.mutate().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        img.setImageDrawable(imgDrawable);

        return convertView;
    }

}

