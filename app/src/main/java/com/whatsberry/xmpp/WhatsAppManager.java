package com.whatsberry.xmpp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.commands.AdHocCommand;
import org.jivesoftware.smackx.commands.AdHocCommandManager;
import org.jivesoftware.smackx.commands.RemoteCommand;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;

import java.util.List;

/**
 * WhatsApp Manager
 * Handles WhatsApp-specific operations via slidge-whatsapp ad-hoc commands
 */
public class WhatsAppManager {
    private static final String TAG = "WhatsAppManager";

    private static WhatsAppManager instance;
    private XMPPManager xmppManager;
    private Handler mainHandler;
    private String gatewayJid = "whatsapp.localhost"; // Default gateway

    private WhatsAppManager() {
        mainHandler = new Handler(Looper.getMainLooper());
        xmppManager = XMPPManager.getInstance();
    }

    public static synchronized WhatsAppManager getInstance() {
        if (instance == null) {
            instance = new WhatsAppManager();
        }
        return instance;
    }

    /**
     * Set gateway JID
     */
    public void setGatewayJid(String jid) {
        this.gatewayJid = jid;
    }

    /**
     * Simple registration via text message (alternative to Ad-Hoc)
     * Based on slidge documentation: send "register" to gateway JID
     */
    public void registerViaMessage(final RegistrationCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Registering via message to: " + gatewayJid);

                    // Send "register" message
                    xmppManager.sendMessage(gatewayJid, "register", new XMPPManager.MessageCallback() {
                        @Override
                        public void onMessageReceived(String from, String message, boolean isSent) {
                            Log.d(TAG, "Response from gateway: " + message);
                        }

                        @Override
                        public void onMessageSent() {
                            Log.d(TAG, "Register message sent");
                        }

                        @Override
                        public void onMessageError(String error) {
                            Log.e(TAG, "Failed to send register message: " + error);
                        }
                    });

                    // Wait for response (slidge should auto-respond)
                    Thread.sleep(3000);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onRegistrationSuccess();
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Registration via message failed", e);
                    final String error = e.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onRegistrationError(error);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Get QR code via text message (alternative to Ad-Hoc)
     * Based on slidge documentation: send "pair" to gateway JID
     */
    public void getQRViaMessage(final QRCodeCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Requesting QR via message to: " + gatewayJid);

                    // Send "pair" message
                    xmppManager.sendMessage(gatewayJid, "pair", new XMPPManager.MessageCallback() {
                        @Override
                        public void onMessageReceived(String from, String message, boolean isSent) {
                            Log.d(TAG, "Response from gateway: " + message);
                        }

                        @Override
                        public void onMessageSent() {
                            Log.d(TAG, "Pair message sent");
                        }

                        @Override
                        public void onMessageError(String error) {
                            Log.e(TAG, "Failed to send pair message: " + error);
                        }
                    });

                    // Wait for response with QR code
                    // The QR should come in the next message from gateway
                    Thread.sleep(5000);

                    // For now, notify that message was sent
                    // Real QR will come via message listener
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onQRCodeReceived("Check messages from " + gatewayJid);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "QR request via message failed", e);
                    final String error = e.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onQRCodeError(error);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Register with WhatsApp gateway
     */
    public void registerWithGateway(final RegistrationCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!xmppManager.isAuthenticated()) {
                        notifyError(callback, "Not authenticated to XMPP server");
                        return;
                    }

                    Log.d(TAG, "Registering with WhatsApp gateway: " + gatewayJid);

                    // Execute Register command
                    AdHocCommandManager manager = AdHocCommandManager.getAddHocCommandsManager(
                            xmppManager.getConnection());

                    // Discover available commands
                    DiscoverItems items = manager.discoverCommands(gatewayJid);

                    RemoteCommand registerCommand = null;
                    for (DiscoverItems.Item item : items.getItems()) {
                        Log.d(TAG, "Available command: " + item.getNode() + " - " + item.getName());
                        String node = item.getNode();
                        String name = item.getName().toLowerCase();

                        // Look for register command (jabber:iq:register or contains "register")
                        if (node.equals("jabber:iq:register") || node.contains("register") || name.contains("register")) {
                            registerCommand = manager.getRemoteCommand(gatewayJid, node);
                            Log.d(TAG, "Found register command: " + node);
                            break;
                        }
                    }

                    if (registerCommand == null) {
                        notifyError(callback, "Register command not found");
                        return;
                    }

                    // Execute register command
                    registerCommand.execute();

                    // Check if form needs to be completed
                    Form form = registerCommand.getForm();
                    if (form != null && registerCommand.getStatus() == AdHocCommand.Status.executing) {
                        Log.d(TAG, "Registration returned a form, completing it...");

                        // Submit the form with default values to complete registration
                        Form answerForm = form.createAnswerForm();

                        // Complete the command with the answered form
                        registerCommand.complete(answerForm);

                        // After completing, check if there's ANOTHER form (Preferences)
                        Thread.sleep(500);
                        form = registerCommand.getForm();
                        if (form != null && "Preferences".equals(form.getTitle())) {
                            Log.d(TAG, "Received Preferences form, completing it with defaults...");

                            // Create answer form with default values
                            answerForm = form.createAnswerForm();

                            // Complete the Preferences form
                            registerCommand.complete(answerForm);
                        }
                    }

                    // Wait a bit for registration to complete on server
                    Thread.sleep(1000);

                    notifyRegistrationSuccess(callback);

                } catch (Exception e) {
                    Log.e(TAG, "Registration error", e);
                    notifyError(callback, "Registration failed: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Get QR code for pairing WhatsApp
     */
    public void getQRCode(final QRCodeCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!xmppManager.isAuthenticated()) {
                        notifyQRError(callback, "Not authenticated to XMPP server");
                        return;
                    }

                    Log.d(TAG, "Requesting QR code from gateway");

                    AdHocCommandManager manager = AdHocCommandManager.getAddHocCommandsManager(
                            xmppManager.getConnection());

                    // Discover commands
                    DiscoverItems items = manager.discoverCommands(gatewayJid);

                    RemoteCommand pairCommand = null;
                    for (DiscoverItems.Item item : items.getItems()) {
                        Log.d(TAG, "Command: " + item.getNode() + " - " + item.getName());
                        String node = item.getNode();
                        String name = item.getName().toLowerCase();

                        // Look for pair command (wa_pair_phone or contains "pair")
                        if (node.equals("wa_pair_phone") || node.contains("pair") || name.contains("pair")) {
                            pairCommand = manager.getRemoteCommand(gatewayJid, node);
                            Log.d(TAG, "Found pair command: " + node);
                            break;
                        }
                    }

                    if (pairCommand == null) {
                        notifyQRError(callback, "PairPhone command not found");
                        return;
                    }

                    // Execute pair command
                    pairCommand.execute();

                    // Get form with QR code
                    Form form = pairCommand.getForm();
                    if (form != null) {
                        // Look for QR code field
                        String qrCodeData = null;
                        for (FormField field : form.getFields()) {
                            Log.d(TAG, "Field: " + field.getVariable() + " = " + field.getType());

                            // QR code might be in different fields depending on slidge version
                            if (field.getVariable().contains("qr") ||
                                field.getVariable().contains("code") ||
                                field.getType().equals("text-single")) {

                                List<String> values = field.getValues();
                                if (values != null && !values.isEmpty()) {
                                    qrCodeData = values.get(0);
                                    Log.d(TAG, "Found QR data: " + qrCodeData.substring(0, Math.min(50, qrCodeData.length())));
                                    break;
                                }
                            }
                        }

                        if (qrCodeData != null) {
                            final String finalQrData = qrCodeData;
                            notifyQRReceived(callback, finalQrData);
                        } else {
                            notifyQRError(callback, "QR code not found in response");
                        }
                    } else {
                        notifyQRError(callback, "No form received from gateway");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "QR code error", e);
                    notifyQRError(callback, "Failed to get QR code: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Login to WhatsApp (after QR scan)
     */
    public void loginWhatsApp(final LoginCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!xmppManager.isAuthenticated()) {
                        notifyLoginError(callback, "Not authenticated to XMPP server");
                        return;
                    }

                    Log.d(TAG, "Logging into WhatsApp");

                    AdHocCommandManager manager = AdHocCommandManager.getAddHocCommandsManager(
                            xmppManager.getConnection());

                    DiscoverItems items = manager.discoverCommands(gatewayJid);

                    RemoteCommand loginCommand = null;
                    for (DiscoverItems.Item item : items.getItems()) {
                        if (item.getNode().contains("login") || item.getName().toLowerCase().contains("login")) {
                            loginCommand = manager.getRemoteCommand(gatewayJid, item.getNode());
                            break;
                        }
                    }

                    if (loginCommand == null) {
                        notifyLoginError(callback, "Login command not found");
                        return;
                    }

                    loginCommand.execute();
                    notifyLoginSuccess(callback);

                } catch (Exception e) {
                    Log.e(TAG, "WhatsApp login error", e);
                    notifyLoginError(callback, "Login failed: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Logout from WhatsApp
     */
    public void logoutWhatsApp(final LogoutCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!xmppManager.isAuthenticated()) {
                        notifyLogoutError(callback, "Not authenticated");
                        return;
                    }

                    AdHocCommandManager manager = AdHocCommandManager.getAddHocCommandsManager(
                            xmppManager.getConnection());

                    DiscoverItems items = manager.discoverCommands(gatewayJid);

                    RemoteCommand logoutCommand = null;
                    for (DiscoverItems.Item item : items.getItems()) {
                        if (item.getNode().contains("logout") || item.getName().toLowerCase().contains("logout")) {
                            logoutCommand = manager.getRemoteCommand(gatewayJid, item.getNode());
                            break;
                        }
                    }

                    if (logoutCommand != null) {
                        logoutCommand.execute();
                    }

                    notifyLogoutSuccess(callback);

                } catch (Exception e) {
                    Log.e(TAG, "Logout error", e);
                    notifyLogoutError(callback, "Logout failed: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Discover available ad-hoc commands
     */
    public void discoverCommands(final CommandsCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!xmppManager.isAuthenticated()) {
                        return;
                    }

                    AdHocCommandManager manager = AdHocCommandManager.getAddHocCommandsManager(
                            xmppManager.getConnection());

                    DiscoverItems items = manager.discoverCommands(gatewayJid);

                    final StringBuilder commands = new StringBuilder();
                    for (DiscoverItems.Item item : items.getItems()) {
                        commands.append(item.getName()).append(" (").append(item.getNode()).append(")\n");
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onCommandsDiscovered(commands.toString());
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Discover commands error", e);
                }
            }
        }).start();
    }

    // Notification helpers
    private void notifyRegistrationSuccess(final RegistrationCallback callback) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRegistrationSuccess();
                }
            });
        }
    }

    private void notifyError(final RegistrationCallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRegistrationError(error);
                }
            });
        }
    }

    private void notifyQRReceived(final QRCodeCallback callback, final String qrData) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onQRCodeReceived(qrData);
                }
            });
        }
    }

    private void notifyQRError(final QRCodeCallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onQRCodeError(error);
                }
            });
        }
    }

    private void notifyLoginSuccess(final LoginCallback callback) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onLoginSuccess();
                }
            });
        }
    }

    private void notifyLoginError(final LoginCallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onLoginError(error);
                }
            });
        }
    }

    private void notifyLogoutSuccess(final LogoutCallback callback) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onLogoutSuccess();
                }
            });
        }
    }

    private void notifyLogoutError(final LogoutCallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onLogoutError(error);
                }
            });
        }
    }

    // Callback interfaces
    public interface RegistrationCallback {
        void onRegistrationSuccess();
        void onRegistrationError(String error);
    }

    public interface QRCodeCallback {
        void onQRCodeReceived(String qrData);
        void onQRCodeError(String error);
    }

    public interface LoginCallback {
        void onLoginSuccess();
        void onLoginError(String error);
    }

    public interface LogoutCallback {
        void onLogoutSuccess();
        void onLogoutError(String error);
    }

    public interface CommandsCallback {
        void onCommandsDiscovered(String commands);
    }
}
