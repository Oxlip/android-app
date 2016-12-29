package com.oxlip.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.makeramen.RoundedImageView;

/**
 * Helper class to transform appliance image into a roundedImageView.
 */
public class ImageHelper {
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

    /**
     * Converts given bitmap into rounded one by cropping the bitmap with given radius.
     *
     * @param bmp Bitmap to be converted.
     * @param radius Radius of the cut.
     * @param backgroundColor Backgroud color to fill the cropped image corners.
     *
     * @return Converted bitmap.
     */
    public static Bitmap getRoundedBitmap(Bitmap bmp, int radius, int backgroundColor) {
        Bitmap sbmp;

        if (bmp.getWidth() != radius || bmp.getHeight() != radius) {
            float smallest = Math.min(bmp.getWidth(), bmp.getHeight());
            float factor = smallest / radius;
            sbmp = Bitmap.createScaledBitmap(bmp,
                    (int) (bmp.getWidth() / factor),
                    (int) (bmp.getHeight() / factor), false);
        } else {
            sbmp = bmp;
        }

        Bitmap output = Bitmap.createBitmap(radius, radius, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, radius, radius);

        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(backgroundColor);
        canvas.drawCircle(radius / 2 + 0.7f, radius / 2 + 0.7f, radius / 2 + 0.1f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(sbmp, rect, rect, paint);

        return output;
    }
}
