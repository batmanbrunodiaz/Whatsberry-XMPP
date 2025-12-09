# Architecture Diagrams - WhatsBerry

Technical diagrams and flows to understand the complete project operation.

---

## 📱 User Flow in the App (BlackBerry 10)

### First Time - Registration and Pairing

```
┌─────────────────────────────────────────────────────────────┐
│                    FIRST TIME - FLOW                        │
└─────────────────────────────────────────────────────────────┘

1. User opens app
   │
   ├─> [MainActivity.java]
   │   ├─ Shows configuration fields
   │   │  ├─ Server: whatsberry.descarga.media
   │   │  ├─ Port: 5222
   │   │  ├─ Domain: whatsberry.descarga.media
   │   │  ├─ Username: yourname
   │   │  ├─ Password: yourpassword
   │   │  └─ Gateway: whatsapp.localhost
   │   │
   │   └─ User clicks "Register New Account"
   │
   ├─> [XMPPManager.java]
   │   ├─ Connects to XMPP server (port 5222)
   │   ├─ Negotiates STARTTLS with proxy
   │   ├─ Registers account on Prosody
   │   └─ Auto-login
   │
   ├─> [WhatsAppManager.java]
   │   ├─ Executes ad-hoc command "register"
   │   ├─ Gateway creates session
   │   ├─ Executes ad-hoc command "PairPhone"
   │   └─ Receives QR code (base64)
   │
   ├─> [MainActivity.java]
   │   ├─ Displays QR code in ImageView
   │   └─ Waits for pairing
   │
   ├─> User scans QR with official WhatsApp
   │
   ├─> [WhatsAppManager.java]
   │   ├─ Gateway detects successful pairing
   │   ├─ Downloads roster (contacts)
   │   └─ Sends presence (available)
   │
   ├─> [XMPPService.java]
   │   ├─ Saves credentials in SharedPreferences
   │   ├─ Starts Foreground Service
   │   └─ Keeps connection active
   │
   └─> [MainTabsActivity.java]
       ├─ Loads WhatsApp contacts
       ├─ Loads recent chats
       └─ Ready to chat!
```

---

## 🔄 Existing User Flow - Auto-Login

```
┌─────────────────────────────────────────────────────────────┐
│                 EXISTING USER - FLOW                        │
└─────────────────────────────────────────────────────────────┘

1. User opens app
   │
   ├─> [MainActivity.java]
   │   ├─ Reads credentials from SharedPreferences
   │   ├─ Finds saved credentials
   │   └─ Starts XMPPService automatically
   │
   ├─> [XMPPService.java]
   │   ├─ autoLogin() executed
   │   ├─ Connects to server (port 5222)
   │   ├─ Negotiates STARTTLS
   │   ├─ Login with saved credentials
   │   └─ Starts as Foreground Service
   │
   ├─> [MainActivity.java] - Automatic skip
   │   └─ Intent → MainTabsActivity
   │
   ├─> [MainTabsActivity.java]
   │   ├─ Loads contacts (from local DB + XMPP)
   │   ├─ Loads recent chats (from local DB)
   │   └─ Syncs with server
   │
   └─> User sees their chats and can message immediately
```

---

## 💬 Message Sending Flow

