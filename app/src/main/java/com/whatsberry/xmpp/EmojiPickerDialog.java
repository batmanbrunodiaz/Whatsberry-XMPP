package com.whatsberry.xmpp;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * Emoji Picker Dialog - Uses Twemoji PNG images
 * Compatible with Android API 18
 */
public class EmojiPickerDialog extends Dialog {
    private EmojiSelectedListener listener;

    private static final String[] CATEGORY_NAMES = {
        "Smileys",
        "People",
        "Animals",
        "Food",
        "Activities",
        "Travel",
        "Objects",
        "Symbols"
    };

    private static final String[] CATEGORY_KEYS = {
        "smileys",
        "people",
        "animals",
        "food",
        "activities",
        "travel",
        "objects",
        "symbols"
    };

    public interface EmojiSelectedListener {
        void onEmojiSelected(String emoji);
    }

    public EmojiPickerDialog(Context context, EmojiSelectedListener listener) {
        super(context);
        this.listener = listener;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Create ScrollView with all emoji categories
        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);
        container.setBackgroundColor(0xFFFFFFFF);

        // Add each emoji category
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            List<String> emojis = EmojiData.getEmojisForCategory(CATEGORY_KEYS[i]);

            if (emojis.isEmpty()) {
                continue; // Skip empty categories
            }

            // Category label
            TextView label = new TextView(context);
            label.setText(CATEGORY_NAMES[i]);
            label.setTextSize(16);
            label.setTextColor(0xFF000000);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setPadding(8, 16, 8, 8);
            container.addView(label);

            // Emoji grid for this category
            GridView gridView = createEmojiGrid(context, emojis);
            container.addView(gridView);
        }

        scrollView.addView(container);
        setContentView(scrollView);

        // Set dialog size
        if (getWindow() != null) {
            getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (context.getResources().getDisplayMetrics().heightPixels * 0.7)
            );
        }
    }

    private GridView createEmojiGrid(Context context, final List<String> emojis) {
        GridView gridView = new GridView(context);
        gridView.setNumColumns(8); // 8 emojis per row
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setPadding(8, 8, 8, 8);
        gridView.setVerticalSpacing(8);
        gridView.setHorizontalSpacing(8);

        // Calculate grid height
        int emojiSize = TwemojiParser.getEmojiSize(context);
        int rows = (int) Math.ceil(emojis.size() / 8.0);
        int gridHeight = (emojiSize + 16) * rows; // emoji size + spacing
        gridView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            gridHeight
        ));

        // Set adapter
        final EmojiAdapter adapter = new EmojiAdapter(context, emojis);
        gridView.setAdapter(adapter);

        // Handle emoji selection
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (listener != null && position < emojis.size()) {
                    listener.onEmojiSelected(emojis.get(position));
                }
                dismiss();
            }
        });

        return gridView;
    }
}
