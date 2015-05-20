package com.nuton.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.makeramen.RoundedImageView;

/**
 * Helper class to transform appliance image into a roundedImageView.
 */
public class ApplianceImage {
    public static void transformImage(View rootView, int imgId, int imgViewId) {
        Context context = ApplicationGlobals.getAppContext();

        RoundedImageView riv = (RoundedImageView) rootView.findViewById(imgViewId);
        riv.setBackgroundColor(Color.GRAY);
        riv.setBorderColor(Color.DKGRAY);

        Drawable imgDrawable = context.getResources().getDrawable(imgId);
        if (imgDrawable == null) {
            return;
        }

        imgDrawable.mutate().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
        riv.setImageDrawable(imgDrawable);

    }

    public static void loadApplianceImage(View rootView, String imageName, int imgViewId) {
        Context context = ApplicationGlobals.getAppContext();
        int imgId =  context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
        transformImage(rootView, imgId, imgViewId);
    }
}
