package com.thesiegs.hearthtube.mobile.dialogs;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.thesiegs.hearthtube.mobile.ui.HearthActivity;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HearthTube Mobile's version of every settings screen, menu and picker the shared presenters put in an
 * {@link AppDialogView}: a menu of rows (switches and buttons inline, lists opening their own screen), then Back goes
 * up one screen and, from the first, closes. The TV renders the same categories as Leanback preferences.
 */
public class MobileDialogActivity extends HearthActivity implements AppDialogView {
    private static final int TEXT = Color.WHITE;
    private static final int SUBTEXT = 0xFFBBBBBB;

    /** One screen of the dialog: a menu of categories, or the options of one list category */
    private static final class Screen {
        final List<OptionCategory> categories;
        final OptionCategory list;
        final CharSequence title;

        Screen(List<OptionCategory> categories, OptionCategory list, CharSequence title) {
            this.categories = categories;
            this.list = list;
            this.title = title;
        }
    }

    private final List<Screen> mStack = new ArrayList<>();
    /** What was picked in each radio list on this screen: the options themselves only hear about the one chosen */
    private final Map<OptionCategory, OptionItem> mPicked = new HashMap<>();
    private AppDialogPresenter mPresenter;
    private LinearLayout mBody;
    private TextView mTitle;
    private boolean mPaused;
    private boolean mFinished;
    private boolean mTransparent;
    private boolean mOverlay;
    private int mId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(createContent());

