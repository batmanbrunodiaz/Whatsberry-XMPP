package com.whatsberry.xmpp;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import java.util.List;

/**
 * Emoji Adapter - Displays emojis as images in a grid
 * Compatible with Android API 18
 */
public class EmojiAdapter extends BaseAdapter {
    private Context context;
    private List<String> emojis;

    public EmojiAdapter(Context context, List<String> emojis) {
        this.context = context;
        this.emojis = emojis;
    }

    @Override
    public int getCount() {
        return emojis.size();
    }

    @Override
    public Object getItem(int position) {
        return emojis.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setPadding(6, 6, 6, 6);
            int size = TwemojiParser.getEmojiSize(context);
            imageView.setLayoutParams(new AbsListView.LayoutParams(size, size));
        } else {
            imageView = (ImageView) convertView;
        }

        Drawable emojiDrawable = TwemojiParser.getEmojiDrawableFromString(context, emojis.get(position));
        if (emojiDrawable != null) {
            imageView.setImageDrawable(emojiDrawable);
        } else {
            imageView.setImageDrawable(null);
        }

        return imageView;
    }

    /**
     * Update emoji list and refresh view
     */
    public void updateEmojis(List<String> newEmojis) {
        this.emojis = newEmojis;
        notifyDataSetChanged();
    }
}