```
┌─────────────────────────────────────────────────────────────┐
│                   SEND MESSAGE - FLOW                       │
└─────────────────────────────────────────────────────────────┘

1. User in ChatActivity
   │
   ├─> User types message in EditText
   ├─> User presses "Send"
   │
   ├─> [ChatActivity.java]
   │   ├─ Captures message text
   │   ├─ Gets contact's JID (e.g., +1234567890@whatsapp.localhost)
   │   └─ Calls sendMessage()
   │
   ├─> [XMPPManager.java]
   │   ├─ Creates Message object
   │   ├─ message.setTo(contactJid)
   │   ├─ message.setBody(messageText)
   │   ├─ message.setFrom(myJid)
   │   └─ connection.sendStanza(message)
   │
   ├─> [XMPP Network]
   │   Client → STARTTLS Proxy (TLS encrypted)
   │            │
   │            ├─> Proxy → Prosody (plaintext localhost)
   │                       │
   │                       ├─> Prosody → Slidge (component)
   │                                    │
   │                                    ├─> Slidge → WhatsApp Servers
   │
   ├─> [DatabaseHelper.java]
   │   ├─ Saves message in local DB
   │   ├─ Status: "sending"
   │   └─ Current timestamp
   │
   ├─> [ChatActivity.java]
   │   ├─ Adds message to ListView
   │   ├─ Auto-scroll to bottom
   │   └─ Clears EditText
   │
   └─> Confirmation from WhatsApp (ack)
       │
       ├─> [XMPPManager.java] - MessageListener
       │   └─ Receives delivery confirmation
       │
       └─> [DatabaseHelper.java]
           ├─ Updates message status
           └─ Status: "delivered" or "read"
```

---

## 📥 Message Reception Flow

```
┌─────────────────────────────────────────────────────────────┐
│                   RECEIVE MESSAGE - FLOW                    │
└─────────────────────────────────────────────────────────────┘

1. WhatsApp Server sends message
   │
   ├─> Slidge Gateway receives from WhatsApp server
   ├─> Slidge converts to XMPP format
   ├─> Slidge sends stanza to Prosody
   ├─> Prosody routes to connected user
   ├─> STARTTLS Proxy forwards (encrypted)
   │
   ├─> [XMPPService.java] - MessageListener
   │   ├─ onMessageReceived() triggered
   │   ├─ Extracts: from, body, timestamp
   │   └─ Processes message
   │
   ├─> [DatabaseHelper.java]
   │   ├─ Saves message in local DB
   │   │  ├─ chat_id
   │   │  ├─ from_jid
   │   │  ├─ body
   │   │  ├─ timestamp
   │   │  └─ is_read = 0 (unread)
   │   └─ Returns messageId
   │
   ├─> [Notification]
   │   │
   │   ├─ If app in background:
   │   │  ├─> NotificationManager
   │   │  ├─> Creates notification
   │   │  ├─> Shows in BB10 Hub ✅
   │   │  └─> Sound/vibration
   │   │
   │   └─ If app in foreground:
   │       └─> Only updates UI
   │
   ├─> [MainTabsActivity.java]
   │   ├─ If visible:
   │   │  ├─> Updates chat list
   │   │  ├─> Moves chat to top
   │   │  └─> Increments unread counter
   │   │
   │   └─ If not visible:
   │       └─> Updates in background
   │
   └─> [ChatActivity.java]
       └─ If chat is open:
          ├─> Adds message to ListView
          ├─> Auto-scroll to bottom
          └─> markMessagesAsRead()
              └─> DB: is_read = 1
```

---

## 🔌 Detailed XMPP Connection Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    NETWORK ARCHITECTURE                          │
└──────────────────────────────────────────────────────────────────┘

BlackBerry 10 Device (10.1.1.2 - local network)
  │
  │ [Smack XMPP 4.1.9 Library]
  │ - XMPPTCPConnection
  │ - ConnectionConfiguration
  │ - SecurityMode: required (STARTTLS)
  │
  ├─ TCP Socket open to whatsberry.descarga.media:5222
  │
  ▼
