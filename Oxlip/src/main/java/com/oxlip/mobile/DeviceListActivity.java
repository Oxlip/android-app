package com.oxlip.mobile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.login.LoginManager;

import studios.codelight.smartloginlibrary.SmartLoginBuilder;
import studios.codelight.smartloginlibrary.SmartLoginConfig;
import studios.codelight.smartloginlibrary.users.SmartFacebookUser;
import studios.codelight.smartloginlibrary.users.SmartGoogleUser;
import studios.codelight.smartloginlibrary.users.SmartUser;

/**
 * An activity representing a list of Devices. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link DeviceDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link DeviceListFragment} and the item details
 * (if present) is a {@link AuraDetailFragment}.
 * <p>
 * This activity also implements the required
 * {@link DeviceListFragment.Callbacks} interface
 * to listen for item selections.
 */
public class DeviceListActivity extends ActionBarActivity
        implements DeviceListFragment.Callbacks, ExtractBitmapFromUrlTaskCompleted {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private Menu mMenu;
    private Bitmap mProfilePic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_list);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_action_logo);

        if (findViewById(R.id.device_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.device_list))
                    .setActivateOnItemClick(true);
        }

        // TODO: If exposing deep links into your app, handle intents here.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        MenuItem profilePictureenuItem = menu.findItem(R.id.main_action_bar_profile_picture);
        MenuItem signinMenuItem =  menu.findItem(R.id.main_action_bar_signin);
        MenuItem signoutMenuItem = menu.findItem(R.id.main_action_bar_signout);
        if (signinMenuItem == null) {
            return true;
        }
        signinMenuItem.setVisible(mProfilePic == null);
        signoutMenuItem.setVisible(mProfilePic != null);
        profilePictureenuItem.setVisible(mProfilePic != null);
        if(mProfilePic != null) {
            Drawable profilePic;
            profilePic = new BitmapDrawable(getResources(), mProfilePic);
            profilePictureenuItem.setIcon(profilePic);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.main_action_bar_signin:
                SmartLoginBuilder loginBuilder = new SmartLoginBuilder();

                Intent intent = loginBuilder.with(this)
                        .setAppLogo(R.drawable.ic_action_logo)
                        .isFacebookLoginEnabled(true).withFacebookAppId("366802223673857")
                        .withFacebookPermissions(null)
                        .isGoogleLoginEnabled(false)
                        .build();
                startActivityForResult(intent, SmartLoginConfig.LOGIN_REQUEST);
                return true;
            case R.id.main_action_bar_signout:
                LoginManager.getInstance().logOut();
                mProfilePic = null;
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Intent "data" contains the user object
        if(resultCode == SmartLoginConfig.FACEBOOK_LOGIN_REQUEST){
            SmartFacebookUser user;
            try {
                user = data.getParcelableExtra(SmartLoginConfig.USER);
                Bundle params = new Bundle();
                params.putBoolean("redirect", false);
                params.putString("type", "large");
                new GraphRequest(
                    AccessToken.getCurrentAccessToken(),
                    user.getUserId() + "/picture",
                    params, HttpMethod.GET,
                    new GraphRequest.Callback() {
                        @Override
                        public void onCompleted(GraphResponse response) {
                            if (response != null) {
                                try {
                                    String picUrl = (String) response.getJSONObject().getJSONObject("data").getString("url");
                                    new ExtractBitmapFromUrlTask(DeviceListActivity.this).execute(picUrl);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).executeAsync();

                //setFacebookProfilePicture(user.getUserId());
                //use this user object as per your requirement
            } catch (Exception e){
                Log.e(getClass().getSimpleName(), e.getMessage());
            }
        }else if(resultCode == SmartLoginConfig.GOOGLE_LOGIN_REQUEST){
            SmartGoogleUser user;
            try {
                user = data.getParcelableExtra(SmartLoginConfig.USER);
                //use this user object as per your requirement
            }catch (Exception e){
                Log.e(getClass().getSimpleName(), e.getMessage());
            }
        }else if(resultCode == SmartLoginConfig.CUSTOM_LOGIN_REQUEST){
            SmartUser user = data.getParcelableExtra(SmartLoginConfig.USER);
            //use this user object as per your requirement
        }else if(resultCode == RESULT_CANCELED){
            //Login Failed
            Log.e(getClass().getSimpleName(), "Login failed");
        }
    }

    /**
     * Callback method from {@link DeviceListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(Device device) {
        String deviceAddress = device.getDeviceInfo().address;
        if (!device.isRegistered()) {
            Toast.makeText(getApplicationContext(), "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            DetailFragment fragment;
            int deviceType = device.getDeviceInfo().deviceType;
            Bundle arguments = new Bundle();
            arguments.putString("deviceAddress", deviceAddress);

            if (deviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_AURA) {
                fragment = new AuraDetailFragment();
            } else if (deviceType == DatabaseHelper.DeviceInfo.DEVICE_TYPE_LYRA) {
                fragment = new LyraDetailFragment();
            } else {
                return;
            }

            fragment.setArguments(arguments);
            getFragmentManager().beginTransaction()
                    .replace(R.id.device_detail_container, fragment)
                    .commit();

        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, DeviceDetailActivity.class);
            detailIntent.putExtra("deviceAddress", deviceAddress);
            detailIntent.putExtra("deviceType", device.getDeviceInfo().deviceType);
            startActivity(detailIntent);
        }
    }

    @Override
    public void onTaskCompleted(Bitmap extractedBitmap) {
        // Calculate ActionBar's height
        int radius = 0;
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            radius = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }

        mProfilePic = ImageHelper.getRoundedBitmap(extractedBitmap, radius, getResources().getColor(R.color.profile_background));
    }
}
