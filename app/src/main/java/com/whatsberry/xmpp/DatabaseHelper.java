package com.whatsberry.xmpp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Database Helper for persistent message storage
 * Stores messages in SQLite directly on external SD card
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "whatsberry.db";
    private static final int DATABASE_VERSION = 4; // Incremented for is_read column

    // Table name
    private static final String TABLE_MESSAGES = "messages";

    // Column names
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_CONTACT_JID = "contact_jid";
    private static final String COLUMN_BODY = "body";
    private static final String COLUMN_IS_SENT = "is_sent";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_FILE_URL = "file_url"; // New column for multimedia files
    private static final String COLUMN_STANZA_ID = "stanza_id"; // XMPP message ID for retraction
    private static final String COLUMN_IS_READ = "is_read"; // 1 if message has been read, 0 if unread

    private Context context;

    public DatabaseHelper(Context context) {
        super(context, getDatabasePath(context), null, DATABASE_VERSION);
        this.context = context;
    }

    // Database location constants
    public static final String LOCATION_INTERNAL = "internal";
    public static final String LOCATION_EXTERNAL_STANDARD = "external_standard";
    public static final String LOCATION_EXTERNAL_BB10 = "external_bb10";
    public static final String LOCATION_CUSTOM = "custom";

    /**
     * Get database path based on user preference
     */
    private static String getDatabasePath(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("WhatsberryPrefs", Context.MODE_PRIVATE);
        String location = prefs.getString("db_location", LOCATION_EXTERNAL_STANDARD);
        String customPath = prefs.getString("db_custom_path", "");

        Log.d(TAG, "Database location preference: " + location);

        switch (location) {
            case LOCATION_INTERNAL:
                // Internal storage (most secure, deleted on uninstall)
                return DATABASE_NAME;

            case LOCATION_EXTERNAL_BB10:
                // BlackBerry 10 external SD card
                File bb10Dir = new File("/mnt/sdcard/external_sd/Whatsberry");
                if (!bb10Dir.exists()) {
                    bb10Dir.mkdirs();
                    Log.d(TAG, "Created BB10 directory: " + bb10Dir.getAbsolutePath());
                }
                File bb10DbFile = new File(bb10Dir, DATABASE_NAME);
                Log.d(TAG, "Database path (BB10): " + bb10DbFile.getAbsolutePath());
                return bb10DbFile.getAbsolutePath();

            case LOCATION_CUSTOM:
                // Custom path specified by user
                if (customPath.isEmpty()) {
                    Log.w(TAG, "Custom path empty, falling back to standard external");
                    location = LOCATION_EXTERNAL_STANDARD;
                    // Fall through to LOCATION_EXTERNAL_STANDARD
                } else {
                    File customDir = new File(customPath, "Whatsberry");
                    if (!customDir.exists()) {
                        customDir.mkdirs();
                        Log.d(TAG, "Created custom directory: " + customDir.getAbsolutePath());
                    }
                    File customDbFile = new File(customDir, DATABASE_NAME);
                    Log.d(TAG, "Database path (Custom): " + customDbFile.getAbsolutePath());
                    return customDbFile.getAbsolutePath();
                }

            case LOCATION_EXTERNAL_STANDARD:
            default:
                // Standard Android external storage
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    Log.w(TAG, "External storage not available, using internal storage");
                    return DATABASE_NAME;
                }

                File whatsberryDir = new File(Environment.getExternalStorageDirectory(), "Whatsberry");
                if (!whatsberryDir.exists()) {
                    whatsberryDir.mkdirs();
                    Log.d(TAG, "Created standard external directory: " + whatsberryDir.getAbsolutePath());
                }

                File dbFile = new File(whatsberryDir, DATABASE_NAME);
                Log.d(TAG, "Database path (Standard External): " + dbFile.getAbsolutePath());
                return dbFile.getAbsolutePath();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_MESSAGES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_CONTACT_JID + " TEXT NOT NULL, " +
                COLUMN_BODY + " TEXT NOT NULL, " +
                COLUMN_IS_SENT + " INTEGER NOT NULL, " +
                COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                COLUMN_FILE_URL + " TEXT, " + // NULL for text-only messages
                COLUMN_STANZA_ID + " TEXT, " + // XMPP message ID, NULL if not available
                COLUMN_IS_READ + " INTEGER NOT NULL DEFAULT 0" + // 0 = unread, 1 = read
                ")";

        db.execSQL(createTable);

        // Create index on contact_jid for faster queries
        db.execSQL("CREATE INDEX idx_contact_jid ON " + TABLE_MESSAGES + "(" + COLUMN_CONTACT_JID + ")");

        Log.d(TAG, "Database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // Handle migration from version 1 to 2 (add file_url column)
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN " + COLUMN_FILE_URL + " TEXT");
                Log.d(TAG, "Added file_url column to messages table");
            } catch (Exception e) {
                Log.e(TAG, "Error adding file_url column", e);
                // If column already exists, this is fine
            }
        }

        // Handle migration from version 2 to 3 (add stanza_id column)
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN " + COLUMN_STANZA_ID + " TEXT");
                Log.d(TAG, "Added stanza_id column to messages table");
            } catch (Exception e) {
                Log.e(TAG, "Error adding stanza_id column", e);
                // If column already exists, this is fine
            }
        }

        // Handle migration from version 3 to 4 (add is_read column)
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN " + COLUMN_IS_READ + " INTEGER NOT NULL DEFAULT 0");
                Log.d(TAG, "Added is_read column to messages table");

                // Mark all sent messages as read (we only care about received messages)
                db.execSQL("UPDATE " + TABLE_MESSAGES + " SET " + COLUMN_IS_READ + " = 1 WHERE " + COLUMN_IS_SENT + " = 1");
                Log.d(TAG, "Marked all sent messages as read");
            } catch (Exception e) {
                Log.e(TAG, "Error adding is_read column", e);
                // If column already exists, this is fine
            }
        }

        // For future upgrades, add more conditions here
    }

    /**
     * Insert a message into the database
     */
    public long insertMessage(String contactJid, String body, boolean isSent, long timestamp) {
        return insertMessage(contactJid, body, isSent, timestamp, null, null);
    }

    /**
     * Insert a message into the database with optional file URL
     */
    public long insertMessage(String contactJid, String body, boolean isSent, String fileUrl) {
        return insertMessage(contactJid, body, isSent, System.currentTimeMillis(), fileUrl, null);
    }

    /**
     * Insert a message into the database with stanza ID (for retraction)
     */
    public long insertMessage(String contactJid, String body, boolean isSent, long timestamp, String fileUrl, String stanzaId) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_CONTACT_JID, contactJid);
        values.put(COLUMN_BODY, body);
        values.put(COLUMN_IS_SENT, isSent ? 1 : 0);
        values.put(COLUMN_TIMESTAMP, timestamp);
        // Sent messages are automatically marked as read, received messages are unread
        values.put(COLUMN_IS_READ, isSent ? 1 : 0);
        if (fileUrl != null && !fileUrl.isEmpty()) {
            values.put(COLUMN_FILE_URL, fileUrl);
        }
        if (stanzaId != null && !stanzaId.isEmpty()) {
            values.put(COLUMN_STANZA_ID, stanzaId);
        }

        long id = db.insert(TABLE_MESSAGES, null, values);

        Log.d(TAG, "Inserted message: " + id + " for " + contactJid +
              (fileUrl != null ? " (file)" : "") +
              (stanzaId != null ? " [stanza:" + stanzaId + "]" : "") +
              (!isSent ? " [UNREAD]" : ""));

        return id;
    }

    /**
     * Get messages for a specific contact
     */
    public List<MessageRecord> getMessagesForContact(String contactJid, int limit) {
        List<MessageRecord> messages = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_MESSAGES +
                " WHERE " + COLUMN_CONTACT_JID + " = ?" +
                " ORDER BY " + COLUMN_TIMESTAMP + " ASC" +
                (limit > 0 ? " LIMIT " + limit : "");

        Cursor cursor = db.rawQuery(query, new String[]{contactJid});

        if (cursor.moveToFirst()) {
            do {
                MessageRecord msg = new MessageRecord();
                msg.id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID));
                msg.contactJid = cursor.getString(cursor.getColumnIndex(COLUMN_CONTACT_JID));
                msg.body = cursor.getString(cursor.getColumnIndex(COLUMN_BODY));
                msg.isSent = cursor.getInt(cursor.getColumnIndex(COLUMN_IS_SENT)) == 1;
                msg.timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP));

                // Read file URL if present
                int fileUrlIndex = cursor.getColumnIndex(COLUMN_FILE_URL);
                if (fileUrlIndex != -1) {
                    msg.fileUrl = cursor.getString(fileUrlIndex);
                }

                // Read stanza ID if present
                int stanzaIdIndex = cursor.getColumnIndex(COLUMN_STANZA_ID);
                if (stanzaIdIndex != -1) {
                    msg.stanzaId = cursor.getString(stanzaIdIndex);
                }

                // Read is_read status
                int isReadIndex = cursor.getColumnIndex(COLUMN_IS_READ);
                if (isReadIndex != -1) {
                    msg.isRead = cursor.getInt(isReadIndex) == 1;
                } else {
                    msg.isRead = msg.isSent; // Fallback: sent messages are read
                }

                messages.add(msg);
            } while (cursor.moveToNext());
        }

        cursor.close();

        Log.d(TAG, "Loaded " + messages.size() + " messages for " + contactJid);

        return messages;
    }

    /**
     * Get total number of messages
     */
    public int getMessageCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MESSAGES, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * Delete old messages (keep only last N messages per contact)
     */
    public void cleanOldMessages(int keepPerContact) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Get all unique contact JIDs
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + COLUMN_CONTACT_JID + " FROM " + TABLE_MESSAGES, null);

        if (cursor.moveToFirst()) {
            do {
                String contactJid = cursor.getString(0);

                // Delete old messages for this contact, keeping only the last N
                String deleteQuery = "DELETE FROM " + TABLE_MESSAGES +
                        " WHERE " + COLUMN_CONTACT_JID + " = ?" +
                        " AND " + COLUMN_ID + " NOT IN (" +
                        "   SELECT " + COLUMN_ID + " FROM " + TABLE_MESSAGES +
                        "   WHERE " + COLUMN_CONTACT_JID + " = ?" +
                        "   ORDER BY " + COLUMN_TIMESTAMP + " DESC" +
                        "   LIMIT " + keepPerContact +
                        ")";

                db.execSQL(deleteQuery, new String[]{contactJid, contactJid});
            } while (cursor.moveToNext());
        }

        cursor.close();

        Log.d(TAG, "Cleaned old messages, keeping " + keepPerContact + " per contact");
    }

    /**
     * Get last message for each contact (optimized for chat list)
     * Returns map of contactJid -> last MessageRecord
     */
    public java.util.Map<String, MessageRecord> getLastMessagePerContact() {
        java.util.Map<String, MessageRecord> lastMessages = new java.util.HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Optimized query to get last message per contact
        String query = "SELECT m.* FROM " + TABLE_MESSAGES + " m " +
                "INNER JOIN (" +
                "   SELECT " + COLUMN_CONTACT_JID + ", MAX(" + COLUMN_TIMESTAMP + ") as max_time " +
                "   FROM " + TABLE_MESSAGES + " " +
                "   GROUP BY " + COLUMN_CONTACT_JID +
                ") latest " +
                "ON m." + COLUMN_CONTACT_JID + " = latest." + COLUMN_CONTACT_JID + " " +
                "AND m." + COLUMN_TIMESTAMP + " = latest.max_time";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                MessageRecord msg = new MessageRecord();
                msg.id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID));
                msg.contactJid = cursor.getString(cursor.getColumnIndex(COLUMN_CONTACT_JID));
                msg.body = cursor.getString(cursor.getColumnIndex(COLUMN_BODY));
                msg.isSent = cursor.getInt(cursor.getColumnIndex(COLUMN_IS_SENT)) == 1;
                msg.timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP));

                // Read file URL if present
                int fileUrlIndex = cursor.getColumnIndex(COLUMN_FILE_URL);
                if (fileUrlIndex != -1) {
                    msg.fileUrl = cursor.getString(fileUrlIndex);
                }

                // Read stanza ID if present
                int stanzaIdIndex = cursor.getColumnIndex(COLUMN_STANZA_ID);
                if (stanzaIdIndex != -1) {
                    msg.stanzaId = cursor.getString(stanzaIdIndex);
                }

                // Read is_read status
                int isReadIndex = cursor.getColumnIndex(COLUMN_IS_READ);
                if (isReadIndex != -1) {
                    msg.isRead = cursor.getInt(isReadIndex) == 1;
                } else {
                    msg.isRead = msg.isSent; // Fallback: sent messages are read
                }

                lastMessages.put(msg.contactJid, msg);
            } while (cursor.moveToNext());
        }

        cursor.close();

        Log.d(TAG, "Loaded last messages for " + lastMessages.size() + " contacts");

        return lastMessages;
    }

    /**
     * Get database location info
     */
    public String getDatabaseLocation() {
        return getDatabasePath(context);
    }

    /**
     * Search messages by query string
     * @param query Search query (searches in message body)
     * @param contactJid Optional: filter by specific contact, null for all contacts
     * @param limit Maximum number of results
     * @return List of matching messages
     */
    public List<MessageRecord> searchMessages(String query, String contactJid, int limit) {
        List<MessageRecord> messages = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return messages;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();

            String selection;
            String[] selectionArgs;

            if (contactJid != null && !contactJid.isEmpty()) {
                // Search in specific contact
                selection = COLUMN_BODY + " LIKE ? AND " + COLUMN_CONTACT_JID + " = ?";
                selectionArgs = new String[]{"%" + query + "%", contactJid};
            } else {
                // Search in all contacts
                selection = COLUMN_BODY + " LIKE ?";
                selectionArgs = new String[]{"%" + query + "%"};
            }

            cursor = db.query(
                TABLE_MESSAGES,
                null, // all columns
                selection,
                selectionArgs,
                null,
                null,
                COLUMN_TIMESTAMP + " DESC", // newest first
                String.valueOf(limit)
            );

            while (cursor.moveToNext()) {
                MessageRecord record = new MessageRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                record.contactJid = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT_JID));
                record.body = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BODY));
                record.isSent = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_SENT)) == 1;
                record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));

                // Read file URL and stanza ID (may be null)
                try {
                    record.fileUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_URL));
                } catch (Exception e) {
                    record.fileUrl = null;
                }

                try {
                    record.stanzaId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STANZA_ID));
                } catch (Exception e) {
                    record.stanzaId = null;
                }

                // Read is_read status
                try {
                    record.isRead = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_READ)) == 1;
                } catch (Exception e) {
                    record.isRead = record.isSent; // Fallback
                }

                messages.add(record);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error searching messages", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Log.d(TAG, "Search query '" + query + "' returned " + messages.size() + " results");
        return messages;
    }

    /**
     * Delete a specific message by ID
     */
    public boolean deleteMessage(long messageId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_MESSAGES, COLUMN_ID + " = ?", new String[]{String.valueOf(messageId)});
        Log.d(TAG, "Deleted message ID " + messageId + " (" + rows + " rows affected)");
        return rows > 0;
    }

    /**
     * Update/edit a message body
     */
    public boolean updateMessage(long messageId, String newBody) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BODY, newBody);

        int rows = db.update(TABLE_MESSAGES, values, COLUMN_ID + " = ?", new String[]{String.valueOf(messageId)});
        Log.d(TAG, "Updated message ID " + messageId + " (" + rows + " rows affected)");
        return rows > 0;
    }

    /**
     * Mark all messages from a contact as read
     */
    public int markMessagesAsRead(String contactJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_READ, 1);

        // Only mark received messages as read (not sent ones, they're already read)
        int rows = db.update(TABLE_MESSAGES, values,
                COLUMN_CONTACT_JID + " = ? AND " + COLUMN_IS_SENT + " = 0 AND " + COLUMN_IS_READ + " = 0",
                new String[]{contactJid});

        Log.d(TAG, "Marked " + rows + " messages as read for contact " + contactJid);
        return rows;
    }

    /**
     * Count unread messages for a contact
     */
    public int countUnreadMessages(String contactJid) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Count received messages that are not read
        String query = "SELECT COUNT(*) FROM " + TABLE_MESSAGES +
                " WHERE " + COLUMN_CONTACT_JID + " = ?" +
                " AND " + COLUMN_IS_SENT + " = 0" +
                " AND " + COLUMN_IS_READ + " = 0";

        Cursor cursor = db.rawQuery(query, new String[]{contactJid});

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * Delete all messages for a specific contact (clear conversation)
     */
    public int deleteAllMessagesForContact(String contactJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_MESSAGES, COLUMN_CONTACT_JID + " = ?", new String[]{contactJid});
        Log.d(TAG, "Deleted all messages for contact " + contactJid + " (" + rows + " rows affected)");
        return rows;
    }

    /**
     * Detect if running on BlackBerry 10
     */
    public static boolean isBlackBerry10() {
        File bb10Indicator = new File("/mnt/sdcard/external_sd");
        boolean isBB10 = bb10Indicator.exists() && bb10Indicator.isDirectory();
        Log.d(TAG, "BB10 detection: " + isBB10);
        return isBB10;
    }

    /**
     * Get all available database locations with their descriptions
     */
    public static String[] getAvailableLocations(Context context) {
        java.util.List<String> locations = new java.util.ArrayList<>();

        // Internal storage (always available)
        locations.add(LOCATION_INTERNAL + "|Internal Storage (Secure, deleted on uninstall)");

        // External storage (if available)
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            locations.add(LOCATION_EXTERNAL_STANDARD + "|External Storage (/sdcard/Whatsberry/)");
        }

        // BB10 external SD card (if exists)
        if (isBlackBerry10()) {
            locations.add(LOCATION_EXTERNAL_BB10 + "|BB10 External SD (/mnt/sdcard/external_sd/Whatsberry/)");
        }

        // Custom path (always available)
        locations.add(LOCATION_CUSTOM + "|Custom Path (Specify manually)");

        return locations.toArray(new String[0]);
    }

    /**
     * Migrate database from current location to new location
     * @return true if migration successful, false otherwise
     */
    public static boolean migrateDatabase(Context context, String newLocation, String customPath) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("WhatsberryPrefs", Context.MODE_PRIVATE);
        String oldLocation = prefs.getString("db_location", LOCATION_EXTERNAL_STANDARD);
        String oldCustomPath = prefs.getString("db_custom_path", "");

        Log.d(TAG, "Migrating database from " + oldLocation + " to " + newLocation);

        // Get old database path
        String oldDbPath = getDatabasePathForLocation(context, oldLocation, oldCustomPath);
        File oldDbFile = new File(oldDbPath);

        // Save new preferences BEFORE getting new path
        prefs.edit()
            .putString("db_location", newLocation)
            .putString("db_custom_path", customPath)
            .apply();

        // Get new database path
        String newDbPath = getDatabasePathForLocation(context, newLocation, customPath);
        File newDbFile = new File(newDbPath);

        // If old database doesn't exist, nothing to migrate
        if (!oldDbFile.exists()) {
            Log.d(TAG, "No existing database to migrate");
            return true;
        }

        // If paths are the same, nothing to do
        if (oldDbPath.equals(newDbPath)) {
            Log.d(TAG, "Database already at target location");
            return true;
        }

        // Create parent directory for new database
        File newDbDir = newDbFile.getParentFile();
        if (newDbDir != null && !newDbDir.exists()) {
            newDbDir.mkdirs();
        }

        // Copy database file
        try {
            copyFile(oldDbFile, newDbFile);
            Log.d(TAG, "✅ Database migrated successfully to " + newDbPath);

            // Optionally delete old database (commented out for safety)
            // oldDbFile.delete();
            // Log.d(TAG, "Old database deleted");

            return true;
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to migrate database", e);

            // Restore old preferences on failure
            prefs.edit()
                .putString("db_location", oldLocation)
                .putString("db_custom_path", oldCustomPath)
                .apply();

            return false;
        }
    }

    /**
     * Get database path for a specific location (helper method)
     */
    private static String getDatabasePathForLocation(Context context, String location, String customPath) {
        switch (location) {
            case LOCATION_INTERNAL:
                return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();

            case LOCATION_EXTERNAL_BB10:
                return new File("/mnt/sdcard/external_sd/Whatsberry", DATABASE_NAME).getAbsolutePath();

            case LOCATION_CUSTOM:
                if (!customPath.isEmpty()) {
                    return new File(customPath, "Whatsberry/" + DATABASE_NAME).getAbsolutePath();
                }
                // Fall through to default

            case LOCATION_EXTERNAL_STANDARD:
            default:
                return new File(Environment.getExternalStorageDirectory(), "Whatsberry/" + DATABASE_NAME).getAbsolutePath();
        }
    }

    /**
     * Copy file from source to destination
     */
    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream inStream = null;
        FileOutputStream outStream = null;

        try {
            inStream = new FileInputStream(src);
            outStream = new FileOutputStream(dst);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }

            Log.d(TAG, "Copied " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }

    /**
     * Message record class
     */
    public static class MessageRecord {
        public long id;
        public String contactJid;
        public String body;
        public boolean isSent;
        public long timestamp;
        public String fileUrl; // URL to multimedia file, null for text-only messages
        public String stanzaId; // XMPP message ID for retraction, null if not available
        public boolean isRead; // True if message has been read

        /**
         * Check if this message contains a file
         */
        public boolean hasFile() {
            return fileUrl != null && !fileUrl.isEmpty();
        }

        /**
         * Check if this message can be retracted (has stanza ID)
         */
        public boolean canRetract() {
            return stanzaId != null && !stanzaId.isEmpty();
        }
    }
}