┌─────────────────────────────────────────┐
│  XMPP STARTTLS Proxy (Node.js)          │
│  - IP: 0.0.0.0 (listens on all)         │
│  - Port: 5222                           │
│  - TLS Versions: 1.0, 1.1, 1.2, 1.3    │
│  - Certs: /etc/prosody/certs/           │
└─────────────────────────────────────────┘
  │
  │ PHASE 1: Initial connection (plaintext)
  │ ├─> Client: <stream:stream>
  │ ├─> Proxy → Prosody: forward
  │ ├─> Prosody → Proxy: <stream:features>
  │ └─> Proxy → Client: <stream:features> + <starttls/> INJECTED
  │
  │ PHASE 2: STARTTLS Negotiation
  │ ├─> Client → Proxy: <starttls/>
  │ ├─> Proxy destroys old Prosody connection
  │ ├─> Proxy → Client: <proceed/>
  │ └─> TLS Handshake (TLS 1.0 negotiated)
  │
  │ PHASE 3: Post-TLS (new connection)
  │ ├─> Proxy creates NEW TCP connection to Prosody:5200
  │ ├─> Client → Proxy: encrypted data (TLS)
  │ ├─> Proxy → Prosody: plaintext data (localhost)
  │ └─> Bidirectional relay established ✅
  │
  ├─ Plaintext connection to localhost:5200
  │
  ▼
┌─────────────────────────────────────────┐
│  Prosody XMPP Server                    │
│  - IP: 127.0.0.1 (localhost only)       │
│  - c2s Port: 5200 (NO TLS)              │
│  - Component Port: 5347                 │
│  - VirtualHost: whatsberry.descarga...  │
│  - modules_disabled = { "tls" }         │
└─────────────────────────────────────────┘
  │
  │ XMPP Stanzas:
  │ - <iq> (info queries - ad-hoc commands)
  │ - <message> (chat messages)
  │ - <presence> (online status)
  │
  ├─ Component connection (XEP-0114)
  │
  ▼
┌─────────────────────────────────────────┐
│  Slidge WhatsApp Gateway                │
│  - Docker container                     │
│  - network_mode: host                   │
│  - JID: whatsapp.localhost              │
│  - Secret: shared with Prosody          │
│  - Port: 5347 (localhost)               │
│  - Config: ~/.local/share/slidge/       │
└─────────────────────────────────────────┘
  │
  │ [whatsmeow Library - Go]
  │ - WebSocket to WhatsApp
  │ - Protobuf messages
  │ - E2E encryption (Signal protocol)
  │
  ├─ WebSocket/HTTPS
  │
  ▼
┌─────────────────────────────────────────┐
│  WhatsApp Servers                       │
│  - web.whatsapp.com                     │
│  - *.whatsapp.net                       │
└─────────────────────────────────────────┘
```

---

## 🗂️ Android App Class Structure

```
┌──────────────────────────────────────────────────────────────┐
│                   MAIN CLASSES                               │
└──────────────────────────────────────────────────────────────┘

MainActivity
├─ Responsibilities:
│  ├─ Login screen
│  ├─ XMPP account registration
│  ├─ Server configuration
│  ├─ Database settings dialog
│  └─ Display QR code for pairing
│
├─ Fields:
│  ├─ EditText: etServer, etPort, etDomain
│  ├─ EditText: etUsername, etPassword, etGateway
│  ├─ ImageView: ivQrCode
│  └─ Buttons: btnLogin, btnRegister, btnDatabaseSettings
│
└─ Key methods:
   ├─ loadSavedSettings() - Reads SharedPreferences
   ├─ saveSettings() - Saves configuration
   ├─ showDatabaseSettings() - DB location dialog
   └─ startMainActivity() - Intent to MainTabsActivity

─────────────────────────────────────────────────────────────

MainTabsActivity
├─ Responsibilities:
│  ├─ Recent chats list
│  ├─ Contacts list
│  ├─ Options menu
│  └─ Main navigation
│
├─ UI Components:
│  ├─ ListView: lvChats
│  ├─ ListView: lvContacts
│  ├─ Buttons: btnChats, btnContacts, btnMenu
│  └─ PopupMenu: Refresh, Database Settings, Logout
│
└─ Key methods:
   ├─ loadChats() - Loads from DB
   ├─ loadContacts() - Loads from DB + XMPP
   ├─ showOptionsMenu() - Shows menu
   └─ logout() - Closes session

─────────────────────────────────────────────────────────────

