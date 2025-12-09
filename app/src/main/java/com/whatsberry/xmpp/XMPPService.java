package com.whatsberry.xmpp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service to keep XMPP connection alive
 * Runs as foreground service to prevent BB10 from killing the process
 * Shows persistent notification in BlackBerry Hub
 */
public class XMPPService extends Service {
    private static final String TAG = "XMPPService";
    private static final int FOREGROUND_NOTIFICATION_ID = 9999;
    private static final String PREFS_NAME = "WhatsberryPrefs";
    private static final String PREF_SERVER = "server";
    private static final String PREF_PORT = "port";
    private static final String PREF_DOMAIN = "domain";
    private static final String PREF_USERNAME = "username";
    private static final String PREF_PASSWORD = "password";

    private XMPPManager xmppManager;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "XMPPService created");
        xmppManager = XMPPManager.getInstance();
        xmppManager.initialize(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "XMPPService started");

        // CRITICAL: Start as foreground service to prevent BB10 from killing it
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        Log.d(TAG, "✅ Running as FOREGROUND SERVICE (BB10 won't kill this)");

        if (!isRunning) {
            isRunning = true;

            // Auto-login with hardcoded credentials
            autoLogin();
        }

        // START_STICKY ensures service restarts if killed
        return START_STICKY;
    }

    /**
     * Create persistent notification for foreground service
     * This notification tells BB10 that the app needs to stay alive
     */
    private Notification createForegroundNotification() {
        // Create intent to open main activity when notification is tapped
        Intent intent = new Intent(this, MainTabsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Build notification using deprecated but BB10-compatible method
        Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_notify_sync; // Sync icon
        notification.tickerText = "WhatsBerry conectado";
        notification.when = System.currentTimeMillis();
        notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        // Set content using deprecated method (works better on BB10)
        notification.setLatestEventInfo(
                this,
                "WhatsBerry",
                "Conectado - Recibiendo mensajes",
                pendingIntent
        );

        Log.d(TAG, "Foreground notification created");
        return notification;
    }

    private void autoLogin() {
        // Get credentials from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final String server = prefs.getString(PREF_SERVER, "");
        final String username = prefs.getString(PREF_USERNAME, "");
        final String password = prefs.getString(PREF_PASSWORD, "");
        final String portStr = prefs.getString(PREF_PORT, "5222");
        final String domain = prefs.getString(PREF_DOMAIN, server);

        // Don't auto-login if credentials are not configured
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Log.d(TAG, "No saved credentials found, skipping auto-login");
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid port number, using default 5222");
            return;
        }

        Log.d(TAG, "Auto-login to: " + username + "@" + server + ":" + port);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Check if already connected
                    if (xmppManager.isAuthenticated()) {
                        Log.d(TAG, "Already authenticated, skipping login");
                        return;
                    }

                    // Connect and Login
                    xmppManager.connect(server, port, domain, new XMPPManager.ConnectionCallback() {
                        @Override
                        public void onConnected() {
                            Log.d(TAG, "Connected to XMPP server, now logging in...");

                            // Login AFTER we're connected
                            xmppManager.login(username, password, new XMPPManager.ConnectionCallback() {
                                @Override
                                public void onConnected() {}

                                @Override
                                public void onAuthenticated() {
                                    Log.d(TAG, "✅ Auto-login successful!");
                                }

                                @Override
                                public void onDisconnected() {
                                    Log.d(TAG, "Disconnected after login, retrying...");
                                    scheduleRetry();
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Login error: " + error);
                                    scheduleRetry();
                                }
                            });
                        }

                        @Override
                        public void onAuthenticated() {
                            Log.d(TAG, "✅ Authenticated via connect callback");
                        }

                        @Override
                        public void onDisconnected() {
                            Log.d(TAG, "Disconnected from server, retrying...");
                            scheduleRetry();
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Connection error: " + error);
                            scheduleRetry();
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Auto-login exception: " + e.getMessage(), e);
                    scheduleRetry();
                }
            }
        }).start();
    }

    private void scheduleRetry() {
        // Retry login after 30 seconds
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Retrying auto-login...");
                autoLogin();
            }
        }, 30000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "XMPPService destroyed");
        isRunning = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
