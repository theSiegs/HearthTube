package com.liskovsoft.smartyoutubetv2.common.app.views;

import android.graphics.drawable.Drawable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.util.List;

public interface ProfilePickerView {
    void show(List<Account> accounts, List<Drawable> icons, List<Boolean> locked);
    void finishView();
}