ChatActivity
├─ Responsibilities:
│  ├─ Display conversation with contact
│  ├─ Send messages
│  ├─ Receive real-time messages
│  └─ Mark as read
│
├─ UI Components:
│  ├─ ListView: lvMessages
│  ├─ EditText: etMessage
│  ├─ Button: btnSend
│  └─ MessageAdapter (custom)
│
└─ Key methods:
   ├─ loadMessages() - Loads from DB
   ├─ sendMessage() - Sends via XMPP
   ├─ onNewMessage() - BroadcastReceiver
   └─ markMessagesAsRead() - Updates DB

─────────────────────────────────────────────────────────────

XMPPService (extends Service)
├─ Responsibilities:
│  ├─ Keep XMPP connection active
│  ├─ Foreground service (prevents BB10 from killing it)
│  ├─ Listen for incoming messages
│  └─ Sync state
│
├─ Components:
│  ├─ XMPPManager: connection management
│  ├─ MessageListener: receives messages
│  ├─ PresenceListener: contact status
│  └─ NotificationManager: notifications
│
└─ Key methods:
   ├─ onCreate() - Starts foreground service
   ├─ autoLogin() - Automatic login
   ├─ onMessageReceived() - Processes message
   └─ sendLocalBroadcast() - Notifies UI

─────────────────────────────────────────────────────────────

XMPPManager
├─ Responsibilities:
│  ├─ Manage XMPP connection (Smack)
│  ├─ Authentication
│  ├─ Send stanzas
│  └─ Register listeners
│
├─ Smack Objects:
│  ├─ XMPPTCPConnection
│  ├─ XMPPTCPConnectionConfiguration
│  ├─ ReconnectionManager
│  └─ ChatManager
│
└─ Key methods:
   ├─ connect() - Connects to server
   ├─ login() - SASL authentication
   ├─ register() - Registers new account
   ├─ sendMessage() - Sends message
   └─ addMessageListener() - Listens for messages

─────────────────────────────────────────────────────────────

DatabaseHelper (extends SQLiteOpenHelper)
├─ Responsibilities:
│  ├─ Create and migrate DB schema
│  ├─ CRUD for messages
│  ├─ CRUD for chats
│  └─ DB location management
│
├─ Tables:
│  ├─ messages (id, chat_id, from_jid, body, timestamp, is_read)
│  ├─ chats (id, jid, name, last_message, unread_count)
│  └─ contacts (id, jid, name, phone)
│
└─ Key methods:
   ├─ insertMessage() - Save message
   ├─ getMessages() - Get chat messages
   ├─ markMessagesAsRead() - Mark as read
   ├─ getDatabasePath() - DB location
   └─ migrateDatabase() - Move DB between locations
