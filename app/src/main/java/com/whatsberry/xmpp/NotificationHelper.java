package com.whatsberry.xmpp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

/**
 * Helper class for showing notifications
 * Optimized for BlackBerry 10 Android Runtime
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final int NOTIFICATION_ID_BASE = 1000;
    private static int notificationCounter = 0;

    private Context context;
    private NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        Log.d(TAG, "NotificationHelper initialized");
    }

    /**
     * Show a new message notification
     * Enhanced for BlackBerry Hub integration with maximum compatibility
     */
    public void showMessageNotification(String contactJid, String contactName, String messageText) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "SHOWING NOTIFICATION FOR: " + contactName);
        Log.d(TAG, "Message: " + messageText);
        Log.d(TAG, "========================================");

        try {
            // Create intent to open chat when notification is clicked
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("contactJid", contactJid);
            intent.putExtra("contactName", contactName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    contactJid.hashCode(), // Use JID hash as unique request code
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );

            // Get default notification sound
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Log.d(TAG, "Sound URI: " + soundUri);

            // Prepare ticker text (CRITICAL for BB10 - shows as heads-up)
            String tickerText = contactName + ": " + messageText;
            if (tickerText.length() > 50) {
                tickerText = tickerText.substring(0, 47) + "...";
            }

            // Build notification with ALL possible flags for BB10 compatibility
            Notification notification = new Notification();
            notification.icon = android.R.drawable.stat_notify_chat;
            notification.tickerText = tickerText; // CRITICAL for BB10 - shows as heads-up
            notification.when = System.currentTimeMillis();

            // CRITICAL: Use DEFAULT_ALL for BB10 Hub integration
            // This includes: DEFAULT_SOUND, DEFAULT_VIBRATE, DEFAULT_LIGHTS
            // BB10's Android Runtime needs this to treat notification as "important"
            notification.defaults = Notification.DEFAULT_ALL;
            Log.d(TAG, "Using Notification.DEFAULT_ALL (SOUND + VIBRATE + LIGHTS)");

            // Optionally set custom sound (but DEFAULT_ALL will use system default if not set)
            // notification.sound = soundUri; // Commented out - let DEFAULT_ALL handle it

            // Set flags for maximum visibility
            notification.flags = Notification.FLAG_SHOW_LIGHTS
                               | Notification.FLAG_AUTO_CANCEL;
            Log.d(TAG, "Flags: FLAG_SHOW_LIGHTS | FLAG_AUTO_CANCEL");

            // Set content view using deprecated but BB10-compatible method
            notification.setLatestEventInfo(
                    context,
                    contactName,
                    messageText,
                    pendingIntent
            );
            Log.d(TAG, "setLatestEventInfo() called with title: " + contactName);

            // Show notification with unique ID per contact
            int notificationId = NOTIFICATION_ID_BASE + Math.abs(contactJid.hashCode() % 1000);

            Log.d(TAG, "Calling notificationManager.notify() with ID: " + notificationId);
            notificationManager.notify(notificationId, notification);
            Log.d(TAG, "✅ Notification sent successfully with DEFAULT_ALL!");

        } catch (Exception e) {
            Log.e(TAG, "❌ ERROR showing notification:", e);
            e.printStackTrace();
        }
    }

    /**
     * Clear all notifications
     */
    public void clearAllNotifications() {
        notificationManager.cancelAll();
    }
}
