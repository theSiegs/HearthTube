package com.liskovsoft.smartyoutubetv2.tv;

import android.app.admin.DeviceAdminReceiver;

/**
 * A device admin with no policies (it can't lock, wipe or change anything). It exists because Android won't
 * suspend an active device admin: Google TV suspends every app a kids profile hasn't approved, and HearthTube
 * can't be approved (Google TV only offers Play Store apps). Turned on once by a parent, e.g.
 * {@code adb shell dpm set-active-admin com.thesiegs.hearthtube/com.liskovsoft.smartyoutubetv2.tv.HearthTubeDeviceAdmin}.
 * Bedtime and screen time limits are then up to Hearth and HearthTube, since Google TV can't pause us either.
 */
public class HearthTubeDeviceAdmin extends DeviceAdminReceiver {
}
