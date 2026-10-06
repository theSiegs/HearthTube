package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The "More" tile ending a big row (HearthRowsFragment): same size as the cards, opens the row as a grid.
 */
public class MoreTilePresenter extends Presenter {
    /** The tile's item in the row */
    public static final class More {
        public static final More INSTANCE = new More();

        private More() {
        }

        @Override
        public String toString() {
            return "More";
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        Pair<Integer, Integer> size = LargeVideoCardPresenter.getLargeCardDimensPx(context);

        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackgroundResource(R.drawable.hearth_more_tile_background);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setLayoutParams(new ViewGroup.LayoutParams(size.first, size.second));

        int icon = Math.round(40 * context.getResources().getDisplayMetrics().density);
        ImageView image = new ImageView(context);
        image.setImageResource(R.drawable.ic_hearth_more_grid);
        tile.addView(image, new LinearLayout.LayoutParams(icon, icon));

        TextView label = new TextView(context);
        label.setText(R.string.more_videos);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Math.round(8 * context.getResources().getDisplayMetrics().density), 0, 0);
        tile.addView(label);

        return new ViewHolder(tile);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
