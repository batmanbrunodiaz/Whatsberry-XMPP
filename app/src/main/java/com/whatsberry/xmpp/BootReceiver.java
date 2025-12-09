package com.whatsberry.xmpp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receiver to start XMPP service on device boot
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted, starting XMPP service");

            // Start XMPP service
            Intent serviceIntent = new Intent(context, XMPPService.class);
            context.startService(serviceIntent);
        }
    }
}
