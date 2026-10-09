package com.liskovsoft.smartyoutubetv2.tv.ui.profile;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.leanback.widget.Presenter;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ProfilePickerPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ProfilePickerView;
import com.liskovsoft.smartyoutubetv2.common.utils.DailyBackground;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ProfileItem;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ProfileTilePresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

import java.util.List;

import io.reactivex.disposables.Disposable;

public class ProfilePickerActivity extends LeanbackActivity implements ProfilePickerView {
    private static final int BACKGROUND_FADE_MS = 600;
    private ViewGroup mRow;
    private ProfileTilePresenter mTilePresenter;
    private Disposable mBackgroundAction;
    // No account yet: the picker is the welcome, with "Sign in"
    private boolean mWelcome;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_picker);

        mRow = findViewById(R.id.profile_row);
        mTilePresenter = new ProfileTilePresenter(this::onItemClicked);

        ProfilePickerPresenter presenter = ProfilePickerPresenter.instance(this);
        presenter.setView(this);
        presenter.show();

        loadBackground();
    }

    private void loadBackground() {
        mBackgroundAction = DailyBackground.load(this, result -> {
            if (result.image == null) {
                return; // plain dark
            }

            ImageView image = findViewById(R.id.profile_background_image);
            image.setImageBitmap(result.image);
            image.animate().alpha(1f).setDuration(BACKGROUND_FADE_MS).start();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RxHelper.disposeActions(mBackgroundAction);
    }

    @Override
    public void show(List<Account> accounts, List<Drawable> icons, List<Boolean> locked) {
        mRow.removeAllViews();
        mWelcome = accounts.isEmpty();

        TextView title = findViewById(R.id.profile_picker_title);
        TextView hint = findViewById(R.id.profile_picker_hint);

        if (mWelcome) {
            showWelcome(title, hint);
        } else {
            title.setText(R.string.profile_picker_title);
            String linkingProfile = ProfilePickerPresenter.instance(this).getLinkingProfile();
            hint.setVisibility(linkingProfile != null ? View.VISIBLE : View.GONE);
            if (linkingProfile != null) {
                hint.setText(getString(R.string.profile_picker_link_hint, linkingProfile));
            }
        }

        View focusTile = null;

        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            Drawable icon = icons != null && icons.size() == accounts.size() ? icons.get(i) : null;
            boolean isLocked = locked != null && locked.size() == accounts.size() && locked.get(i);

            View tile = addTile(new ProfileItem(ProfileItem.TYPE_ACCOUNT, account, icon, isLocked));

            if (account.isSelected()) {
                focusTile = tile;
            }
        }

        // HearthTube: the guest only when allowed (Accounts settings), never in a kids profile
        boolean guest = com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData.instance(this).isGuestAllowed();
        View guestTile = guest ? addTile(new ProfileItem(ProfileItem.TYPE_GUEST, null, null, false)) : null;
        View addTile = addTile(new ProfileItem(mWelcome ? ProfileItem.TYPE_SIGN_IN : ProfileItem.TYPE_ADD, null, null, false));

        // No account selected means the last one watching was the guest; a welcome starts on "Sign in"
        if (mWelcome) {
            focusTile = addTile;
        } else if (focusTile == null) {
            focusTile = guestTile != null ? guestTile : mRow.getChildCount() > 1 ? mRow.getChildAt(0) : addTile;
        }

        if (focusTile != null) {
            focusTile.requestFocus();
        }
    }

    /** "Hi, Sam" (the Google TV profile, through Hearth) and what signing in is for; kids: a grown-up does it */
    private void showWelcome(TextView title, TextView hint) {
        com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile profile =
                com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile.query(this);
        String name = profile != null ? profile.name : null;
        title.setText(name != null ? getString(R.string.profile_welcome_title_named, name) : getString(R.string.profile_welcome_title));

        boolean kids = com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.isKidsProfile(this);
        hint.setText(kids ? R.string.profile_welcome_hint_kids : R.string.profile_welcome_hint);
        hint.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        boolean guest = com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData.instance(this).isGuestAllowed();

        if (mWelcome && !guest) {
            // Nobody can watch yet, and there's no signed-out Home to fall back to: Back leaves HearthTube
            // (to Hearth when it's the home screen), and the welcome is here again next time
            if (!com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile.goHome(this)) {
                com.liskovsoft.smartyoutubetv2.common.utils.Utils.properlyFinishTheApp(this);
            }
            return;
        }

        super.onBackPressed();
    }

    private View addTile(ProfileItem item) {
        Presenter.ViewHolder holder = mTilePresenter.onCreateViewHolder(mRow);
        mTilePresenter.onBindViewHolder(holder, item);
        mRow.addView(holder.view);
        return holder.view;
    }

    @Override
    public void finishView() {
        finish();
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }

    private void onItemClicked(ProfileItem item) {
        ProfilePickerPresenter presenter = ProfilePickerPresenter.instance(this);

        switch (item.type) {
            case ProfileItem.TYPE_ACCOUNT:
                presenter.onAccountPicked(item.account);
                break;
            case ProfileItem.TYPE_GUEST:
                presenter.onGuestPicked();
                break;
            case ProfileItem.TYPE_ADD:
            case ProfileItem.TYPE_SIGN_IN:
                presenter.onAddAccountPicked();
                break;
        }
    }
}
