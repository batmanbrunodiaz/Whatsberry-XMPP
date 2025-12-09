package com.whatsberry.xmpp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Activity with button-based navigation (WhatsApp style)
 * View 1: Recent Chats
 * View 2: Contacts
 */
public class MainTabsActivity extends Activity implements XMPPManager.MessageCallback {
    private static final String TAG = "MainTabsActivity";

    // Track currently open chat to prevent notifications
    public static String currentOpenChatJid = null;

    // Tab 1: Chats
    private ListView lvChats;
    private EditText etSearchChats;
    private TextView tvEmptyChats;
    private ChatsAdapter chatsAdapter;
    private List<ChatItem> chats;
    private List<ChatItem> filteredChats;

    // Tab 2: Contacts
    private ListView lvContacts;
    private EditText etSearchContacts;
    private TextView tvEmptyContacts;
    private ContactsAdapter contactsAdapter;
    private List<XMPPManager.Contact> contacts;
    private List<XMPPManager.Contact> filteredContacts;

    // Common
    private Button btnMenuMain, btnChatsTab, btnContactsTab;
    private TextView tvConnectionStatus;
    private LinearLayout tabChats, tabContacts;
    private ProgressDialog progressDialog;

    private XMPPManager xmppManager;
    private WhatsAppManager whatsAppManager;
    private DatabaseHelper databaseHelper;
    private NotificationHelper notificationHelper;
    private android.os.Handler connectionCheckHandler;
    private Runnable connectionCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_tabs);

        xmppManager = XMPPManager.getInstance();
        whatsAppManager = WhatsAppManager.getInstance();
        databaseHelper = new DatabaseHelper(this);
        notificationHelper = new NotificationHelper(this);

        // Ensure XMPP foreground service is running (critical for BB10 notifications)
        Intent serviceIntent = new Intent(this, XMPPService.class);
        startService(serviceIntent);
        android.util.Log.d(TAG, "✅ Ensured XMPPService is running");

        initializeViews();
        setupListeners();

        // Set message callback for real-time updates
        xmppManager.setMessageCallback(this);

        // Start connection status monitoring
        startConnectionMonitoring();

        // Show chats by default
        showChats();

        loadChats();
        loadContacts();
    }

    private void showChats() {
        tabChats.setVisibility(View.VISIBLE);
        tabContacts.setVisibility(View.GONE);
    }

    private void showContacts() {
        tabChats.setVisibility(View.GONE);
        tabContacts.setVisibility(View.VISIBLE);
    }

    private void initializeViews() {
        // Header buttons
        btnMenuMain = (Button) findViewById(R.id.btnMenuMain);
        btnChatsTab = (Button) findViewById(R.id.btnChatsTab);
        btnContactsTab = (Button) findViewById(R.id.btnContactsTab);
        tvConnectionStatus = (TextView) findViewById(R.id.tvConnectionStatus);

        // Views
        tabChats = (LinearLayout) findViewById(R.id.tabChats);
        tabContacts = (LinearLayout) findViewById(R.id.tabContacts);

        // Tab 1: Chats
        lvChats = (ListView) findViewById(R.id.lvChats);
        etSearchChats = (EditText) findViewById(R.id.etSearchChats);
        tvEmptyChats = (TextView) findViewById(R.id.tvEmptyChats);

        chats = new ArrayList<>();
        filteredChats = new ArrayList<>();
        chatsAdapter = new ChatsAdapter();
        lvChats.setAdapter(chatsAdapter);
        lvChats.setEmptyView(tvEmptyChats);

        // Tab 2: Contacts
        lvContacts = (ListView) findViewById(R.id.lvContacts);
        etSearchContacts = (EditText) findViewById(R.id.etSearchContacts);
        tvEmptyContacts = (TextView) findViewById(R.id.tvEmptyContacts);

        contacts = new ArrayList<>();
        filteredContacts = new ArrayList<>();
        contactsAdapter = new ContactsAdapter();
        lvContacts.setAdapter(contactsAdapter);
        lvContacts.setEmptyView(tvEmptyContacts);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
    }

    private void setupListeners() {
        // Menu button shows popup with options
        btnMenuMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOptionsMenu(v);
            }
        });

        // Chats tab button
        btnChatsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChats();
            }
        });

        // Contacts tab button
        btnContactsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showContacts();
            }
        });

        // Chats search
        etSearchChats.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChats(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Contacts search
        etSearchContacts.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Chat item click
        lvChats.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ChatItem chat = filteredChats.get(position);
                openChat(chat.contactJid, chat.contactName);
            }
        });

        // Contact item click
        lvContacts.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                XMPPManager.Contact contact = filteredContacts.get(position);
                openChat(contact.jid, contact.name);
            }
        });
    }

    private void loadChats() {
        // Load in background without blocking UI
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ChatItem> loadedChats = getRecentChats();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chats.clear();
                        chats.addAll(loadedChats);
                        filterChats(etSearchChats.getText().toString());
                    }
                });
            }
        }).start();
    }

    private void loadContacts() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<XMPPManager.Contact> loadedContacts = xmppManager.getContacts();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        contacts.clear();
                        contacts.addAll(loadedContacts);
                        filterContacts(etSearchContacts.getText().toString());
                    }
                });
            }
        }).start();
    }

    /**
     * Get recent chats from database
     * Returns list of unique contacts with their last message
     * OPTIMIZED: Gets all last messages in one query
     */
    private List<ChatItem> getRecentChats() {
        List<ChatItem> chatItems = new ArrayList<>();

        try {
            // Get last message for each contact in ONE query (fast!)
            Map<String, DatabaseHelper.MessageRecord> lastMessages =
                databaseHelper.getLastMessagePerContact();

            // Get all contacts from roster (cached)
            List<XMPPManager.Contact> allContacts = xmppManager.getContacts();
            Map<String, XMPPManager.Contact> contactMap = new HashMap<>();
            for (XMPPManager.Contact contact : allContacts) {
                contactMap.put(contact.jid, contact);
            }

            // Build chat items from last messages
            for (Map.Entry<String, DatabaseHelper.MessageRecord> entry : lastMessages.entrySet()) {
                String jid = entry.getKey();
                DatabaseHelper.MessageRecord lastMsg = entry.getValue();

                ChatItem item = new ChatItem();
                item.contactJid = jid;
                item.lastMessage = lastMsg.body;
                item.timestamp = lastMsg.timestamp;

                // Count unread messages (received messages from this contact)
                // If this chat is currently open, unread count is 0
                if (jid.equals(currentOpenChatJid)) {
                    item.unreadCount = 0;
                } else {
                    item.unreadCount = countUnreadMessages(jid);
                }

                // Get contact info from roster
                XMPPManager.Contact contact = contactMap.get(jid);
                if (contact != null) {
                    item.contactName = contact.name;
                    item.isOnline = contact.isOnline;
                } else {
                    // Contact not in roster, use JID as name
                    item.contactName = jid.contains("@") ?
                        jid.substring(0, jid.indexOf("@")) : jid;
                    item.isOnline = false;
                }

                chatItems.add(item);
            }

            // Sort by timestamp (newest first)
            java.util.Collections.sort(chatItems, new java.util.Comparator<ChatItem>() {
                @Override
                public int compare(ChatItem c1, ChatItem c2) {
                    // Manual comparison for API 18 compatibility
                    if (c2.timestamp > c1.timestamp) return 1;
                    if (c2.timestamp < c1.timestamp) return -1;
                    return 0;
                }
            });

        } catch (Exception e) {
            android.util.Log.e(TAG, "Error loading chats", e);
        }

        return chatItems;
    }

    /**
     * Count unread messages for a contact
     * Uses database is_read flag to track which messages have been read
     */
    private int countUnreadMessages(String contactJid) {
        try {
            return databaseHelper.countUnreadMessages(contactJid);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error counting unread messages", e);
            return 0;
        }
    }

    private void filterChats(String query) {
        filteredChats.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredChats.addAll(chats);
        } else {
            String lowerQuery = query.toLowerCase();
            for (ChatItem chat : chats) {
                if (chat.contactName.toLowerCase().contains(lowerQuery) ||
                    chat.lastMessage.toLowerCase().contains(lowerQuery)) {
                    filteredChats.add(chat);
                }
            }
        }

        chatsAdapter.notifyDataSetChanged();
    }

    private void filterContacts(String query) {
        filteredContacts.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredContacts.addAll(contacts);
        } else {
            String lowerQuery = query.toLowerCase();
            for (XMPPManager.Contact contact : contacts) {
                if (contact.name.toLowerCase().contains(lowerQuery) ||
                    contact.jid.toLowerCase().contains(lowerQuery)) {
                    filteredContacts.add(contact);
                }
            }
        }

        contactsAdapter.notifyDataSetChanged();
    }

    private void openChat(String contactJid, String contactName) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("contactJid", contactJid);
        intent.putExtra("contactName", contactName);
        startActivity(intent);
    }

    /**
     * Show options menu popup
     */
    private void showOptionsMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);

        popup.getMenu().add(0, 1, 0, "Refresh");
        popup.getMenu().add(0, 2, 1, "Database Settings");
        popup.getMenu().add(0, 3, 2, "Logout");

        popup.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1: // Refresh
                        loadChats();
                        loadContacts();
                        Toast.makeText(MainTabsActivity.this, "Refreshing...", Toast.LENGTH_SHORT).show();
                        return true;
                    case 2: // Database Settings
                        showDatabaseSettings();
                        return true;
                    case 3: // Logout
                        logout();
                        return true;
                    default:
                        return false;
                }
            }
        });

        popup.show();
    }

    private void logout() {
        progressDialog.setMessage("Logging out...");
        progressDialog.show();

        whatsAppManager.logoutWhatsApp(new WhatsAppManager.LogoutCallback() {
            @Override
            public void onLogoutSuccess() {
                progressDialog.dismiss();
                xmppManager.disconnect();
                Toast.makeText(MainTabsActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onLogoutError(String error) {
                progressDialog.dismiss();
                Toast.makeText(MainTabsActivity.this, "Logout error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Start monitoring XMPP connection status
     */
    private void startConnectionMonitoring() {
        connectionCheckHandler = new android.os.Handler();
        connectionCheckRunnable = new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();

                // Auto-refresh chats list every 10 seconds if connected
                if (xmppManager.isAuthenticated()) {
                    loadChats();
                }

                // Check every 10 seconds
                connectionCheckHandler.postDelayed(this, 10000);
            }
        };
        // Start monitoring
        connectionCheckHandler.post(connectionCheckRunnable);
    }

    /**
     * Update connection status indicator
     */
    private void updateConnectionStatus() {
        boolean isConnected = xmppManager.isAuthenticated();

        if (isConnected) {
            tvConnectionStatus.setText("Connected");
            tvConnectionStatus.setTextColor(0xFFAAFFAA); // Light green
        } else {
            tvConnectionStatus.setText("Disconnected");
            tvConnectionStatus.setTextColor(0xFFFFAAAA); // Light red
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh chats when returning from chat activity
        loadChats();
        // Update connection status immediately
        updateConnectionStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop connection monitoring
        if (connectionCheckHandler != null && connectionCheckRunnable != null) {
            connectionCheckHandler.removeCallbacks(connectionCheckRunnable);
        }

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // MessageCallback implementation
    @Override
    public void onMessageReceived(String from, String message, boolean isSent) {
        android.util.Log.d(TAG, "onMessageReceived: from=" + from + ", isSent=" + isSent + ", msg=" + message.substring(0, Math.min(50, message.length())));

        // Extract contact name from JID
        String contactName = from;
        if (from.contains("@")) {
            contactName = from.substring(0, from.indexOf("@"));
        }
        if (from.contains("/")) {
            from = from.substring(0, from.indexOf("/"));
        }

        // Find contact name from roster
        for (XMPPManager.Contact contact : contacts) {
            if (contact.jid.equals(from) || contact.jid.startsWith(from)) {
                contactName = contact.name;
                break;
            }
        }

        // Show notification only if chat is not currently open
        if (currentOpenChatJid == null || !from.startsWith(currentOpenChatJid)) {
            android.util.Log.d(TAG, "Showing notification for: " + contactName);
            notificationHelper.showMessageNotification(from, contactName, message);
        } else {
            android.util.Log.d(TAG, "NOT showing notification - chat is open: " + currentOpenChatJid);
        }

        // Refresh chats list when new message arrives
        loadChats();
    }

    @Override
    public void onMessageSent() {
        // Refresh chats list after sending
        loadChats();
    }

    @Override
    public void onMessageError(String error) {
        // No action needed
    }

    // Chat item model
    private static class ChatItem {
        String contactJid;
        String contactName;
        String lastMessage;
        long timestamp;
        boolean isOnline;
        int unreadCount;
    }

    // Chats adapter
    private class ChatsAdapter extends ArrayAdapter<ChatItem> {
        ChatsAdapter() {
            super(MainTabsActivity.this, R.layout.item_chat, filteredChats);
        }

        @Override
        public int getCount() {
            return filteredChats.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_chat, parent, false);
            }

            ChatItem chat = filteredChats.get(position);

            TextView tvAvatar = (TextView) view.findViewById(R.id.tvChatAvatar);
            TextView tvName = (TextView) view.findViewById(R.id.tvChatName);
            TextView tvTime = (TextView) view.findViewById(R.id.tvChatTime);
            TextView tvLastMessage = (TextView) view.findViewById(R.id.tvChatLastMessage);
            TextView tvUnreadBadge = (TextView) view.findViewById(R.id.tvUnreadBadge);

            // Set avatar (first letter of name)
            String initial = chat.contactName.length() > 0 ?
                    chat.contactName.substring(0, 1).toUpperCase() : "?";
            tvAvatar.setText(initial);

            // Set name
            tvName.setText(chat.contactName);

            // Set time
            String timeStr = formatTimestamp(chat.timestamp);
            tvTime.setText(timeStr);

            // Set last message
            tvLastMessage.setText(chat.lastMessage);

            // Set unread badge (TODO: implement unread counting)
            if (chat.unreadCount > 0) {
                tvUnreadBadge.setText(String.valueOf(chat.unreadCount));
                tvUnreadBadge.setVisibility(View.VISIBLE);
            } else {
                tvUnreadBadge.setVisibility(View.GONE);
            }

            return view;
        }

        private String formatTimestamp(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 60000) { // Less than 1 minute
                return "now";
            } else if (diff < 3600000) { // Less than 1 hour
                return (diff / 60000) + "m";
            } else if (diff < 86400000) { // Less than 1 day
                return (diff / 3600000) + "h";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");
                return sdf.format(new Date(timestamp));
            }
        }
    }

    // Contacts adapter
    private class ContactsAdapter extends ArrayAdapter<XMPPManager.Contact> {
        ContactsAdapter() {
            super(MainTabsActivity.this, R.layout.item_contact, filteredContacts);
        }

        @Override
        public int getCount() {
            return filteredContacts.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_contact, parent, false);
            }

            XMPPManager.Contact contact = filteredContacts.get(position);

            TextView tvAvatar = (TextView) view.findViewById(R.id.tvContactAvatar);
            TextView tvName = (TextView) view.findViewById(R.id.tvContactName);
            TextView tvStatus = (TextView) view.findViewById(R.id.tvContactStatus);
            TextView tvOnlineIndicator = (TextView) view.findViewById(R.id.tvOnlineIndicator);

            // Set avatar (first letter of name)
            String initial = contact.name.length() > 0 ?
                    contact.name.substring(0, 1).toUpperCase() : "?";
            tvAvatar.setText(initial);

            // Set name
            tvName.setText(contact.name);

            // Set status text
            String statusText = contact.status;
            if (statusText == null || statusText.isEmpty()) {
                statusText = contact.isOnline ? "Available" : "Offline";
            }
            tvStatus.setText(statusText);

            // Set online indicator visibility and color
            if (contact.isOnline) {
                tvOnlineIndicator.setVisibility(View.VISIBLE);
                tvOnlineIndicator.setTextColor(0xFF25D366); // WhatsApp green
            } else {
                tvOnlineIndicator.setVisibility(View.GONE);
            }

            return view;
        }
    }

    /**
     * Show database location settings dialog
     */
    private void showDatabaseSettings() {
        final String[] locations = DatabaseHelper.getAvailableLocations(this);
        final String[] locationNames = new String[locations.length];
        final String[] locationValues = new String[locations.length];

        // Parse location strings (format: "value|Description")
        for (int i = 0; i < locations.length; i++) {
            String[] parts = locations[i].split("\\|");
            locationValues[i] = parts[0];
            locationNames[i] = parts.length > 1 ? parts[1] : parts[0];
        }

        // Get current location
        android.content.SharedPreferences prefs = getSharedPreferences("WhatsberryPrefs", MODE_PRIVATE);
        final String currentLocation = prefs.getString("db_location", DatabaseHelper.LOCATION_EXTERNAL_STANDARD);

        // Find current selection index
        int currentIndex = 0;
        for (int i = 0; i < locationValues.length; i++) {
            if (locationValues[i].equals(currentLocation)) {
                currentIndex = i;
                break;
            }
        }

        // Build dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Database Storage Location");
        builder.setSingleChoiceItems(locationNames, currentIndex, null);
        builder.setPositiveButton("Save & Migrate", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                int selectedIndex = ((android.app.AlertDialog) dialog).getListView().getCheckedItemPosition();
                String selectedLocation = locationValues[selectedIndex];

                // If custom path selected, prompt for path
                if (selectedLocation.equals(DatabaseHelper.LOCATION_CUSTOM)) {
                    showCustomPathDialog(selectedLocation);
                } else {
                    migrateToLocation(selectedLocation, "");
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Info", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                showDatabaseInfo();
            }
        });

        builder.show();
    }

    /**
     * Show custom path input dialog
     */
    private void showCustomPathDialog(final String location) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Enter Custom Path");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("/path/to/directory");
        builder.setView(input);

        builder.setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String customPath = input.getText().toString().trim();
                if (customPath.isEmpty()) {
                    Toast.makeText(MainTabsActivity.this, "Path cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                migrateToLocation(location, customPath);
            }
        });
        builder.setNegativeButton("Cancel", null);

        builder.show();
    }

    /**
     * Migrate database to new location
     */
    private void migrateToLocation(final String newLocation, final String customPath) {
        progressDialog.setMessage("Migrating database...");
        progressDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = DatabaseHelper.migrateDatabase(MainTabsActivity.this, newLocation, customPath);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();

                        if (success) {
                            Toast.makeText(MainTabsActivity.this,
                                "Database migrated successfully! Restart app to apply changes.",
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainTabsActivity.this,
                                "Failed to migrate database. Check logs.",
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Show database information dialog
     */
    private void showDatabaseInfo() {
        android.content.SharedPreferences prefs = getSharedPreferences("WhatsberryPrefs", MODE_PRIVATE);
        String location = prefs.getString("db_location", DatabaseHelper.LOCATION_EXTERNAL_STANDARD);
        String customPath = prefs.getString("db_custom_path", "");

        String locationName;
        switch (location) {
            case DatabaseHelper.LOCATION_INTERNAL:
                locationName = "Internal Storage";
                break;
            case DatabaseHelper.LOCATION_EXTERNAL_BB10:
                locationName = "BB10 External SD Card";
                break;
            case DatabaseHelper.LOCATION_CUSTOM:
                locationName = "Custom Path: " + customPath;
                break;
            case DatabaseHelper.LOCATION_EXTERNAL_STANDARD:
            default:
                locationName = "External Storage (Standard)";
                break;
        }

        String dbPath = databaseHelper != null ? databaseHelper.getDatabaseLocation() : "Unknown";

        String message = "Current Location: " + locationName + "\n\n" +
                        "Database Path:\n" + dbPath + "\n\n" +
                        "BB10 Detected: " + (DatabaseHelper.isBlackBerry10() ? "Yes" : "No");

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Database Information");
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
