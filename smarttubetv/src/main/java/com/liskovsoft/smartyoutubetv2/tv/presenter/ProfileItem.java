package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.graphics.drawable.Drawable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

public class ProfileItem {
    public static final int TYPE_ACCOUNT = 0;
    // 1 was the guest, gone
    public static final int TYPE_ADD = 2;
    /** The welcome's "Sign in" (no account yet): the add tile, worded for a first sign-in */
    public static final int TYPE_SIGN_IN = 3;

    public final int type;
    public final Account account;
    public final Drawable icon;
    public final boolean locked;

    public ProfileItem(int type, Account account, Drawable icon, boolean locked) {
        this.type = type;
        this.account = account;
        this.icon = icon;
        this.locked = locked;
    }
}
