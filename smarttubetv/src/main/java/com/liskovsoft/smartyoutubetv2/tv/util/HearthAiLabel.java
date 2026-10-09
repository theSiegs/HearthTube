package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.AiSlopList;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * A "Likely AI" pill in the top left corner of a card's thumbnail, for a video from a channel on AiSList
 * ({@link AiSlopList}; kids profiles never see those videos). It floats over the thumbnail, so the card keeps its size
 * and its focus zoom and outline. With Hearth's cards the duration badge is at the top right, under the progress line:
 * the pill sits level with it.
 */
public final class HearthAiLabel {
    private HearthAiLabel() {
    }

    public static void bind(View card, Video video, boolean hearthStyle) {
        View wrapper = card != null ? card.findViewById(R.id.main_image_wrapper) : null;

        if (!(wrapper instanceof RelativeLayout)) {
            return;
        }

        TextView label = wrapper.findViewById(R.id.hearth_ai_label);
        boolean show = AiSlopList.isLabeled(video);

        if (!show) {
            if (label != null) {
                label.setVisibility(View.GONE);
            }
            return;
        }

        if (label == null) {
            label = create(wrapper.getContext(), hearthStyle);
            ((ViewGroup) wrapper).addView(label);
        }

        label.setVisibility(View.VISIBLE);
    }

    private static TextView create(Context context, boolean hearthStyle) {
        TextView label = new TextView(context);
        label.setId(R.id.hearth_ai_label);
        label.setText(R.string.ai_label);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.card_badge_text_size) * 0.85f);
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setBackgroundResource(R.drawable.hearth_ai_label);
        int padH = dp(context, 7);
        int padV = dp(context, 2);
        label.setPadding(padH, padV, padH, padV);

        // Same inset as the duration badge; with Hearth's cards below the progress line, like that badge
        int margin = context.getResources().getDimensionPixelSize(R.dimen.lb_basic_card_info_badge_margin);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_START);
        params.topMargin = hearthStyle ? 2 * margin : margin;
        params.setMarginStart(margin);
        label.setLayoutParams(params);

        return label;
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
