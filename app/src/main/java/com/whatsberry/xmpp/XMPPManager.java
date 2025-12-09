package com.whatsberry.xmpp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.ChatStateManager;
import org.jivesoftware.smackx.chatstates.ChatStateListener;
import org.jivesoftware.smackx.ping.PingManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XMPP Connection Manager
 * Handles all XMPP operations: connect, login, register, messaging
 */
public class XMPPManager {
    private static final String TAG = "XMPPManager";

    private static XMPPManager instance;
    private AbstractXMPPConnection connection;
    private ChatManager chatManager;
    private ChatStateManager chatStateManager;
    private Handler mainHandler;
    private java.util.Set<String> chatsWithListeners = new java.util.HashSet<>();
    private java.util.Set<String> recentMessages = new java.util.HashSet<>();
    private Handler cleanupHandler = new Handler(Looper.getMainLooper());

    // Callbacks
    private ConnectionCallback connectionCallback;
    private MessageCallback messageCallback;
    private TypingStateCallback typingStateCallback;
    private MessageRetractCallback messageRetractCallback;

    // Database for persistent storage
    private DatabaseHelper databaseHelper;
    private Context context;

    private XMPPManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Initialize with context (must be called before using)
     */
    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        this.databaseHelper = new DatabaseHelper(this.context);
    }

    public static synchronized XMPPManager getInstance() {
        if (instance == null) {
            instance = new XMPPManager();
        }
        return instance;
    }

    /**
     * Create SSLContext that supports TLS 1.2 on Android API 18
     * Uses custom TLSSocketFactory to enable TLS 1.2 on all sockets
     */
    private SSLContext getTLS12Context() {
        try {
            // Create custom TLSSocketFactory that enables TLS 1.2
            TLSSocketFactory tlsSocketFactory = new TLSSocketFactory();

            // Create SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);

            // Use reflection to replace the socket factory in the SSLContext
            // This is a workaround because SSLContext.getSocketFactory() is final
            try {
                Field factoryField = sslContext.getClass().getDeclaredField("socketFactory");
                factoryField.setAccessible(true);
                factoryField.set(sslContext, tlsSocketFactory);
                Log.d(TAG, "Successfully injected TLSSocketFactory into SSLContext");
            } catch (Exception e) {
                Log.w(TAG, "Could not inject TLSSocketFactory via reflection: " + e.getMessage());
                // Reflection failed, but we'll still return the context
                // It might work anyway depending on Smack's implementation
            }

            return sslContext;

        } catch (Exception e) {
            Log.e(TAG, "Failed to create TLS context with custom socket factory", e);
            return null;
        }
    }

    /**
     * Connect to XMPP server
     */
    public void connect(final String server, final int port, final String domain,
                       final ConnectionCallback callback) {
        this.connectionCallback = callback;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Connecting to " + server + ":" + port);

                    XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder()
                            .setServiceName(domain)
                            .setHost(server)
                            .setPort(port)
                            .setSecurityMode(ConnectionConfiguration.SecurityMode.required) // Use STARTTLS via proxy
                            .setCompressionEnabled(false) // Disable compression for BB10 compatibility
                            .setConnectTimeout(30000); // 30 second timeout

                    // Port 5222 connects to custom Node.js STARTTLS proxy that:
                    // - Accepts plaintext XMPP initially
                    // - Intercepts STARTTLS and upgrades to TLS 1.0+ (any version BB10 supports)
                    // - Forwards decrypted traffic to Prosody on localhost:5200
                    Log.d(TAG, "Connecting via STARTTLS proxy on port " + port);

                    XMPPTCPConnectionConfiguration config = configBuilder.build();

                    Log.d(TAG, "Connection config: server=" + server + ", port=" + port + ", domain=" + domain + ", timeout=30s, TLS=1.2");

                    connection = new XMPPTCPConnection(config);

                    // Setup reconnection
                    ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(connection);
                    reconnectionManager.enableAutomaticReconnection();

                    // Add connection listener
                    connection.addConnectionListener(new ConnectionListener() {
                        @Override
                        public void connected(XMPPConnection connection) {
                            Log.d(TAG, "Connected to XMPP server");
                        }

                        @Override
                        public void authenticated(XMPPConnection connection, boolean resumed) {
                            Log.d(TAG, "Authenticated");
                            setupChatManager();
                        }

                        @Override
                        public void connectionClosed() {
                            Log.d(TAG, "Connection closed");
                            notifyDisconnected();
                        }

                        @Override
                        public void connectionClosedOnError(Exception e) {
                            Log.e(TAG, "Connection closed on error", e);
                            notifyConnectionError(e.getMessage());
                        }

                        @Override
                        public void reconnectionSuccessful() {
                            Log.d(TAG, "Reconnection successful");
                        }

                        @Override
                        public void reconnectingIn(int seconds) {
                            Log.d(TAG, "Reconnecting in " + seconds + " seconds");
                        }

                        @Override
                        public void reconnectionFailed(Exception e) {
                            Log.e(TAG, "Reconnection failed", e);
                        }
                    });

                    Log.d(TAG, "Attempting to connect...");
                    connection.connect();
                    Log.d(TAG, "Connection established successfully!");
                    notifyConnected();

                } catch (SmackException e) {
                    Log.e(TAG, "Smack connection error: " + e.getClass().getSimpleName(), e);
                    notifyConnectionError("Connection failed: " + e.getMessage());
                } catch (IOException e) {
                    Log.e(TAG, "IO error during connection", e);
                    notifyConnectionError("Network error: " + e.getMessage());
                } catch (XMPPException e) {
                    Log.e(TAG, "XMPP error during connection", e);
                    notifyConnectionError("XMPP error: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected connection error: " + e.getClass().getSimpleName(), e);
                    notifyConnectionError("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Login with existing credentials
     */
    public void login(final String username, final String password,
                     final ConnectionCallback callback) {
        this.connectionCallback = callback;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null || !connection.isConnected()) {
                        notifyConnectionError("Not connected to server");
                        return;
                    }

                    Log.d(TAG, "Logging in as " + username);
                    connection.login(username, password);
                    notifyAuthenticated();

                } catch (Exception e) {
                    Log.e(TAG, "Login error", e);
                    notifyConnectionError("Login failed: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Register new account
     */
    public void register(final String username, final String password,
                        final ConnectionCallback callback) {
        this.connectionCallback = callback;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null || !connection.isConnected()) {
                        notifyConnectionError("Not connected to server");
                        return;
                    }

                    Log.d(TAG, "Registering account " + username);
                    org.jivesoftware.smackx.iqregister.AccountManager accountManager =
                        org.jivesoftware.smackx.iqregister.AccountManager.getInstance(connection);

                    accountManager.sensitiveOperationOverInsecureConnection(true);
                    accountManager.createAccount(username, password);

                    // Auto login after registration
                    connection.login(username, password);
                    notifyAuthenticated();

                } catch (Exception e) {
                    Log.e(TAG, "Registration error", e);
                    notifyConnectionError("Registration failed: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Setup chat manager for receiving messages
     */
    private void setupChatManager() {
        chatManager = ChatManager.getInstanceFor(connection);

        // Enable PingManager to keep connection alive
        PingManager pingManager = PingManager.getInstanceFor(connection);
        pingManager.setPingInterval(30); // Ping every 30 seconds
        Log.d(TAG, "PingManager enabled with 30 second interval");

        // Enable ChatStateManager for typing indicators
        chatStateManager = ChatStateManager.getInstance(connection);
        Log.d(TAG, "ChatStateManager enabled for typing indicators");

        // ===== GLOBAL MESSAGE LISTENER =====
        // This catches ALL incoming messages, even before chats are created
        // This is critical for receiving messages from new contacts
        Log.d(TAG, "Setting up GLOBAL message listener...");
        connection.addAsyncStanzaListener(new org.jivesoftware.smack.StanzaListener() {
            @Override
            public void processPacket(org.jivesoftware.smack.packet.Stanza packet) {
                try {
                    if (!(packet instanceof Message)) {
                        return; // Not a message, ignore
                    }

                    Message message = (Message) packet;
                    String from = message.getFrom();
                    String body = message.getBody();

                    // Only process chat messages with body
                    if (message.getType() != Message.Type.chat) {
                        return; // Not a chat message, ignore
                    }

                    Log.d(TAG, "===== GLOBAL LISTENER RECEIVED MESSAGE =====");
                    Log.d(TAG, "From: " + from);
                    Log.d(TAG, "Body: " + body);
                    Log.d(TAG, "Type: " + message.getType());

                    // Only process messages with body text (ignore empty messages)
                    if (body != null && !body.trim().isEmpty()) {
                        notifyMessageReceived(message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in global message listener: " + e.getMessage(), e);
                }
            }
        }, new org.jivesoftware.smack.filter.StanzaTypeFilter(Message.class));
        Log.d(TAG, "GLOBAL message listener installed successfully");

        // ===== MESSAGE RETRACTION LISTENER (XEP-0424) =====
        // Listen for message retraction requests
        Log.d(TAG, "Setting up MESSAGE RETRACTION listener...");
        connection.addAsyncStanzaListener(new org.jivesoftware.smack.StanzaListener() {
            @Override
            public void processPacket(org.jivesoftware.smack.packet.Stanza packet) {
                try {
                    if (!(packet instanceof Message)) {
                        return;
                    }

                    Message message = (Message) packet;

                    // Check for retract extension
                    org.jivesoftware.smack.packet.ExtensionElement retractExt =
                        message.getExtension("urn:xmpp:message-retract:1");

                    if (retractExt != null) {
                        String from = message.getFrom();

                        // Parse the retract extension XML to get the stanza ID
                        String xml = retractExt.toXML().toString();
                        String stanzaId = extractStanzaIdFromRetract(xml);

                        if (stanzaId != null && !stanzaId.isEmpty()) {
                            Log.d(TAG, "===== RECEIVED MESSAGE RETRACTION =====");
                            Log.d(TAG, "From: " + from);
                            Log.d(TAG, "Stanza ID to delete: " + stanzaId);

                            // Delete from database
                            if (databaseHelper != null) {
                                // Find message by stanza ID and delete it
                                DatabaseHelper.MessageRecord messageToDelete = findMessageByStanzaId(stanzaId);
                                if (messageToDelete != null) {
                                    boolean deleted = databaseHelper.deleteMessage(messageToDelete.id);
                                    if (deleted) {
                                        Log.d(TAG, "✅ Message deleted from database");

                                        // Notify callback
                                        if (messageRetractCallback != null) {
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    messageRetractCallback.onMessageRetracted(from, stanzaId);
                                                }
                                            });
                                        }
                                    } else {
                                        Log.w(TAG, "Failed to delete message from database");
                                    }
                                } else {
                                    Log.w(TAG, "Message with stanza ID " + stanzaId + " not found in database");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in retraction listener: " + e.getMessage(), e);
                }
            }
        }, new org.jivesoftware.smack.filter.StanzaTypeFilter(Message.class));
        Log.d(TAG, "MESSAGE RETRACTION listener installed successfully");

        // Listen for ALL incoming messages
        chatManager.addChatListener(new ChatManagerListener() {
            @Override
            public void chatCreated(Chat chat, boolean createdLocally) {
                // Add listener to ALL chats (both created locally and remotely)
                String participant = chat.getParticipant();
                Log.d(TAG, "Chat created with: " + participant + " (local=" + createdLocally + ")");

                // Prevent adding duplicate listeners to the same chat
                if (chatsWithListeners.contains(participant)) {
                    Log.d(TAG, "Chat listener already exists for: " + participant);
                    return;
                }

                chatsWithListeners.add(participant);

                // Add message listener
                chat.addMessageListener(new ChatMessageListener() {
                    @Override
                    public void processMessage(Chat chat, Message message) {
                        Log.d(TAG, "Chat message from " + message.getFrom() + ": " + message.getBody());

                        // Check for chat state extension in message
                        try {
                            org.jivesoftware.smackx.chatstates.packet.ChatStateExtension chatStateExt =
                                (org.jivesoftware.smackx.chatstates.packet.ChatStateExtension)
                                message.getExtension("http://jabber.org/protocol/chatstates");

                            if (chatStateExt != null) {
                                ChatState state = chatStateExt.getChatState();
                                String from = message.getFrom();
                                boolean isTyping = (state == ChatState.composing);
                                Log.d(TAG, "Chat state from " + from + ": " + state + " (typing=" + isTyping + ")");

                                if (typingStateCallback != null) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            typingStateCallback.onTypingStateChanged(from, isTyping);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            // Ignore - not all messages have chat state
                        }

                        // NOTE: Message already handled by global listener above
                        // This listener is now mainly for chat states (typing indicators)
                    }
                });
            }
        });

        // Enable Carbon Copies to receive messages sent from other devices
        setupCarbonCopies();
    }

    /**
     * Setup Carbon Copies (XEP-0280) to receive messages sent from other devices
     */
    private void setupCarbonCopies() {
        try {
            final CarbonManager carbonManager = CarbonManager.getInstanceFor(connection);

            // Enable carbon copies
            carbonManager.enableCarbons();
            Log.d(TAG, "Carbon Copies enabled successfully");

            // Add listener for carbon copy messages
            connection.addAsyncStanzaListener(new org.jivesoftware.smack.StanzaListener() {
                @Override
                public void processPacket(org.jivesoftware.smack.packet.Stanza packet) {
                    try {
                        Message message = (Message) packet;

                        // Get carbon extension
                        CarbonExtension carbonExt = (CarbonExtension) message.getExtension(CarbonExtension.NAMESPACE);
                        if (carbonExt != null) {
                            // Get the forwarded message
                            org.jivesoftware.smackx.forward.packet.Forwarded forwarded = carbonExt.getForwarded();
                            if (forwarded != null && forwarded.getForwardedPacket() instanceof Message) {
                                Message forwardedMsg = (Message) forwarded.getForwardedPacket();

                                // Check if it's a sent carbon (message you sent from another device)
                                if (carbonExt.getDirection() == CarbonExtension.Direction.sent) {
                                    String to = forwardedMsg.getTo();
                                    String body = forwardedMsg.getBody();

                                    if (body != null && !body.isEmpty()) {
                                        Log.d(TAG, "Carbon copy SENT (to " + to + "): " + body);

                                        // Store as a sent message in database with stanza ID
                                        String stanzaId = forwardedMsg.getStanzaId();
                                        storeMessage(to, body, true, null, stanzaId);

                                        // Notify UI if we're in that chat
                                        if (messageCallback != null) {
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    messageCallback.onMessageReceived(to, body, true); // true = sent message
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing carbon copy: " + e.getMessage());
                    }
                }
            }, new org.jivesoftware.smack.filter.StanzaExtensionFilter(CarbonExtension.NAMESPACE));

        } catch (Exception e) {
            Log.e(TAG, "Failed to enable carbon copies: " + e.getMessage(), e);
        }
    }

    /**
     * Send message to contact
     */
    public void sendMessage(String toJid, String messageText, final MessageCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null || !connection.isAuthenticated()) {
                        if (callback != null) {
                            callback.onMessageError("Not connected");
                        }
                        return;
                    }

                    // Create message manually to capture stanza ID
                    Message message = new Message(toJid, Message.Type.chat);
                    message.setBody(messageText);

                    // Get the stanza ID that will be sent
                    String stanzaId = message.getStanzaId();
                    Log.d(TAG, "Sending message with stanza ID: " + stanzaId);

                    // Send the message
                    connection.sendStanza(message);

                    // Store sent message locally with stanza ID
                    storeMessage(toJid, messageText, true, null, stanzaId);

                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMessageSent();
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Send message error", e);
                    if (callback != null) {
                        final String error = e.getMessage();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMessageError(error);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * Send file with OOB (Out of Band Data) - XEP-0066
     * This sends the file URL in a way that XMPP clients (including WhatsApp bridge via Slidge) recognize as attachment
     */
    public void sendFileMessage(String toJid, final String fileUrl, final String fileName, final MessageCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null || !connection.isAuthenticated()) {
                        if (callback != null) {
                            callback.onMessageError("Not connected");
                        }
                        return;
                    }

                    // Create message
                    Message message = new Message(toJid, Message.Type.chat);

                    // Set body to the URL (fallback for non-supporting clients)
                    message.setBody(fileUrl);

                    // Determine MIME type from filename
                    String mimeType = getMimeTypeFromFilename(fileName);

                    // Add OOB extension (XEP-0066) manually via XML
                    // Slidge recognizes this and converts to WhatsApp media
                    String oobExtension = "<x xmlns='jabber:x:oob'><url>" +
                        escapeXml(fileUrl) + "</url>" +
                        (fileName != null ? "<desc>" + escapeXml(fileName) + "</desc>" : "") +
                        "</x>";

                    // For Slidge, we also hint the MIME type if it's media
                    if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                        // Add a hint that this is media content
                        message.setSubject(mimeType);
                    }

                    // Add extensions via packet extension
                    try {
                        // Create custom packet extension for OOB
                        org.jivesoftware.smack.packet.PacketExtension oobExt = new org.jivesoftware.smack.packet.PacketExtension() {
                            @Override
                            public String getElementName() {
                                return "x";
                            }

                            @Override
                            public String getNamespace() {
                                return "jabber:x:oob";
                            }

                            @Override
                            public CharSequence toXML() {
                                StringBuilder xml = new StringBuilder();
                                xml.append("<x xmlns='jabber:x:oob'>");
                                xml.append("<url>").append(escapeXml(fileUrl)).append("</url>");
                                if (fileName != null && !fileName.isEmpty()) {
                                    xml.append("<desc>").append(escapeXml(fileName)).append("</desc>");
                                }
                                xml.append("</x>");
                                return xml.toString();
                            }
                        };

                        message.addExtension(oobExt);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not add OOB extension", e);
                    }

                    // Get the stanza ID before sending
                    String stanzaId = message.getStanzaId();
                    Log.d(TAG, "Sending file message with stanza ID: " + stanzaId);

                    // Send the message
                    connection.sendStanza(message);

                    Log.d(TAG, "Sent file message: " + fileUrl + " (type: " + mimeType + ")");

                    // Store sent message locally with file flag and stanza ID
                    storeFileMessage(toJid, fileUrl, fileName, true, stanzaId);

                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMessageSent();
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Send file message error", e);
                    if (callback != null) {
                        final String error = e.getMessage();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMessageError(error);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * Retract (delete) a message using XEP-0424: Message Retraction
     * @param toJid The JID of the contact
     * @param stanzaId The stanza ID of the message to retract
     * @param callback Callback for success/failure
     */
    public void retractMessage(String toJid, final String stanzaId, final MessageCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null || !connection.isAuthenticated()) {
                        if (callback != null) {
                            callback.onMessageError("Not connected");
                        }
                        return;
                    }

                    if (stanzaId == null || stanzaId.isEmpty()) {
                        if (callback != null) {
                            callback.onMessageError("No stanza ID available");
                        }
                        return;
                    }

                    // Create retraction message according to XEP-0424
                    Message message = new Message(toJid, Message.Type.chat);

                    // Fallback text for clients that don't support retraction
                    message.setBody("This message was deleted");

                    // Create retract extension
                    org.jivesoftware.smack.packet.PacketExtension retractExt = new org.jivesoftware.smack.packet.PacketExtension() {
                        @Override
                        public String getElementName() {
                            return "retract";
                        }

                        @Override
                        public String getNamespace() {
                            return "urn:xmpp:message-retract:1";
                        }

                        @Override
                        public CharSequence toXML() {
                            return "<retract id='" + escapeXml(stanzaId) + "' xmlns='urn:xmpp:message-retract:1'/>";
                        }
                    };

                    // Create fallback extension
                    org.jivesoftware.smack.packet.PacketExtension fallbackExt = new org.jivesoftware.smack.packet.PacketExtension() {
                        @Override
                        public String getElementName() {
                            return "fallback";
                        }

                        @Override
                        public String getNamespace() {
                            return "urn:xmpp:fallback:0";
                        }

                        @Override
                        public CharSequence toXML() {
                            return "<fallback xmlns='urn:xmpp:fallback:0' for='urn:xmpp:message-retract:1'/>";
                        }
                    };

                    // Create store hint extension (to ensure archiving)
                    org.jivesoftware.smack.packet.PacketExtension storeExt = new org.jivesoftware.smack.packet.PacketExtension() {
                        @Override
                        public String getElementName() {
                            return "store";
                        }

                        @Override
                        public String getNamespace() {
                            return "urn:xmpp:hints";
                        }

                        @Override
                        public CharSequence toXML() {
                            return "<store xmlns='urn:xmpp:hints'/>";
                        }
                    };

                    // Add all extensions
                    message.addExtension(retractExt);
                    message.addExtension(fallbackExt);
                    message.addExtension(storeExt);

                    // Send the retraction message
                    connection.sendStanza(message);

                    Log.d(TAG, "Sent message retraction for stanza ID: " + stanzaId);

                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMessageSent();
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Retract message error", e);
                    if (callback != null) {
                        final String error = e.getMessage();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMessageError(error);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private String getMimeTypeFromFilename(String filename) {
        if (filename == null) return "application/octet-stream";

        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".3gp")) return "video/3gpp";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".ogg") || lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".m4a")) return "audio/mp4";

        return "application/octet-stream";
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Extract stanza ID from retract extension XML
     * Example: <retract id='message-id-123' xmlns='urn:xmpp:message-retract:1'/>
     */
    private String extractStanzaIdFromRetract(String xml) {
        try {
            // Simple XML parsing to extract id attribute
            int idStart = xml.indexOf("id='");
            if (idStart == -1) {
                idStart = xml.indexOf("id=\"");
            }
            if (idStart != -1) {
                idStart += 4; // Skip "id='"
                int idEnd = xml.indexOf("'", idStart);
                if (idEnd == -1) {
                    idEnd = xml.indexOf("\"", idStart);
                }
                if (idEnd != -1) {
                    return xml.substring(idStart, idEnd);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting stanza ID from retract XML", e);
        }
        return null;
    }

    /**
     * Find a message in the database by its stanza ID
     */
    private DatabaseHelper.MessageRecord findMessageByStanzaId(String stanzaId) {
        if (databaseHelper == null || stanzaId == null) {
            return null;
        }

        try {
            // Search through all messages to find one with matching stanza ID
            // This is not optimal but works for now
            // TODO: Add database method to search by stanza_id directly
            List<String> contacts = getAllContactJids();
            for (String contactJid : contacts) {
                List<DatabaseHelper.MessageRecord> messages =
                    databaseHelper.getMessagesForContact(contactJid, 0); // All messages
                for (DatabaseHelper.MessageRecord msg : messages) {
                    if (stanzaId.equals(msg.stanzaId)) {
                        return msg;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding message by stanza ID", e);
        }
        return null;
    }

    /**
     * Get all contact JIDs from roster and recent messages
     */
    private List<String> getAllContactJids() {
        List<String> jids = new ArrayList<>();
        try {
            if (connection != null && connection.isAuthenticated()) {
                Roster roster = Roster.getInstanceFor(connection);
                Collection<RosterEntry> entries = roster.getEntries();
                for (RosterEntry entry : entries) {
                    jids.add(entry.getUser());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting contact JIDs", e);
        }
        return jids;
    }

    private void storeFileMessage(String contactJid, String fileUrl, String fileName, boolean isSent) {
        storeFileMessage(contactJid, fileUrl, fileName, isSent, null);
    }

    private void storeFileMessage(String contactJid, String fileUrl, String fileName, boolean isSent, String stanzaId) {
        if (databaseHelper == null) {
            Log.w(TAG, "Database not initialized, message not stored");
            return;
        }

        // Normalize JID
        if (contactJid.contains("/")) {
            contactJid = contactJid.substring(0, contactJid.indexOf("/"));
        }

        // Store with file URL info so it can be displayed correctly
        String messageBody = fileName != null ? fileName : fileUrl;
        long timestamp = System.currentTimeMillis();

        Log.d(TAG, "💾 Storing file message - Contact: " + contactJid + ", isSent: " + isSent +
                   ", file: " + fileName + (stanzaId != null ? ", stanzaId: " + stanzaId : ""));

        databaseHelper.insertMessage(contactJid, messageBody, isSent, timestamp, fileUrl, stanzaId);
    }

    /**
     * Get roster (contact list)
     */
    public List<Contact> getContacts() {
        List<Contact> contacts = new ArrayList<>();

        try {
            if (connection == null || !connection.isAuthenticated()) {
                return contacts;
            }

            Roster roster = Roster.getInstanceFor(connection);
            Collection<RosterEntry> entries = roster.getEntries();

            for (RosterEntry entry : entries) {
                Contact contact = new Contact();
                contact.jid = entry.getUser();
                contact.name = entry.getName() != null ? entry.getName() : entry.getUser();

                Presence presence = roster.getPresence(entry.getUser());
                contact.isOnline = presence.isAvailable();
                contact.status = presence.getStatus();

                contacts.add(contact);
            }

        } catch (Exception e) {
            Log.e(TAG, "Get contacts error", e);
        }

        return contacts;
    }

    /**
     * Set message callback
     */
    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    /**
     * Set typing state callback to receive typing notifications
     */
    public void setTypingStateCallback(TypingStateCallback callback) {
        this.typingStateCallback = callback;
    }

    public void setMessageRetractCallback(MessageRetractCallback callback) {
        this.messageRetractCallback = callback;
    }

    /**
     * Send typing state (composing or paused)
     */
    public void sendTypingState(String toJid, boolean isTyping) {
        if (connection == null || !connection.isAuthenticated() || chatStateManager == null) {
            return;
        }

        try {
            Chat chat = chatManager.createChat(toJid);
            ChatState state = isTyping ? ChatState.composing : ChatState.paused;

            // Create message with chat state
            Message message = new Message();
            message.setTo(toJid);
            message.addExtension(new org.jivesoftware.smackx.chatstates.packet.ChatStateExtension(state));

            connection.sendStanza(message);
            Log.d(TAG, "Sent typing state to " + toJid + ": " + state);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send typing state", e);
        }
    }

    /**
     * Load message history for a contact from database
     */
    public void loadMessageHistory(final String contactJid, final int maxMessages,
                                   final MessageHistoryCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (databaseHelper == null) {
                        notifyHistoryError(callback, "Database not initialized");
                        return;
                    }

                    Log.d(TAG, "Loading message history for: " + contactJid);

                    // Normalize JID
                    String normalizedJid = contactJid;
                    if (normalizedJid.contains("/")) {
                        normalizedJid = normalizedJid.substring(0, normalizedJid.indexOf("/"));
                    }

                    // Load from database
                    List<DatabaseHelper.MessageRecord> records =
                        databaseHelper.getMessagesForContact(normalizedJid, maxMessages);

                    // Convert to HistoricalMessage
                    final List<HistoricalMessage> historyMessages = new ArrayList<>();
                    for (DatabaseHelper.MessageRecord record : records) {
                        HistoricalMessage msg = new HistoricalMessage();
                        msg.databaseId = record.id; // Store database ID for edit/delete operations
                        msg.from = record.contactJid;
                        msg.body = record.body;
                        msg.isSent = record.isSent;
                        msg.timestamp = record.timestamp;
                        msg.fileUrl = record.fileUrl; // Copy file URL if present
                        msg.stanzaId = record.stanzaId; // Copy stanza ID for retraction
                        historyMessages.add(msg);
                    }

                    Log.d(TAG, "Loaded " + historyMessages.size() + " messages from database");
                    notifyHistoryLoaded(callback, historyMessages);

                } catch (Exception e) {
                    Log.e(TAG, "Load message history error", e);
                    notifyHistoryError(callback, "Failed to load history: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Get database location (on external SD card)
     */
    public String getDatabaseLocation() {
        if (databaseHelper != null) {
            return databaseHelper.getDatabaseLocation();
        }
        return "Database not initialized";
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
    }

    /**
     * Check if connected and authenticated
     */
    public boolean isAuthenticated() {
        return connection != null && connection.isAuthenticated();
    }

    /**
     * Get current connection
     */
    public AbstractXMPPConnection getConnection() {
        return connection;
    }

    /**
     * Get database helper for message operations
     */
    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    // Notification helpers
    private void notifyConnected() {
        if (connectionCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    connectionCallback.onConnected();
                }
            });
        }
    }

    private void notifyAuthenticated() {
        if (connectionCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    connectionCallback.onAuthenticated();
                }
            });
        }
    }

    private void notifyDisconnected() {
        if (connectionCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    connectionCallback.onDisconnected();
                }
            });
        }
    }

    private void notifyConnectionError(final String error) {
        if (connectionCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    connectionCallback.onError(error);
                }
            });
        }
    }

    private void notifyMessageReceived(final Message message) {
        // Validate message has body
        if (message.getBody() == null || message.getBody().isEmpty()) {
            Log.w(TAG, "Received message with empty body from: " + message.getFrom());
            return;
        }

        final String body = message.getBody();
        final String from = message.getFrom();

        // Create unique message ID based on sender and content
        final String messageId = from + "|" + body + "|" + (System.currentTimeMillis() / 2000); // 2 second window

        // Check if we've already processed this message recently
        synchronized (recentMessages) {
            if (recentMessages.contains(messageId)) {
                Log.d(TAG, "Duplicate message detected, skipping: " + messageId);
                return;
            }
            recentMessages.add(messageId);
        }

        // Clean up old message IDs after 10 seconds
        cleanupHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (recentMessages) {
                    recentMessages.remove(messageId);
                }
            }
        }, 10000);

        // Store received message in database with stanza ID
        String stanzaId = message.getStanzaId();
        Log.d(TAG, "Received message with stanza ID: " + stanzaId);
        storeMessage(from, body, false, null, stanzaId);

        // Check if message contains a file URL (simple detection)
        // URLs from our upload service or common media URLs
        final boolean isFileUrl = body.startsWith("http://") || body.startsWith("https://");
        final boolean isMediaFile = isFileUrl && (
            body.contains("/attachments/") ||
            body.endsWith(".jpg") || body.endsWith(".jpeg") || body.endsWith(".png") ||
            body.endsWith(".gif") || body.endsWith(".mp4") || body.endsWith(".3gp") ||
            body.endsWith(".mp3") || body.endsWith(".ogg") || body.endsWith(".m4a")
        );

        if (messageCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    messageCallback.onMessageReceived(message.getFrom(), body, false); // false = received message
                }
            });
        }
    }

    private void storeMessage(String contactJid, String body, boolean isSent) {
        storeMessage(contactJid, body, isSent, null, null);
    }

    private void storeMessage(String contactJid, String body, boolean isSent, String fileUrl, String stanzaId) {
        if (databaseHelper == null) {
            Log.e(TAG, "❌ Database not initialized, message NOT stored!");
            return;
        }

        // Normalize JID (remove resource)
        String originalJid = contactJid;
        if (contactJid.contains("/")) {
            contactJid = contactJid.substring(0, contactJid.indexOf("/"));
        }

        long timestamp = System.currentTimeMillis();

        Log.d(TAG, "💾 Storing message - Contact: " + contactJid + " (original: " + originalJid + "), isSent: " + isSent +
                   ", body length: " + body.length() + (stanzaId != null ? ", stanzaId: " + stanzaId : ""));

        // Store in database
        try {
            databaseHelper.insertMessage(contactJid, body, isSent, timestamp, fileUrl, stanzaId);
            Log.d(TAG, "✅ Message stored successfully in database");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to store message in database", e);
        }

        // NO LIMITS: Store all messages forever (no cleanup)
    }

    private void notifyHistoryLoaded(final MessageHistoryCallback callback,
                                    final List<HistoricalMessage> messages) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onHistoryLoaded(messages);
                }
            });
        }
    }

    private void notifyHistoryError(final MessageHistoryCallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onHistoryError(error);
                }
            });
        }
    }

    // Callback interfaces
    public interface ConnectionCallback {
        void onConnected();
        void onAuthenticated();
        void onDisconnected();
        void onError(String error);
    }

    public interface MessageCallback {
        void onMessageReceived(String from, String message, boolean isSent);
        void onMessageSent();
        void onMessageError(String error);
    }

    public interface MessageHistoryCallback {
        void onHistoryLoaded(List<HistoricalMessage> messages);
        void onHistoryError(String error);
    }

    public interface MessageRetractCallback {
        void onMessageRetracted(String from, String stanzaId);
    }

    public interface TypingStateCallback {
        void onTypingStateChanged(String from, boolean isTyping);
    }

    // Contact model
    public static class Contact {
        public String jid;
        public String name;
        public boolean isOnline;
        public String status;
    }

    // Historical message model
    public static class HistoricalMessage {
        public long databaseId; // Database ID for message operations (edit/delete)
        public String from;
        public String body;
        public boolean isSent;
        public long timestamp;
        public String fileUrl; // URL for multimedia files, null for text-only
        public String stanzaId; // XMPP stanza ID for retraction
    }
}
