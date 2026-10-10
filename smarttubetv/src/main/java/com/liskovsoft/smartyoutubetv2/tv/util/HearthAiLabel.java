package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.AiSlopList;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * A "Likely AI" pill in the top left corner of a card's thumbnail, for a video from a channel on AiSList
 * ({@link AiSlopList}; kids profiles never see those videos). It floats over the thumbnail, so the card keeps its size
 * and its focus zoom and outline. With Hearth's cards the duration badge is at the top right, under the progress line:
 * the pill sits level with it. In the player the same pill starts the line under the video's title.
 */
public final class HearthAiLabel {
    private HearthAiLabel() {
    }

    /**
     * The player's line under the video's title, with a small "Likely AI" pill in front when its card would have one
     * ({@link AiSlopList#isLabeled}, by the channel ID the player has): never in a kids profile (the player refuses
     * these videos there) and not with labels off. The pill keeps to the line's height, so nothing moves.
     */
    public static CharSequence withPlayerLabel(Context context, Video video, CharSequence subtitle) {
        if (context == null || !AiSlopList.isLabeled(video)) {
            return subtitle;
        }

        SpannableString label = new SpannableString(context.getString(R.string.ai_label));
        label.setSpan(new PillSpan(ContextCompat.getDrawable(context, R.drawable.hearth_ai_label)), 0, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return subtitle == null || subtitle.length() == 0 ? label : TextUtils.concat(label, "  ", subtitle);
    }

    /** Its text a little smaller and white, on the card pill's background, centered in the line */
    private static final class PillSpan extends ReplacementSpan {
        private static final float TEXT_SCALE = 0.8f;
        /** Each side's padding, as a part of the line's text size */
        private static final float PADDING = 0.5f;
        private final Drawable mBackground;

        PillSpan(Drawable background) {
            mBackground = background;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                paint.getFontMetricsInt(fm); // the line's own height: the pill doesn't make it taller
            }

            return Math.round(width(paint, text, start, end));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         @NonNull Paint paint) {
            Paint.FontMetrics line = paint.getFontMetrics();
            float pillTop = y + line.ascent;
            float pillBottom = y + line.descent;

            if (mBackground != null) {
                mBackground.setBounds(Math.round(x), Math.round(pillTop), Math.round(x + width(paint, text, start, end)),
                        Math.round(pillBottom));
                mBackground.draw(canvas);
            }

            TextPaint small = small(paint);
            Paint.FontMetrics metrics = small.getFontMetrics();
            float baseline = (pillTop + pillBottom - metrics.ascent - metrics.descent) / 2;
            canvas.drawText(text, start, end, x + paint.getTextSize() * PADDING, baseline, small);
        }

        private static float width(Paint paint, CharSequence text, int start, int end) {
            return small(paint).measureText(text, start, end) + 2 * paint.getTextSize() * PADDING;
        }

        private static TextPaint small(Paint paint) {
            TextPaint small = new TextPaint(paint);
            small.setTextSize(paint.getTextSize() * TEXT_SCALE);
            small.setColor(Color.WHITE);
            small.setShadowLayer(0, 0, 0, 0); // none: the pill is its own background
            return small;
        }
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
