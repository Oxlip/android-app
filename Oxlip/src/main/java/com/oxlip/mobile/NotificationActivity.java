package com.oxlip.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class NotificationActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DFUNotificationActivity", "create");

        // Now finish, which will drop the user in to the activity that was at the top
        //  of the task stack
        finish();
    }
}
