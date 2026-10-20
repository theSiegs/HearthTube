package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;

public class SignInError implements ErrorFragmentData {
    private final Context mContext;
    private final boolean mAskParent;

    public SignInError(Context context) {
        this(context, false);
    }

    /** @param askParent a kids profile: says a parent signs in (the sign-in itself asks for the PIN) */
    public SignInError(Context context, boolean askParent) {
        mContext = context;
        mAskParent = askParent;
    }

    @Override
    public void onAction() {
        YTSignInPresenter.instance(mContext).start();
    }

    @Override
    public String getMessage() {
        return mContext.getString(mAskParent ? R.string.kids_sign_in_needed : R.string.library_signin_to_show_more);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.action_signin);
    }
}
