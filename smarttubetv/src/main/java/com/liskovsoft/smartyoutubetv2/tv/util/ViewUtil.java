package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.text.TextUtils;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build.VERSION;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridView;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview.MarqueeTextView;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat.MarqueeTextViewCompat;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.speedmarquee.SpeedMarquee;

public class ViewUtil {
    /**
     * Focused card zoom factor
     */
    public static final int FOCUS_ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    //public static final int FOCUS_ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_NONE;
    /**
     * Dim focused card?
     */
    public static final boolean FOCUS_DIMMER_ENABLED = false;
    /**
     * Dim other rows in {@link RowPresenter}
     */
    public static final boolean ROW_SELECT_EFFECT_ENABLED = false;
    /**
     * Scroll continue threshold
     */
    public static final int GRID_SCROLL_CONTINUE_NUM = 10;
    public static final int ROW_SCROLL_CONTINUE_NUM = 4;

    /**
     * Checks whether text is truncated (e.g. has ... at the end)
     */
    public static boolean isTruncated(TextView textView) {
        Layout layout = textView.getLayout();
        if (layout != null) {
            int lines = layout.getLineCount();
            if (lines > 0) {
                int ellipsisCount = layout.getEllipsisCount(lines - 1);
                if (ellipsisCount > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void disableMarquee(TextView... textViews) {
        if (VERSION.SDK_INT <= 19 || textViews == null) { // Android 4: Broken grid layout fix
            return;
        }

        for (TextView textView : textViews) {
            textView.setEllipsize(TruncateAt.END);
            // Line below cause broken grid layout on Android 4 and older
            textView.setHorizontallyScrolling(false);

            applyMarqueeRtlParams(textView, false);
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/3332924/textview-marquee-not-working">More info</a>
     */
    public static void enableMarquee(TextView... textViews) {
        if (VERSION.SDK_INT <= 19 || textViews == null) { // Android 4: Broken grid layout fix
            return;
        }

        for (TextView textView : textViews) {
            if (ViewUtil.isTruncated(textView)) { // multiline scroll fix
                textView.setEllipsize(TruncateAt.MARQUEE);
                textView.setMarqueeRepeatLimit(-1);
                textView.setHorizontallyScrolling(true);

                // App dialog title fix.
                //textView.setSelected(true);

                applyMarqueeRtlParams(textView, true);
            }
        }
    }

    public static void applyMarqueeRtlParams(TextView textView, boolean scroll) {
        if (!Helpers.isTextRTL(textView.getText())) {
            // TextView may be reused from rtl context. Do reset.
            // NOTE: don't enable commented options because Setting item's text won't be centered.
            //textView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            textView.setTextDirection(View.TEXT_DIRECTION_LTR);
            textView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            //textView.setGravity(Gravity.TOP | Gravity.START);
            return;
        }

        if (scroll) {
            // Fix: right scrolling on rtl languages
            // Fix: text disappear on rtl languages
            textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            textView.setTextDirection(View.TEXT_DIRECTION_RTL);
            textView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            textView.setGravity(Gravity.START);
        } else {
            // Fix: text disappear on rtl languages
            textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
    }

    public static void setTextScrollSpeed(TextView textView, float speed) {
        if (VERSION.SDK_INT <= 19) { // Android 4: Broken grid layout fix
            return;
        }

        if (textView instanceof MarqueeTextViewCompat) {
            ((MarqueeTextViewCompat) textView).setMarqueeSpeedFactor(speed);
        } else if (textView instanceof MarqueeTextView) {
            ((MarqueeTextView) textView).setMarqueeSpeedFactor(speed);
        } else if (textView instanceof SpeedMarquee) {
            ((SpeedMarquee) textView).setSpeed(speed);
        }
    }

    public static void enableView(View view, boolean enabled) {
        if (view != null) {
            view.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    public static void setDimensions(View view, int width, int height) {
        if (view != null) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();

            if (lp != null) {
                if (width > 0) {
                    lp.width = width;
                }
                if (height > 0) {
                    lp.height = height;
                }
                view.setLayoutParams(lp);
            }
        }
    }

    public static boolean isListRowEmpty(Object obj) {
        if (obj instanceof ListRow) {
            ListRow row = (ListRow) obj;
            // Any adapter: HearthTube's rows show other kinds too
            return row.getAdapter() == null || row.getAdapter().size() == 0;
        }

        return true;
    }

    public static RequestOptions glideOptions() {
        return new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE) // ensure start animation from beginning
                .skipMemoryCache(true); // ensure start animation from beginning
    }

    public static void enableTransparentDialog(Context context, View rootView) {
        if (context == null || rootView == null || VERSION.SDK_INT <= 19) {
            return;
        }

        // Usually null. Present only on parent fragment.
        View mainContainer = rootView.findViewById(R.id.settings_preference_fragment_container);
        View mainFrame = rootView.findViewById(R.id.main_frame);
        View itemsContainer = rootView.findViewById(R.id.list);
        View title = rootView.findViewById(R.id.decor_title_container);
        int transparent = ContextCompat.getColor(context, R.color.transparent);
        int semiTransparent = ContextCompat.getColor(context, R.color.semi_grey);

        // Disable shadow outline on parent fragment
        if (mainContainer instanceof FrameLayout && VERSION.SDK_INT >= 21) {
            // ViewOutlineProvider: NoClassDefFoundError on API 19
            mainContainer.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        if (mainFrame instanceof LinearLayout) {
            mainFrame.setBackgroundColor(transparent);
        }
        if (itemsContainer instanceof VerticalGridView) {
            // Set background for individual buttons in the list.
            // This is the only way to do this because items haven't been added yet to the container.
            ((VerticalGridView) itemsContainer).setOnChildLaidOutListener(
                    (parent, view, position, id) -> view.setBackgroundResource(R.drawable.transparent_dialog_item_bg)
            );
        }
        if (title instanceof FrameLayout) {
            title.setBackgroundColor(transparent);
            title.setVisibility(View.GONE);
        }
    }

    /**
     * HearthTube: the app's logo above a centered title, then a divider, like the Hearth launcher's Settings panel.
     */
    public static void addLogoHeader(View rootView, int logoResId) {
        View mainFrame = rootView.findViewById(R.id.main_frame);
        View titleContainer = rootView.findViewById(R.id.decor_title_container);

        if (!(mainFrame instanceof LinearLayout) || titleContainer == null) {
            return;
        }

        LinearLayout frame = (LinearLayout) mainFrame;
        Context context = rootView.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        ImageView logo = new ImageView(context);
        logo.setImageResource(logoResId);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription(context.getString(R.string.app_name));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(56 * density));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.topMargin = Math.round(20 * density);
        frame.addView(logo, 0, logoParams);

        TextView title = rootView.findViewById(R.id.decor_title);
        if (title != null) {
            title.setGravity(Gravity.CENTER);
            // A panel without a title (the side menu): just the logo, then the divider
            title.post(() -> titleContainer.setVisibility(TextUtils.isEmpty(title.getText()) ? View.GONE : View.VISIBLE));
        }

        View divider = new View(context);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density)));
        dividerParams.leftMargin = Math.round(16 * density);
        dividerParams.rightMargin = Math.round(16 * density);
        dividerParams.bottomMargin = Math.round(8 * density);
        frame.addView(divider, frame.indexOfChild(titleContainer) + 1, dividerParams);
    }

    /**
     * HearthTube: a search bar (Search, and a channel's page) as one of Hearth's dark pills with readable white text,
     * the search button then the filter button right after it, in that order on both screens.
     */
    public static void applyHearthSearchBar(View root) {
        float density = root.getResources().getDisplayMetrics().density;

        View items = root.findViewById(androidx.leanback.R.id.lb_search_bar_items);
        if (items != null) {
            items.setBackgroundResource(R.drawable.hearth_tab_bar_background);
        }

        android.widget.EditText editor = root.findViewById(androidx.leanback.R.id.lb_search_text_editor);
        if (editor != null) {
            editor.setTextColor(0xFFFFFFFF);
            editor.setHintTextColor(0x99FFFFFF);
            editor.setShadowLayer(4 * density, 0, 2 * density, 0x8A000000);
        }

        // Search and filter buttons right after the bar (not at the far right edge)
        View searchOrb = root.findViewById(R.id.lb_search_bar_search_orb);
        View settingsOrb = root.findViewById(R.id.search_settings_orb);
        if (searchOrb != null && searchOrb.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams
                && settingsOrb != null && settingsOrb.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
            android.widget.RelativeLayout.LayoutParams search = (android.widget.RelativeLayout.LayoutParams) searchOrb.getLayoutParams();
            search.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
            search.removeRule(android.widget.RelativeLayout.START_OF);
            search.removeRule(android.widget.RelativeLayout.END_OF);
            search.addRule(android.widget.RelativeLayout.END_OF, androidx.leanback.R.id.lb_search_bar_items);
            search.setMarginStart(Math.round(12 * density));
            searchOrb.setLayoutParams(search);

            android.widget.RelativeLayout.LayoutParams settings = (android.widget.RelativeLayout.LayoutParams) settingsOrb.getLayoutParams();
            settings.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
            settings.removeRule(android.widget.RelativeLayout.START_OF);
            settings.removeRule(android.widget.RelativeLayout.END_OF);
            settings.addRule(android.widget.RelativeLayout.END_OF, R.id.lb_search_bar_search_orb);
            settings.setMarginStart(Math.round(12 * density));
            settingsOrb.setLayoutParams(settings);
        }
    }

    /**
     * HearthTube: a panel's list fades out under its header instead of cutting a row in half, and starts a little
     * below it (a long two-line title no longer runs into the first row).
     */
    public static void fadeListTop(View rootView) {
        View list = findList(rootView);

        if (list == null) {
            return;
        }

        float density = rootView.getResources().getDisplayMetrics().density;
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop() + Math.round(12 * density), list.getPaddingRight(), list.getPaddingBottom());

        // Leanback's grid reports no scroll position, so Android's own fading edge never shows: a short gradient in
        // the panel's color over the list's top edge does the same
        if (list.getParent() instanceof FrameLayout) {
            View fade = new View(rootView.getContext());
            fade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF0F0F0F, 0x000F0F0F}));
            ((FrameLayout) list.getParent()).addView(fade,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(24 * density), Gravity.TOP));
        }
    }

    private static View findList(View view) {
        if (view instanceof androidx.recyclerview.widget.RecyclerView) {
            return view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View list = findList(group.getChildAt(i));
                if (list != null) {
                    return list;
                }
            }
        }

        return null;
    }

    public static void enableLeftDialog(Context context, View rootView) {
        if (context == null || rootView == null || VERSION.SDK_INT <= 19) {
            return;
        }

        // Usually null. Present only on parent fragment.
        View mainContainer = rootView.findViewById(R.id.settings_preference_fragment_container);

        if (mainContainer instanceof FrameLayout) {
            ((FrameLayout.LayoutParams) mainContainer.getLayoutParams()).gravity = Gravity.START;
        }
    }

    public static void makeMonochrome(ImageView iconView) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        iconView.setColorFilter(filter);
    }

    public static void setGravity(View view, int gravity) {
        if (view == null) {
            return;
        }

        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            flp.gravity = gravity;
            view.setLayoutParams(flp);
        }
    }

    public static void setWidth(View view, int width) {
        if (view == null) {
            return;
        }

        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.width = width;
            view.setLayoutParams(lp);
        }
    }

    public static void setPadding(View view, int padding) {
        if (view == null) {
            return;
        }

        view.setPadding(padding, view.getPaddingTop(), padding, view.getPaddingBottom());
    }

    /**
     * Fix SDK 28+ GridLayoutManager broken navigation when using Japanese fonts
     */
    public static void fixApi28BrokenGridNavigation(TextView textView) {
        if (VERSION.SDK_INT >= 28) {
            // 1. Disable dynamic line spacing for special characters (prevents expansion for CJK glyphs)
            textView.setFallbackLineSpacing(false);
        }

        // 2. Remove system font padding to ensure consistent baseline and height
        textView.setIncludeFontPadding(false);

        // 3. Add fixed internal padding to create a "safe zone" for both Latin and Japanese text
        int paddingExtra = (int) (4 * textView.getResources().getDisplayMetrics().density);
        textView.setPadding(textView.getPaddingLeft(), paddingExtra, textView.getPaddingRight(), paddingExtra);
    }
}