```

---

## 📊 Sequence Diagram: First Connection

```
User    MainActivity    XMPPManager    STARTTLS Proxy    Prosody    Slidge    WhatsApp
  │          │              │                │             │          │          │
  │ Opens    │              │                │             │          │          │
  │ app      │              │                │             │          │          │
  ├─────────>│              │                │             │          │          │
  │          │              │                │             │          │          │
  │ Enters   │              │                │             │          │          │
  │ credentials              │                │             │          │          │
  ├─────────>│              │                │             │          │          │
  │          │              │                │             │          │          │
  │ Clicks   │              │                │             │          │          │
  │ "Register"               │                │             │          │          │
  ├─────────>│ connect()    │                │             │          │          │
  │          ├─────────────>│                │             │          │          │
  │          │              │ TCP SYN        │             │          │          │
  │          │              ├───────────────>│             │          │          │
  │          │              │                │ TCP to 5200 │          │          │
  │          │              │                ├────────────>│          │          │
  │          │              │                │             │          │          │
  │          │              │ <stream>       │             │          │          │
  │          │              ├───────────────>│─────────────>          │          │
  │          │              │                │<────────────│          │          │
  │          │              │<───────────────│ features    │          │          │
  │          │              │ + STARTTLS     │             │          │          │
  │          │              │                │             │          │          │
  │          │              │ <starttls/>    │             │          │          │
  │          │              ├───────────────>│             │          │          │
  │          │              │                │ [Destroy    │          │          │
  │          │              │                │  old conn]  │          │          │
  │          │              │<───────────────│ <proceed/>  │          │          │
  │          │              │                │             │          │          │
  │          │              │ [TLS 1.0       │             │          │          │
  │          │              │  Handshake]    │             │          │          │
  │          │              │<──────────────>│             │          │          │
  │          │              │ ✅ Encrypted   │             │          │          │
  │          │              │                │             │          │          │
  │          │              │                │ [New TCP    │          │          │
  │          │              │                │  to 5200]   │          │          │
  │          │              │                ├────────────>│          │          │
  │          │              │                │             │          │          │
  │          │ register()   │ (encrypted)    │ (plaintext) │          │          │
  │          ├─────────────>├───────────────>├────────────>│          │          │
  │          │              │                │             │ [Creates │          │
  │          │              │                │             │  account]│          │
  │          │              │                │             │<─────────│          │
  │          │<─────────────│<───────────────│<────────────│ Success  │          │
  │          │              │                │             │          │          │
  │ "Connected!"            │                │             │          │          │
  │<─────────│              │                │             │          │          │
  │          │              │                │             │          │          │
  │          │ PairPhone    │                │             │          │          │
  │          │ (ad-hoc cmd) │                │             │          │          │
  │          ├─────────────>├───────────────>├────────────>├─────────>│          │
  │          │              │                │             │          │ Start    │
  │          │              │                │             │          │ pairing  │
  │          │              │                │             │          ├─────────>│
  │          │              │                │             │          │<─────────│
  │          │              │                │             │          │ QR data  │
  │          │              │                │             │<─────────│          │
  │          │<─────────────│<───────────────│<────────────│ QR (b64) │          │
  │          │              │                │             │          │          │
  │ [Shows QR]              │                │             │          │          │
  │<─────────│              │                │             │          │          │
  │          │              │                │             │          │          │
  │ Scans QR │              │                │             │          │          │
  │ with WhatsApp           │                │             │          │          │
  ├──────────┼──────────────┼────────────────┼─────────────┼──────────┼─────────>│
  │          │              │                │             │          │<─────────│
  │          │              │                │             │          │ Paired!  │
  │          │              │                │             │<─────────│          │
  │          │<─────────────│<───────────────│<────────────│ Success  │          │
  │          │              │                │             │          │          │
  │ "Paired!"│              │                │             │          │          │
  │<─────────│              │                │             │          │          │
  │          │              │                │             │          │          │
  │ → MainTabs              │                │             │          │          │
  │          │              │                │             │          │          │
```

---

## 🔐 Database - Current Schema (v4)

```sql
-- Table: messages
CREATE TABLE messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id TEXT NOT NULL,              -- Chat JID (e.g., +1234@whatsapp.localhost)
    from_jid TEXT NOT NULL,             -- Sender's JID
    to_jid TEXT,                        -- Recipient's JID
    body TEXT,                          -- Message content
    timestamp INTEGER NOT NULL,         -- Unix timestamp
    is_from_me INTEGER DEFAULT 0,      -- 1 if own message, 0 if received
    is_read INTEGER DEFAULT 0,          -- 0 = unread, 1 = read
    message_id TEXT,                    -- Unique message ID (optional)
    has_media INTEGER DEFAULT 0         -- 1 if has media, 0 if text only
);

-- Table: chats
CREATE TABLE chats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jid TEXT UNIQUE NOT NULL,           -- Chat JID
    name TEXT,                          -- Contact/group name
    last_message TEXT,                  -- Preview of last message
    last_message_timestamp INTEGER,     -- Timestamp of last message
    unread_count INTEGER DEFAULT 0      -- Number of unread messages
);

-- Table: contacts
CREATE TABLE contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jid TEXT UNIQUE NOT NULL,           -- Contact JID
    name TEXT,                          -- Contact name
    phone TEXT,                         -- Phone number
    is_my_contact INTEGER DEFAULT 1     -- 1 if in my list, 0 if not
);