        mPresenter = AppDialogPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized(); // asks us to show()
    }

    @Override
    protected void initDpi() {
    }

    @Override
    protected void initTheme() {
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // The wallpaper stays behind it, dimmed (Hearth's panels dim what's behind them)
        root.setBackgroundColor(0x80000000);

        // A centered title over a hairline, like the TV's panels
        mTitle = new TextView(this);
        mTitle.setTextColor(TEXT);
        mTitle.setTextSize(22);
        mTitle.setGravity(android.view.Gravity.CENTER);
        mTitle.setPadding(dp(20), dp(20), dp(20), dp(12));
        HearthLook.textShadow(mTitle);
        root.addView(mTitle);

        View divider = new View(this);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        dividerParams.setMargins(dp(20), 0, dp(20), dp(8));
        root.addView(divider, dividerParams);

        ScrollView scroll = new ScrollView(this);
        mBody = new LinearLayout(this);
        mBody.setOrientation(LinearLayout.VERTICAL);
        mBody.setPadding(dp(12), 0, dp(12), dp(24));
        scroll.addView(mBody);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
    }

    /**
     * A dialog was opened while this one is showing: the presenter has already put it on the stack ({@link #show}); the
     * system only brings the one activity forward again (ViewManager starts it single-top), where a second activity
     * would have hidden the first page
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (canGoBack()) {
            goBack();
        } else {
            finish();
        }
    }

    /**
     * Closing by Back or by the presenter: tell it once, so callbacks (a dialog's "on finish") run. That also clears
     * the presenter (what its "view destroyed" would do), so onDestroy has nothing left to tell it.
     */
    @Override
    public void finish() {
        if (!mFinished) {
            mFinished = true;
            mPresenter.onFinish();
        }

        finishReally();
    }

    // AppDialogView

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable, boolean isTransparent, boolean isOverlay, int id) {
        // Only the first page decides what the dialog is (the TV's panels do the same)
        if (mStack.isEmpty()) {
            mTransparent = isTransparent;
        }
        mOverlay = isOverlay;
        mId = id;

        Screen screen;

        if (isExpandable && categories != null && categories.size() == 1 && isList(categories.get(0))) {
            // One list asked for directly: its options are the screen
            screen = new Screen(null, categories.get(0), categories.get(0).title != null ? categories.get(0).title : title);
        } else {
            screen = new Screen(categories, null, title);
        }

        // One dialog opened from another stacks on it: Back goes to the page it was opened from, like the TV's panels
        mStack.add(screen);
        render(screen);
    }

    @Override
    public void goBack() {
        if (canGoBack()) {
            mStack.remove(mStack.size() - 1);
            render(mStack.get(mStack.size() - 1));
        } else {
            finish();
        }
    }

    @Override
    public void clearBackstack() {
        while (mStack.size() > 1) {
            mStack.remove(0);
        }
    }

    @Override
    public boolean canGoBack() {
        return mStack.size() > 1;
    }

    @Override
    public boolean isShown() {
        return !mPaused && !mFinished;
    }

    @Override
    public boolean isTransparent() {
        return mTransparent;
    }

    @Override
    public boolean isOverlay() {
        return mOverlay;
    }

    @Override
    public boolean isPaused() {
        return mPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }

    // Rendering

    private void open(Screen screen) {
        mStack.add(screen);
        render(screen);
    }

    private void render(Screen screen) {
        mTitle.setText(screen.title);
        mTitle.setVisibility(TextUtils.isEmpty(screen.title) ? View.GONE : View.VISIBLE);
        mBody.removeAllViews();

        if (screen.list != null) {
            renderOptions(screen.list);
        } else if (screen.categories != null) {
            for (OptionCategory category : screen.categories) {
                if (category.options != null) {
                    renderCategory(category);
                }
            }
        }
    }

    private void renderCategory(OptionCategory category) {
        switch (category.type) {
            case OptionCategory.TYPE_SINGLE_SWITCH:
                addSwitch(category.options.get(0));
                break;
            case OptionCategory.TYPE_SINGLE_BUTTON:
                addButton(category.options.get(0));
                break;
            case OptionCategory.TYPE_RADIO_LIST:
            case OptionCategory.TYPE_CHECKBOX_LIST:
            case OptionCategory.TYPE_STRING_LIST:
            case OptionCategory.TYPE_LONG_TEXT:
                addRow(category.title, summaryOf(category), v -> open(new Screen(null, category, category.title)));
                break;
            default:
                // Chat and comments are the player's own panels: not on the phone yet
                break;
        }
    }

    private void renderOptions(OptionCategory category) {
        switch (category.type) {
            case OptionCategory.TYPE_RADIO_LIST:
                renderRadio(category);
                break;
            case OptionCategory.TYPE_CHECKBOX_LIST:
                renderChecks(category);
                break;
            case OptionCategory.TYPE_STRING_LIST:
                for (OptionItem item : category.options) {
                    addRow(item.getTitle(), item.getDescription(), v -> {
                        item.onSelect(true);
                    });
                }
                break;
            case OptionCategory.TYPE_LONG_TEXT:
                TextView text = new TextView(this);
                text.setTextColor(TEXT);
                text.setTextSize(16);
                text.setPadding(dp(8), dp(8), dp(8), dp(8));
                text.setText(category.options.get(0).getTitle());
                mBody.addView(text);
                break;
            default:
                break;
        }
    }

    /** One choice among several: picking one clears the rest and takes you back to the menu it came from */
    private void renderRadio(OptionCategory category) {
        List<RadioButton> buttons = new ArrayList<>();

        for (OptionItem item : category.options) {
            RadioButton button = new RadioButton(this);
            styleChoice(button, item);
            button.setChecked(item == pickedIn(category));
            button.setOnClickListener(v -> {
                for (RadioButton other : buttons) {
                    other.setChecked(other == button);
                }
                mPicked.put(category, item);
                item.onSelect(true);

                if (canGoBack()) {
                    goBack();
                }
            });
            buttons.add(button);
            mBody.addView(button, rowParams());
        }
    }

    private void renderChecks(OptionCategory category) {
        List<CheckBox> boxes = new ArrayList<>();

        for (OptionItem item : category.options) {
            CheckBox box = new CheckBox(this);
            styleChoice(box, item);
            box.setChecked(item.isSelected());
            box.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
                if (item.isSelected() == checked) {
                    return;
                }

                if (checked) {
                    // Choosing one of a group (radio items) clears the others in it
                    OptionItem[] radio = item.getRadio();
                    if (radio != null) {
                        for (OptionItem other : radio) {
                            other.onSelect(false);
                        }
                        syncChecks(category, boxes);
                    }
                }

                item.onSelect(checked);
            });
            boxes.add(box);
            mBody.addView(box, rowParams());
        }
    }

    /** Items cleared behind our back (a radio group) are cleared on screen too */
    private void syncChecks(OptionCategory category, List<CheckBox> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            boolean selected = category.options.get(i).isSelected();
            if (boxes.get(i).isChecked() != selected) {
                boxes.get(i).setChecked(selected);
            }
        }
    }

    /** The option picked in a radio list: this session's pick, else the one the presenter marked */
    @Nullable
    private OptionItem pickedIn(OptionCategory category) {
        OptionItem picked = mPicked.get(category);

        if (picked != null) {
            return picked;
        }

        for (OptionItem item : category.options) {
            if (item.isSelected()) {
                return item;
            }
        }

        return null;
    }

    private void styleChoice(CompoundButton button, OptionItem item) {
        CharSequence description = item.getDescription();
        button.setText(TextUtils.isEmpty(description) ? item.getTitle() : item.getTitle() + "\n" + description);
        button.setTextColor(TEXT);
        button.setTextSize(16);
        HearthLook.panelRow(button);
        HearthLook.accentTint(button);
        button.setPadding(dp(12), dp(14), dp(12), dp(14));
    }

    private void addSwitch(OptionItem item) {
        Switch toggle = new Switch(this);
        toggle.setText(item.getTitle());
        toggle.setTextColor(TEXT);
        toggle.setTextSize(16);
        HearthLook.panelRow(toggle);
        HearthLook.accentTint(toggle);
        toggle.setPadding(dp(16), dp(14), dp(16), dp(14));
        toggle.setChecked(item.isSelected());
        toggle.setOnCheckedChangeListener((b, checked) -> item.onSelect(checked));
        mBody.addView(toggle, rowParams());
    }

    private void addButton(OptionItem item) {
        addRow(item.getTitle(), item.getDescription(), v -> item.onSelect(true));
    }

    private void addRow(CharSequence title, @Nullable CharSequence subtitle, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        HearthLook.panelRow(row);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setOnClickListener(onClick);

        TextView name = new TextView(this);
        name.setTextColor(TEXT);
        name.setTextSize(16);
        name.setText(title);
        row.addView(name);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView sub = new TextView(this);
            sub.setTextColor(SUBTEXT);
            sub.setTextSize(13);
            sub.setText(subtitle);
            row.addView(sub);
        }

        mBody.addView(row, rowParams());
    }

    /** What's chosen now, under a list's title: the selected option, or how many are checked */
    @Nullable
    private CharSequence summaryOf(OptionCategory category) {
        if (category.type == OptionCategory.TYPE_RADIO_LIST) {
            OptionItem picked = pickedIn(category);
            return picked != null ? picked.getTitle() : null;
        } else if (category.type == OptionCategory.TYPE_CHECKBOX_LIST) {
            int checked = 0;
            for (OptionItem item : category.options) {
                if (item.isSelected()) {
                    checked++;
                }
            }
            return checked + " / " + category.options.size();
        }

        return null;
    }

    private static boolean isList(OptionCategory category) {
        return category.type == OptionCategory.TYPE_RADIO_LIST || category.type == OptionCategory.TYPE_CHECKBOX_LIST
                || category.type == OptionCategory.TYPE_STRING_LIST || category.type == OptionCategory.TYPE_LONG_TEXT;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(2));
        return params;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
