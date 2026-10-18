package com.thesiegs.hearthtube.mobile.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;

/** Header texts (Strings) and video cards, one list for the phone's and tablet's grid */
public class VideoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onClick(Video video);
        void onLongClick(Video video);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VIDEO = 1;

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

    public Video lastVideo() {
        for (int i = mItems.size() - 1; i >= 0; i--) {
            if (mItems.get(i) instanceof Video) {
                return (Video) mItems.get(i);
            }
        }

        return null;
    }

    public int indexOf(Video video) {
        return mItems.indexOf(video);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return isHeader(position) ? TYPE_HEADER : TYPE_VIDEO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        if (viewType == TYPE_HEADER) {
            TextView header = new TextView(context);
            header.setTextColor(Color.WHITE);
            header.setTextSize(18);
            header.setPadding(dp(context, 8), dp(context, 16), dp(context, 8), dp(context, 4));
            header.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(header) {};
        }

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 8));
        card.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView image = new AspectImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF202020);
        card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(context, 6), 0, 0);
        card.addView(title);

        TextView author = new TextView(context);
        author.setTextColor(Color.LTGRAY);
        author.setTextSize(13);
        author.setMaxLines(1);
        author.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(author);

        return new VideoHolder(card, image, title, author);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = mItems.get(position);

        if (item instanceof String) {
            ((TextView) holder.itemView).setText((String) item);
            return;
        }

        Video video = (Video) item;
        VideoHolder card = (VideoHolder) holder;
        card.title.setText(video.getTitle());
        card.author.setText(video.getAuthor());
        Glide.with(card.image).load(video.getCardImageUrl()).into(card.image);
        card.itemView.setOnClickListener(v -> mListener.onClick(video));
        card.itemView.setOnLongClickListener(v -> {
            mListener.onLongClick(video);
            return true;
        });
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof VideoHolder) {
            Glide.with(((VideoHolder) holder).image).clear(((VideoHolder) holder).image);
        }
    }

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class VideoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView author;

        VideoHolder(View itemView, ImageView image, TextView title, TextView author) {
            super(itemView);
            this.image = image;
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
