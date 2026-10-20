package com.thesiegs.hearthtube.mobile.signin;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.thesiegs.hearthtube.mobile.R;
import com.thesiegs.hearthtube.mobile.ui.HearthActivity;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;
import com.thesiegs.hearthtube.mobile.ui.ViewTeardown;

/**
 * Signing in: the code to enter at YouTube's activation page (on a kid's phone, from a parent's own device). The
 * shared sign-in presenter waits for it and closes this screen when the account is in.
 */
public class MobileSignInActivity extends HearthActivity implements SignInView {
    private SignInPresenter mPresenter;
    private TextView mUrl;
    private TextView mCode;
    private TextView mOpen;
    private String mFullUrl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // The steps sit on the TV's empty-page card, so they read on any wallpaper
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(pad, pad, pad, pad);
        HearthLook.emptyCard(card);
        card.addView(text(getString(R.string.sign_in_steps), 18, false));
        mUrl = text("", 22, true);
        card.addView(mUrl);
        card.addView(text(getString(R.string.sign_in_code), 18, false));
        mCode = text("...", 40, true);
        card.addView(mCode);

        // On a kid's phone the browser is blocked and a parent signs in from their own device; anyone else can use
        // this one
        mOpen = text(getString(R.string.sign_in_open), 16, true);
        mOpen.setPadding(pad * 2, pad / 2, pad * 2, pad / 2);
        HearthLook.accentButton(mOpen);
        mOpen.setVisibility(View.GONE);
        mOpen.setOnClickListener(v -> openPage());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.topMargin = pad / 2;
        card.addView(mOpen, openParams);
        root.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mPresenter = SignInPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void initDpi() {
    }

    @Override
    protected void initTheme() {
    }

    private TextView text(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 16, 0, 16);
        if (bold) {
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
        }
        return view;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ViewTeardown.destroyed(mPresenter, this);
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        mUrl.setText(signInUrl);
        mCode.setText(userCode);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        showCode(userCode, signInUrl);
        mFullUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;
        mOpen.setVisibility(ParentGate.isKidsProfile(this) ? View.GONE : View.VISIBLE);
    }

    private void openPage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mFullUrl)));
        } catch (ActivityNotFoundException e) {
            // No browser: the code and address on the screen still work from another device
        }
    }

    @Override
    public void close() {
        finish();
    }
}
