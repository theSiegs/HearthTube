package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;

import com.liskovsoft.smartyoutubetv2.tv.R;

public class ProfileTilePresenter extends Presenter {
    private static final float FOCUSED_SCALE = 1.1f;
    private static final int FOCUS_ANIM_MS = 150;
    private final OnProfileClick mOnClick;

    public interface OnProfileClick {
        void onClick(ProfileItem item);
    }

    public ProfileTilePresenter(OnProfileClick onClick) {
        mOnClick = onClick;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        // Inflate against the parent so the tile keeps its margins
        View view = LayoutInflater.from(context).inflate(R.layout.profile_tile, parent, false);

        view.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUSED_SCALE : 1f;
            v.animate().scaleX(scale).scaleY(scale)
                    .setDuration(FOCUS_ANIM_MS).setInterpolator(new DecelerateInterpolator()).start();
        });

        view.setOnClickListener(v -> {
            ProfileItem item = (ProfileItem) v.getTag();
            if (item != null && mOnClick != null) {
                mOnClick.onClick(item);
            }
        });

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object object) {
        ProfileItem item = (ProfileItem) object;
        View view = viewHolder.view;
        view.setTag(item);

        ImageView avatar = view.findViewById(R.id.profile_avatar);
        TextView name = view.findViewById(R.id.profile_name);
        ImageView lockBadge = view.findViewById(R.id.profile_lock_badge);

        switch (item.type) {
            case ProfileItem.TYPE_GUEST:
                setIcon(avatar, R.drawable.ic_profile_guest);
                name.setText(R.string.profile_guest);
                lockBadge.setVisibility(View.GONE);
                break;
            case ProfileItem.TYPE_ADD:
            case ProfileItem.TYPE_SIGN_IN:
                setIcon(avatar, R.drawable.ic_profile_add);
                name.setText(item.type == ProfileItem.TYPE_SIGN_IN ? R.string.profile_sign_in : R.string.profile_add_account);
                lockBadge.setVisibility(View.GONE);
                break;
            default:
                avatar.setPadding(0, 0, 0, 0);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatar.setImageDrawable(item.icon); // null is fine - falls back to the placeholder background
                name.setText(item.account != null ? item.account.getName() : null);
                lockBadge.setVisibility(item.locked ? View.VISIBLE : View.GONE);
                break;
        }
    }

    /**
     * Guest and Add account: a small glyph centered on the placeholder circle
     */
    private static void setIcon(ImageView avatar, int iconResId) {
        int padding = avatar.getLayoutParams().width / 4;
        avatar.setPadding(padding, padding, padding, padding);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setImageResource(iconResId);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ImageView avatar = viewHolder.view.findViewById(R.id.profile_avatar);
        avatar.setImageDrawable(null);
    }
}
