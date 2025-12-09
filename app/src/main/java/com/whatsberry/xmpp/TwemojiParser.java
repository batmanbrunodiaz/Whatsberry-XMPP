package com.whatsberry.xmpp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Twemoji Parser - Loads and renders Twitter emoji images
 * Compatible with Android API 18
 */
public class TwemojiParser {
    private static final String TAG = "TwemojiParser";
    private static final String TWEMOJI_PATH = "twemoji/72x72/";

    private static boolean isEmoji(int codePoint) {
        return (codePoint >= 128512 && codePoint <= 128591) ||
               (codePoint >= 127744 && codePoint <= 128511) ||
               (codePoint >= 128640 && codePoint <= 128767) ||
               (codePoint >= 127462 && codePoint <= 127487) ||
               (codePoint >= 9728 && codePoint <= 9983) ||
               (codePoint >= 9984 && codePoint <= 10175) ||
               (codePoint >= 65024 && codePoint <= 65039) ||
               (codePoint >= 129280 && codePoint <= 129535) ||
               (codePoint >= 129536 && codePoint <= 129647) ||
               (codePoint >= 129648 && codePoint <= 129791) ||
               codePoint == 8205 ||
               (codePoint >= 126980 && codePoint <= 127183);
    }

    /**
     * Parse text and replace emoji characters with Twemoji images
     */
    public static CharSequence parseEmojis(Context context, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int length = builder.length();
        int i = 0;
        while (i < length) {
            int codePoint = Character.codePointAt(builder, i);
            if (isEmoji(codePoint)) {
                int charCount = Character.charCount(codePoint);
                Drawable emojiDrawable = getEmojiDrawable(context, codePoint);
                if (emojiDrawable != null) {
                    builder.setSpan(new ImageSpan(emojiDrawable, ImageSpan.ALIGN_BASELINE), i, i + charCount, 33);
                }
                if (charCount == 2) {
                    i++;
                    length--;
                }
            }
            i++;
        }
        return builder;
    }

    /**
     * Get emoji drawable from code point
     */
    public static Drawable getEmojiDrawable(Context context, int codePoint) {
        String filename = getEmojiFilename(codePoint);
        try {
            InputStream is = context.getAssets().open(TWEMOJI_PATH + filename);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) {
                return null;
            }
            // Scale to 22dp (bigger size to avoid clipping in messages)
            int size = (int) (context.getResources().getDisplayMetrics().density * 22.0f);
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(),
                Bitmap.createScaledBitmap(bitmap, size, size, true));
            drawable.setBounds(0, 0, size, size);
            return drawable;
        } catch (IOException e) {
            Log.w(TAG, "Could not load emoji: " + filename);
            return null;
        }
    }

    /**
     * Get emoji drawable from emoji string (for picker)
     */
    public static Drawable getEmojiDrawableFromString(Context context, String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return null;
        }
        return getEmojiDrawable(context, emoji.codePointAt(0));
    }

    /**
     * Convert code point to filename (e.g., 128512 -> "1f600.png")
     */
    private static String getEmojiFilename(int codePoint) {
        return String.format("%x.png", codePoint).toLowerCase();
    }

    /**
     * Get emoji size for picker grid
     */
    public static int getEmojiSize(Context context) {
        return (int) (context.getResources().getDisplayMetrics().density * 36.0f);
    }

    /**
     * Check if Twemoji assets are available
     */
    public static boolean hasTwemojiFiles(Context context) {
        try {
            String[] list = context.getAssets().list(TWEMOJI_PATH);
            return list != null && list.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if specific emoji file exists
     */
    public static boolean emojiFileExists(Context context, String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return false;
        }
        String filename = getEmojiFilename(emoji.codePointAt(0));
        try {
            String[] list = context.getAssets().list(TWEMOJI_PATH);
            if (list != null) {
                for (String file : list) {
                    if (file.equals(filename)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Error checking emoji file existence", e);
        }
        return false;
    }

    /**
     * Filter list to only include emojis with available files
     */
    public static List<String> filterAvailableEmojis(Context context, List<String> emojis) {
        ArrayList<String> available = new ArrayList<String>();
        for (String emoji : emojis) {
            if (emojiFileExists(context, emoji)) {
                available.add(emoji);
            }
        }
        return available;
    }
}
