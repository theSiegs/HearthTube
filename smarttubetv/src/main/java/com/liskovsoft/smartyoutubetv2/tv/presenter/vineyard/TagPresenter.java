package com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard;

import android.os.Build.VERSION;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.User;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard.TagCardView;

public class TagPresenter extends LongClickPresenter {
    private static int sDefaultBackgroundColor;
    private static int sDefaultTextColor;
    private static int sSelectedBackgroundColor;
    private static int sSelectedTextColor;
    /** HearthTube: search suggestions as Hearth's pills (the chips under the tabs), not black rectangles */
    private static boolean sHearth;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        sDefaultBackgroundColor =
                ContextCompat.getColor(parent.getContext(), Helpers.getThemeAttr(parent.getContext(), R.attr.cardDefaultBackground));
        sDefaultTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_text);
        sSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_background_white);
        sSelectedTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_text_grey);
        sHearth = com.liskovsoft.smartyoutubetv2.tv.util.CardFocusOutline.get(parent.getContext()) != 0;

        TagCardView cardView = new TagCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                if (sHearth) {
                    applyHearthPill(this, selected);
                } else {
                    updateCardBackgroundColor(this, selected);
                    updateCardTextColor(this, selected);
                }
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardBackgroundColor(cardView, false);
        updateCardTextColor(cardView, false);

        if (sHearth) {
            applyHearthPill(cardView, false);
        }
        //if (VERSION.SDK_INT >= 23 && MainUIData.instance(parent.getContext()).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS)) {
        //    cardView.setForeground(ContextCompat.getDrawable(parent.getContext(), R.drawable.lb_card_outline));
        //}
        return new ViewHolder(cardView);
    }

    /**
     * The chips under the tabs: dark glass with white text; the one in focus (leanback marks it selected) white with
     * dark text and the accent ring. Both are set here together so the text is never dark on dark.
     */
    private static void applyHearthPill(TagCardView view, boolean on) {
        float density = view.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(100 * density);

        if (on) {
            android.util.TypedValue accent = new android.util.TypedValue();
            view.getContext().getTheme().resolveAttribute(R.attr.hearthAccent, accent, true);
            pill.setColor(0xFFFFFFFF);
            pill.setStroke(Math.round(3 * density), accent.data);
        } else {
            pill.setColor(0xB3141418);
            pill.setStroke(Math.max(1, Math.round(density)), 0x24FFFFFF);
        }

        view.setBackground(pill);
        view.setTextColor(on ? 0xFF141418 : 0xFFFFFFFF);
    }

    private static void updateCardBackgroundColor(TagCardView view, boolean selected) {
        if (sHearth) {
            view.setBackgroundResource(R.drawable.hearth_chip_background); // follows focus and selection itself
            return;
        }

        view.setBackgroundColor(selected ? sSelectedBackgroundColor : sDefaultBackgroundColor);
    }

    private static void updateCardTextColor(TagCardView view, boolean selected) {
        if (sHearth) {
            return; // the text follows the card's state (setTextColorStates)
        }

        view.setTextColor(selected ? sSelectedTextColor : sDefaultTextColor);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        if (item instanceof Tag) {
            Tag post = (Tag) item;
            TagCardView cardView = (TagCardView) viewHolder.view;

            if (post.tag != null) {
                cardView.setCardText(post.tag);
                //cardView.setCardIcon(R.drawable.ic_tag);
            }
        } else if (item instanceof User) {
            User post = (User) item;
            TagCardView cardView = (TagCardView) viewHolder.view;

            if (post.username != null) {
                cardView.setCardText(post.username);
                cardView.setCardIcon(R.drawable.ic_user);
            }
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }

}