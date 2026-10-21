package com.thesiegs.hearthtube.mobile.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.AiSlopList;
import com.thesiegs.hearthtube.mobile.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Header texts (Strings), video cards and shelves (a title over larger cards that scroll sideways, ending in a "See
 * more" card), one list for the phone's and tablet's grid
 */
public class VideoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onClick(Video video);
        void onLongClick(Video video);
        /** The channel name under a card was tapped: its page */
        void onAuthorClick(Video video);
        /** A shelf's "See more" card: the whole row */
        void onMore(VideoRows.Shelf shelf);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_SHELF = 2;

    private final Listener mListener;
    private final List<Object> mItems = new ArrayList<>();

    public VideoAdapter(Listener listener) {
        mListener = listener;
    }

    public void setItems(List<Object> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public boolean isHeader(int position) {
        return position >= 0 && position < mItems.size() && mItems.get(position) instanceof String;
    }

    /** A header or a shelf: as wide as the list, whatever the number of columns */
    public boolean isFullWidth(int position) {
        return isHeader(position) || (position >= 0 && position < mItems.size() && mItems.get(position) instanceof VideoRows.Shelf);
    }

    public Video lastVideo() {
        for (int i = mItems.size() - 1; i >= 0; i--) {
            Object item = mItems.get(i);

            if (item instanceof Video) {
                return (Video) item;
            }

            if (item instanceof VideoRows.Shelf && !((VideoRows.Shelf) item).videos.isEmpty()) {
                List<Video> videos = ((VideoRows.Shelf) item).videos;
                return videos.get(videos.size() - 1);
            }
        }

        return null;
    }

    public int indexOf(Video video) {
        for (int i = 0; i < mItems.size(); i++) {
            Object item = mItems.get(i);

            if (item.equals(video) || (item instanceof VideoRows.Shelf && ((VideoRows.Shelf) item).videos.contains(video))) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = mItems.get(position);
        return item instanceof String ? TYPE_HEADER : item instanceof VideoRows.Shelf ? TYPE_SHELF : TYPE_VIDEO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        if (viewType == TYPE_HEADER) {
            return new RecyclerView.ViewHolder(newHeader(context)) {};
        }

        if (viewType == TYPE_SHELF) {
            LinearLayout shelf = new LinearLayout(context);
            shelf.setOrientation(LinearLayout.VERTICAL);
            shelf.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView title = newHeader(context);
            shelf.addView(title);

            RecyclerView cards = new RecyclerView(context);
            cards.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            cards.setClipToPadding(false);
            cards.setPadding(dp(context, 4), 0, dp(context, 4), 0);
            cards.setOverScrollMode(View.OVER_SCROLL_NEVER);
            ShelfAdapter adapter = new ShelfAdapter(mListener);
            cards.setAdapter(adapter);
            shelf.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ShelfHolder(shelf, title, adapter);
        }

        return newCard(context, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = mItems.get(position);

        if (item instanceof String) {
            ((TextView) holder.itemView).setText((String) item);
            return;
        }

        if (item instanceof VideoRows.Shelf) {
            VideoRows.Shelf shelf = (VideoRows.Shelf) item;
            ShelfHolder holderOf = (ShelfHolder) holder;
            holderOf.title.setText(shelf.title);
            holderOf.title.setVisibility(shelf.title == null || shelf.title.isEmpty() ? View.GONE : View.VISIBLE);
            holderOf.adapter.set(shelf);
            return;
        }

        bindCard((VideoHolder) holder, (Video) item, mListener);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        clearImage(holder);
    }

    private static void clearImage(RecyclerView.ViewHolder holder) {
        if (holder instanceof VideoHolder) {
            Glide.with(((VideoHolder) holder).image).clear(((VideoHolder) holder).image);
        }
    }

    private static TextView newHeader(Context context) {
        TextView header = new TextView(context);
        header.setTextColor(Color.WHITE);
        header.setTextSize(20);
        header.setTypeface(HearthLook.medium());
        HearthLook.textShadow(header);
        header.setPadding(dp(context, 8), dp(context, 16), dp(context, 8), dp(context, 4));
        header.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    /** A card as wide as {@code width} (a pixel size, or MATCH_PARENT in the grid) */
    private static VideoHolder newCard(Context context, int width) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 8));
        card.setLayoutParams(new RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The thumbnail has rounded corners like the TV's cards; a "Likely AI" pill floats in its top left corner
        FrameLayout thumb = new FrameLayout(context);
        ImageView image = new AspectImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF202020);
        thumb.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int radius = dp(context, 12);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        thumb.setClipToOutline(true);

        TextView aiLabel = new TextView(context);
        aiLabel.setText(com.liskovsoft.smartyoutubetv2.common.R.string.ai_label);
        aiLabel.setTextSize(11);
        aiLabel.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2));
        HearthLook.aiLabel(aiLabel);
        FrameLayout.LayoutParams aiParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        aiParams.setMargins(dp(context, 8), dp(context, 8), 0, 0);
        thumb.addView(aiLabel, aiParams);
        card.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(HearthLook.medium());
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(context, 2), dp(context, 6), 0, 0);
        HearthLook.textShadow(title);
        card.addView(title);

        TextView author = new TextView(context);
        author.setTextColor(0xFFC8C8C8);
        author.setTextSize(12);
        author.setMaxLines(1);
        author.setEllipsize(TextUtils.TruncateAt.END);
        author.setPadding(dp(context, 2), dp(context, 4), dp(context, 8), dp(context, 6));
        HearthLook.textShadow(author);
        card.addView(author);

        return new VideoHolder(card, image, aiLabel, title, author);
    }

    private static void bindCard(VideoHolder card, Video video, Listener listener) {
        card.title.setText(video.getTitle());
        card.author.setText(video.getAuthor());
        // The channel's name opens the channel (a long press still belongs to the card: its menu)
        boolean hasChannel = video.getAuthor() != null && ChannelPresenter.canOpenChannel(video);
        card.author.setClickable(hasChannel);
        card.author.setOnClickListener(hasChannel ? v -> listener.onAuthorClick(video) : null);
        card.author.setOnLongClickListener(hasChannel ? v -> card.itemView.performLongClick() : null);
        card.aiLabel.setVisibility(AiSlopList.isLabeled(video) ? View.VISIBLE : View.GONE);
        Glide.with(card.image).load(video.getCardImageUrl()).into(card.image);
        card.itemView.setOnClickListener(v -> listener.onClick(video));
        card.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(video);
            return true;
        });
    }

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** A shelf's card: wide on a phone (the next one peeks in from the side), a set size on a tablet */
    private static int shelfCardWidth(Context context) {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        return Math.min(dp(context, 340), (int) (screen * 0.82f));
    }

    /** The cards of one shelf and, after them, a "See more" card */
    private static final class ShelfAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_CARD = 0;
        private static final int TYPE_MORE = 1;

        private final Listener mListener;
        private VideoRows.Shelf mShelf;

        ShelfAdapter(Listener listener) {
            mListener = listener;
        }

        void set(VideoRows.Shelf shelf) {
            mShelf = shelf;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mShelf == null ? 0 : mShelf.videos.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position < mShelf.videos.size() ? TYPE_CARD : TYPE_MORE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            int width = shelfCardWidth(context);

            if (viewType == TYPE_CARD) {
                return newCard(context, width);
            }

            // The same shape as a card's picture, so it reads as the next card in the row
            FrameLayout more = new FrameLayout(context);
            more.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 8));
            more.setLayoutParams(new RecyclerView.LayoutParams(width / 2, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView label = new TextView(context);
            label.setText(R.string.see_more);
            label.setTextSize(16);
            label.setGravity(Gravity.CENTER);
            label.setMinHeight(width * 9 / 16);
            HearthLook.glassButton(label);
            more.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(more) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < mShelf.videos.size()) {
                bindCard((VideoHolder) holder, mShelf.videos.get(position), mListener);
            } else {
                VideoRows.Shelf shelf = mShelf;
                holder.itemView.setOnClickListener(v -> mListener.onMore(shelf));
            }
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            clearImage(holder);
        }
    }

    private static final class ShelfHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ShelfAdapter adapter;

        ShelfHolder(View itemView, TextView title, ShelfAdapter adapter) {
            super(itemView);
            this.title = title;
            this.adapter = adapter;
        }
    }

    private static final class VideoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView aiLabel;
        final TextView title;
        final TextView author;

        VideoHolder(View itemView, ImageView image, TextView aiLabel, TextView title, TextView author) {
            super(itemView);
            this.image = image;
            this.aiLabel = aiLabel;
            this.title = title;
            this.author = author;
        }
    }

    /** A 16:9 picture whatever the column's width */
    private static final class AspectImageView extends ImageView {
        AspectImageView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }
}
