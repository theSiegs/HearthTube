package com.liskovsoft.smartyoutubetv2.tv;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/**
 * Asks Android to activate {@link HearthTubeDeviceAdmin}, then goes away. Android's confirmation (DeviceAdminAdd)
 * closes unseen unless it knows the caller, which takes startActivityForResult from a standard activity; HearthTube's
 * own screens are singleInstance, whose results Android cancels. Started by KidsProfileAdmin.
 */
public class KidsAdminRequestActivity extends Activity {
    public static final String EXTRA_EXPLANATION = "explanation";
    private static final int REQUEST_ADD_ADMIN = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return; // Already asked; waiting for the answer
        }

        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, HearthTubeDeviceAdmin.class))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getIntent().getStringExtra(EXTRA_EXPLANATION));
        try {
            startActivityForResult(intent, REQUEST_ADD_ADMIN);
        } catch (ActivityNotFoundException | SecurityException e) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        finish();
    }
}