-- Indexes for performance
CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_messages_timestamp ON messages(timestamp DESC);
CREATE INDEX idx_messages_is_read ON messages(is_read);
CREATE INDEX idx_chats_timestamp ON chats(last_message_timestamp DESC);
```

**DB Locations** (configurable since v3.3.0):

1. **Internal Storage** (default Android):
   - Path: `/data/data/com.whatsberry.xmpp/databases/whatsberry.db`
   - Secure: ✅ Deleted on uninstall
   - Accessible: ❌ Not from file manager

2. **External Standard**:
   - Path: `/sdcard/Whatsberry/whatsberry.db`
   - Secure: ⚠️ Persists after uninstall
   - Accessible: ✅ From file manager

3. **BB10 External SD**:
   - Path: `/mnt/sdcard/external_sd/Whatsberry/whatsberry.db`
   - Secure: ⚠️ On physical SD card
   - Accessible: ✅ Removable

4. **Custom Path**:
   - Path: User-defined
   - Secure: Depends on location
   - Accessible: Depends on location

---

## 🔔 Notification System (v3.1.9+)

```
┌──────────────────────────────────────────────────────────────┐
│         NOTIFICATION SYSTEM - BB10 HUB                      │
└──────────────────────────────────────────────────────────────┘

Incoming message
  │
  ├─> [XMPPService.java] - onMessageReceived()
  │   │
  │   ├─ Save in DatabaseHelper
  │   │
  │   ├─ Check app state:
  │   │  │
  │   │  ├─ If ChatActivity is open with this chat:
  │   │  │  ├─> DON'T show notification
  │   │  │  ├─> Update ListView directly
  │   │  │  └─> markMessagesAsRead() automatic
  │   │  │
  │   │  └─ If app in background or different chat:
  │   │     │
  │   │     └─> Create notification ▼
  │   │
  │   └─> createNotification()
  │       │
  │       ├─ NotificationCompat.Builder
  │       │  ├─ setSmallIcon(R.drawable.ic_notification)
  │       │  ├─ setContentTitle("Contact name")
  │       │  ├─ setContentText("Message preview")
  │       │  ├─ setTicker("New message")  ← Important for BB10
  │       │  ├─ setDefaults(Notification.DEFAULT_ALL)
  │       │  │  ├─> DEFAULT_SOUND
  │       │  │  ├─> DEFAULT_VIBRATE
  │       │  │  └─> DEFAULT_LIGHTS
  │       │  └─ setContentIntent(openChatIntent)
  │       │
  │       └─ NotificationManager.notify(notificationId, notification)
  │          │
  │          └─> ✅ Appears in BlackBerry Hub
  │              ├─ Configurable sound
  │              ├─ Vibration
  │              ├─ LED notification
  │              └─ Message preview
```

**Foreground Service** (CRITICAL for BB10):

```java
// XMPPService.java - onCreate()

// Create persistent notification
Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle("WhatsBerry")
    .setContentText("Connected")
    .setSmallIcon(R.drawable.ic_notification)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .build();

// Start as Foreground Service
startForeground(FOREGROUND_NOTIFICATION_ID, notification);
```

**Why it's critical**:
- BB10 aggressively kills background services
- Foreground Service has high priority
- Ensures XMPPService keeps running
- Allows receiving real-time messages

---

## 🎨 Conclusion

These diagrams cover:

1. ✅ **User flows** - First time and existing users
2. ✅ **Message send/receive** - Complete flow
3. ✅ **Network architecture** - Technical connection details
4. ✅ **Class structure** - Code organization
5. ✅ **Sequence diagram** - First connection step-by-step
6. ✅ **Database schema** - Structure and locations
7. ✅ **Notification system** - How they work on BB10

---

**File**: `docs/ARCHITECTURE_DIAGRAMS-en.md`
**Version**: 1.0
**Date**: December 8, 2024
