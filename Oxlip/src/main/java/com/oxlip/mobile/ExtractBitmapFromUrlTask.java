package com.oxlip.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import java.net.URL;

/**
 * Extracts a image/icon from given URL and returns the bitmap.
 *
 * The extraction is done as a async task and results is posted to the listener through
 * ExtractBitmapFromUrlTaskCompleted interface.
 */

public class ExtractBitmapFromUrlTask extends AsyncTask<String, Void, String> {
    private ExtractBitmapFromUrlTaskCompleted listener;

    public ExtractBitmapFromUrlTask(ExtractBitmapFromUrlTaskCompleted listener){
        this.listener = listener;
    }

    public String doInBackground(String... urls) {
        for(String url:urls) {
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(new URL(url).openConnection().getInputStream());
                listener.onTaskCompleted(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
