package com.whatsberry.xmpp;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emoji Data - Categorizes emojis from Twemoji assets
 * Compatible with Android API 18
 */
public class EmojiData {
    private static final String TAG = "EmojiData";
    private static Map<String, List<String>> emojiCategories;
    private static boolean isInitialized = false;

    public static Map<String, List<String>> getEmojiCategories() {
        return emojiCategories != null ? emojiCategories : new HashMap<String, List<String>>();
    }

    /**
     * Initialize emoji categories from assets
     * Must be called before using emoji picker
     */
    public static void initializeEmojis(Context context) {
        if (isInitialized) {
            return;
        }
        emojiCategories = new HashMap<String, List<String>>();
        try {
            String[] files = context.getAssets().list("twemoji/72x72");
            if (files == null || files.length == 0) {
                Log.e(TAG, "No emoji files found in assets!");
                return;
            }

            Log.d(TAG, "Loading " + files.length + " emojis from assets...");

            // Create category lists
            ArrayList<String> smileys = new ArrayList<String>();
            ArrayList<String> people = new ArrayList<String>();
            ArrayList<String> animals = new ArrayList<String>();
            ArrayList<String> food = new ArrayList<String>();
            ArrayList<String> activities = new ArrayList<String>();
            ArrayList<String> travel = new ArrayList<String>();
            ArrayList<String> objects = new ArrayList<String>();
            ArrayList<String> symbols = new ArrayList<String>();

            // Parse emoji files and categorize
            for (String filename : files) {
                // Skip skin tone variants and non-PNG files
                if (!filename.endsWith(".png") ||
                    filename.contains("-1f3fb") ||
                    filename.contains("-1f3fc") ||
                    filename.contains("-1f3fd") ||
                    filename.contains("-1f3fe") ||
                    filename.contains("-1f3ff")) {
                    continue;
                }

                String emoji = filenameToEmoji(filename);
                if (emoji == null) {
                    continue;
                }

                int codePoint = emoji.codePointAt(0);

                // Categorize based on Unicode ranges
                if (codePoint >= 128512 && codePoint <= 128591) {
                    // Emoticons
                    smileys.add(emoji);
                } else if ((codePoint >= 128102 && codePoint <= 128135) ||
                           (codePoint >= 128372 && codePoint <= 128378) ||
                           (codePoint >= 128581 && codePoint <= 128590) ||
                           (codePoint >= 129280 && codePoint <= 129535 && codePoint < 129344) ||
                           (codePoint >= 129728 && codePoint <= 129730) ||
                           (codePoint >= 129776 && codePoint <= 129784) ||
                           (codePoint >= 9757 && codePoint <= 9997) ||
                           (codePoint >= 128075 && codePoint <= 128080)) {
                    // People & Body
                    people.add(emoji);
                } else if ((codePoint >= 128000 && codePoint <= 128063) ||
                           (codePoint >= 129408 && codePoint <= 129454) ||
                           (codePoint >= 127793 && codePoint <= 127797) ||
                           (codePoint >= 127799 && codePoint <= 127818) ||
                           (codePoint >= 129344 && codePoint <= 129349)) {
                    // Animals & Nature
                    animals.add(emoji);
                } else if ((codePoint >= 127789 && codePoint <= 127792) ||
                           (codePoint >= 127819 && codePoint <= 127871) ||
                           (codePoint >= 129360 && codePoint <= 129391) ||
                           (codePoint >= 129472 && codePoint <= 129483)) {
                    // Food & Drink
                    food.add(emoji);
                } else if ((codePoint >= 127936 && codePoint <= 127940) ||
                           (codePoint >= 127942 && codePoint <= 127946) ||
                           (codePoint >= 127951 && codePoint <= 127955) ||
                           (codePoint >= 128759 && codePoint <= 128764) ||
                           (codePoint >= 129338 && codePoint <= 129342) ||
                           (codePoint >= 129496 && codePoint <= 129503) ||
                           (codePoint >= 9975 && codePoint <= 9978)) {
                    // Activities & Sports
                    activities.add(emoji);
                } else if ((codePoint >= 128640 && codePoint <= 128709) ||
                           (codePoint >= 128715 && codePoint <= 128722) ||
                           (codePoint >= 128725 && codePoint <= 128727) ||
                           (codePoint >= 128733 && codePoint <= 128741) ||
                           (codePoint >= 128745 && codePoint <= 128752) ||
                           (codePoint >= 127956 && codePoint <= 127984) ||
                           (codePoint >= 127987 && codePoint <= 127991) ||
                           (codePoint >= 128506 && codePoint <= 128511) ||
                           (codePoint >= 129468 && codePoint <= 129471)) {
                    // Travel & Places
                    travel.add(emoji);
                } else if ((codePoint >= 128160 && codePoint <= 128254) ||
                           (codePoint >= 128256 && codePoint <= 128371) ||
                           (codePoint >= 128379 && codePoint <= 128419) ||
                           (codePoint >= 128421 && codePoint <= 128505) ||
                           (codePoint >= 128682 && codePoint <= 128714) ||
                           (codePoint >= 128723 && codePoint <= 128724) ||
                           (codePoint >= 129455 && codePoint <= 129460) ||
                           (codePoint >= 129504 && codePoint <= 129652) ||
                           (codePoint >= 8986 && codePoint <= 9210) ||
                           (codePoint >= 9728 && codePoint <= 9732) ||
                           (codePoint >= 9742 && codePoint <= 9745) ||
                           (codePoint >= 9748 && codePoint <= 9749) ||
                           (codePoint >= 9752 && codePoint <= 9752) ||
                           (codePoint >= 9760 && codePoint <= 9776) ||
                           (codePoint >= 9784 && codePoint <= 9786) ||
                           (codePoint >= 9800 && codePoint <= 9811) ||
                           (codePoint >= 9874 && codePoint <= 9879) ||
                           (codePoint >= 9881 && codePoint <= 9884) ||
                           (codePoint >= 9888 && codePoint <= 9889) ||
                           (codePoint >= 9935 && codePoint <= 9940) ||
                           (codePoint >= 9961 && codePoint <= 9973)) {
                    // Objects
                    objects.add(emoji);
                } else {
                    // Symbols & Flags
                    symbols.add(emoji);
                }
            }

            emojiCategories.put("smileys", smileys);
            emojiCategories.put("people", people);
            emojiCategories.put("animals", animals);
            emojiCategories.put("food", food);
            emojiCategories.put("activities", activities);
            emojiCategories.put("travel", travel);
            emojiCategories.put("objects", objects);
            emojiCategories.put("symbols", symbols);

            isInitialized = true;

            Log.d(TAG, "Emojis loaded successfully:");
            Log.d(TAG, "  Smileys: " + smileys.size());
            Log.d(TAG, "  People: " + people.size());
            Log.d(TAG, "  Animals: " + animals.size());
            Log.d(TAG, "  Food: " + food.size());
            Log.d(TAG, "  Activities: " + activities.size());
            Log.d(TAG, "  Travel: " + travel.size());
            Log.d(TAG, "  Objects: " + objects.size());
            Log.d(TAG, "  Symbols: " + symbols.size());

        } catch (IOException e) {
            Log.e(TAG, "Error loading emoji files", e);
        }
    }

    /**
     * Convert filename to emoji character
     * e.g., "1f600.png" -> "😀"
     * e.g., "1f1fa-1f1f8.png" -> "🇺🇸" (flag sequences)
     */
    private static String filenameToEmoji(String filename) {
        try {
            String hex = filename.replace(".png", "");
            if (hex.contains("-")) {
                // Multi-codepoint emoji (flags, ZWJ sequences)
                String[] parts = hex.split("-");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    sb.append(Character.toChars(Integer.parseInt(part, 16)));
                }
                return sb.toString();
            } else {
                // Single codepoint emoji
                return new String(Character.toChars(Integer.parseInt(hex, 16)));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse emoji filename: " + filename);
            return null;
        }
    }

    /**
     * Get emojis for a specific category
     */
    public static List<String> getEmojisForCategory(String category) {
        if (emojiCategories == null) {
            return new ArrayList<String>();
        }
        List<String> emojis = emojiCategories.get(category);
        return emojis != null ? emojis : new ArrayList<String>();
    }
}
