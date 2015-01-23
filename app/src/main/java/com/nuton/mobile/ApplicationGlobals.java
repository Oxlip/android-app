package com.nuton.mobile;

import android.app.Application;
import android.content.Context;

public class ApplicationGlobals extends Application {

    private static Context context;

    public void onCreate(){
        super.onCreate();
        ApplicationGlobals.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return ApplicationGlobals.context;
    }
}
