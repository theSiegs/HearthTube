package com.liskovsoft.smartyoutubetv2.tv.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.leanback.widget.BaseCardView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

/**
 * Video cards shaped like the Hearth launcher's Continue Watching cards: a plain 16:9 rectangle with
 * the title and channel on a dark gradient over the bottom of the thumbnail, instead of a text block
 * below it. The duration and progress move to the top, where Hearth shows its progress badge.
 */
public final class HearthCardStyle {
    /** The focus outline's width: hearth_card_focus_outline.xml */
    private static final int OUTLINE_DP = 4;
    private static final int BAR_DP = 4;

    private HearthCardStyle() {
    }

    private static int dp(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    public static void apply(ComplexImageCardView card) {
        card.setCardType(BaseCardView.CARD_TYPE_INFO_OVER);
        card.setTitleLinesNum(1);
        card.setContentLinesNum(1);

        View clipInfo = card.findViewById(R.id.clip_info);

        if (clipInfo == null || !(clipInfo.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) clipInfo.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        clipInfo.setLayoutParams(params);

        // Progress along the top edge, the duration badge just below it on the right
        View progress = clipInfo.findViewById(R.id.clip_progress);
        View badge = clipInfo.findViewById(R.id.extra_text_badge);

        if (progress != null && clipInfo instanceof LinearLayout) {
            ((ViewGroup) clipInfo).removeView(progress);
            ((ViewGroup) clipInfo).addView(progress, 0);

            // Inside the focus outline (2dp accent and 2dp black over the card's edge), which would cover it on the
            // focused card, and thick enough that a few minutes into a long video still shows
            if (progress.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams bar = (ViewGroup.MarginLayoutParams) progress.getLayoutParams();
                int inset = dp(card, OUTLINE_DP);
                bar.setMargins(inset, inset, inset, 0);
                bar.height = dp(card, BAR_DP);
                progress.setLayoutParams(bar);
            }
        }

        if (badge != null && badge.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            int margin = card.getResources().getDimensionPixelSize(R.dimen.lb_basic_card_info_badge_margin);
            ((ViewGroup.MarginLayoutParams) badge.getLayoutParams()).topMargin = margin;
        }
    }
}
