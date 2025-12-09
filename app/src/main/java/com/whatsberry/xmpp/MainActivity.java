package com.whatsberry.xmpp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Main Activity - Login Screen
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "WhatsberryPrefs";
    private static final String PREF_SERVER = "server";
    private static final String PREF_PORT = "port";
    private static final String PREF_DOMAIN = "domain";
    private static final String PREF_USERNAME = "username";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_GATEWAY = "gateway";

    private EditText etServer, etPort, etDomain, etUsername, etPassword, etGateway;
    private Button btnConnect, btnRegister, btnDatabaseSettings;
    private ProgressDialog progressDialog;

    private XMPPManager xmppManager;
    private WhatsAppManager whatsAppManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        xmppManager = XMPPManager.getInstance();
        xmppManager.initialize(this);  // Initialize with context for database

        whatsAppManager = WhatsAppManager.getInstance();

        // Initialize emoji system with Twemoji assets
        EmojiData.initializeEmojis(this);

        // Start XMPP service as FOREGROUND SERVICE (critical for BB10)
        // This prevents BB10 from killing the process and ensures notifications work
        Intent serviceIntent = new Intent(this, XMPPService.class);
        startService(serviceIntent);
        Log.d(TAG, "✅ XMPPService started (will run as foreground service)");

        initializeViews();
        loadSavedSettings();
        setupListeners();

        // Auto-login and skip to MainTabsActivity if already configured
        autoLoginIfConfigured();
    }

    private void autoLoginIfConfigured() {
        // Check if we have saved credentials
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUsername = prefs.getString(PREF_USERNAME, "");
        String savedPassword = prefs.getString(PREF_PASSWORD, "");
        String savedServer = prefs.getString(PREF_SERVER, "");

        if (!savedUsername.isEmpty() && !savedPassword.isEmpty() && !savedServer.isEmpty()) {
            // Wait a bit for the service to authenticate, then check
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (xmppManager.isAuthenticated()) {
                        // Already authenticated, go to main tabs
                        Intent intent = new Intent(MainActivity.this, MainTabsActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // Not authenticated yet, check again in 2 seconds
                        checkAuthenticationStatus();
                    }
                }
            }, 2000);
        }
    }

    private void checkAuthenticationStatus() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (xmppManager.isAuthenticated()) {
                    Intent intent = new Intent(MainActivity.this, MainTabsActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    // Keep checking every 2 seconds
                    checkAuthenticationStatus();
                }
            }
        }, 2000);
    }

    private void initializeViews() {
        etServer = (EditText) findViewById(R.id.etServer);
        etPort = (EditText) findViewById(R.id.etPort);
        etDomain = (EditText) findViewById(R.id.etDomain);
        etUsername = (EditText) findViewById(R.id.etUsername);
        etPassword = (EditText) findViewById(R.id.etPassword);
        etGateway = (EditText) findViewById(R.id.etGateway);
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnRegister = (Button) findViewById(R.id.btnRegister);
        btnDatabaseSettings = (Button) findViewById(R.id.btnDatabaseSettings);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Load ALL settings from SharedPreferences
        etServer.setText(prefs.getString(PREF_SERVER, ""));
        etPort.setText(prefs.getString(PREF_PORT, "5222"));
        etDomain.setText(prefs.getString(PREF_DOMAIN, ""));
        etUsername.setText(prefs.getString(PREF_USERNAME, ""));
        etPassword.setText(prefs.getString(PREF_PASSWORD, ""));
        etGateway.setText(prefs.getString(PREF_GATEWAY, "whatsapp.localhost"));
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        // Save ALL settings for persistence
        editor.putString(PREF_SERVER, etServer.getText().toString());
        editor.putString(PREF_PORT, etPort.getText().toString());
        editor.putString(PREF_DOMAIN, etDomain.getText().toString());
        editor.putString(PREF_USERNAME, etUsername.getText().toString());
        editor.putString(PREF_PASSWORD, etPassword.getText().toString());
        editor.putString(PREF_GATEWAY, etGateway.getText().toString());
        editor.apply();
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectAndLogin();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectAndRegister();
            }
        });

        btnDatabaseSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatabaseSettings();
            }
        });
    }

    private void connectAndLogin() {
        final String server = etServer.getText().toString().trim();
        final String portStr = etPort.getText().toString().trim();
        final String domain = etDomain.getText().toString().trim();
        final String username = etUsername.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        final String gateway = etGateway.getText().toString().trim();

        if (server.isEmpty() || portStr.isEmpty() || domain.isEmpty() ||
            username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
            return;
        }

        saveSettings();
        whatsAppManager.setGatewayJid(gateway);

        progressDialog.setMessage("Connecting to XMPP server...");
        progressDialog.show();

        xmppManager.connect(server, port, domain, new XMPPManager.ConnectionCallback() {
            @Override
            public void onConnected() {
                progressDialog.setMessage("Logging in...");
                xmppManager.login(username, password, new XMPPManager.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        // Not used in login
                    }

                    @Override
                    public void onAuthenticated() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Connected successfully!", Toast.LENGTH_SHORT).show();
                        // Go directly to contacts since user is already registered in gateway
                        openContactsActivity();
                    }

                    @Override
                    public void onDisconnected() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Login error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onAuthenticated() {
                // Not used in connect
            }

            @Override
            public void onDisconnected() {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "Connection error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void connectAndRegister() {
        final String server = etServer.getText().toString().trim();
        final String portStr = etPort.getText().toString().trim();
        final String domain = etDomain.getText().toString().trim();
        final String username = etUsername.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        final String gateway = etGateway.getText().toString().trim();

        if (server.isEmpty() || portStr.isEmpty() || domain.isEmpty() ||
            username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
            return;
        }

        saveSettings();
        whatsAppManager.setGatewayJid(gateway);

        progressDialog.setMessage("Connecting to XMPP server...");
        progressDialog.show();

        xmppManager.connect(server, port, domain, new XMPPManager.ConnectionCallback() {
            @Override
            public void onConnected() {
                progressDialog.setMessage("Registering account...");
                xmppManager.register(username, password, new XMPPManager.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        // Not used
                    }

                    @Override
                    public void onAuthenticated() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Account registered successfully!", Toast.LENGTH_SHORT).show();
                        openQRCodeActivity();
                    }

                    @Override
                    public void onDisconnected() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Registration error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onAuthenticated() {
                // Not used
            }

            @Override
            public void onDisconnected() {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "Connection error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openQRCodeActivity() {
        Intent intent = new Intent(this, QRCodeActivity.class);
        startActivity(intent);
    }

    private void openContactsActivity() {
        Intent intent = new Intent(this, MainTabsActivity.class);
        startActivity(intent);
        finish(); // Close login screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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
                    Toast.makeText(MainActivity.this, "Path cannot be empty", Toast.LENGTH_SHORT).show();
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
                final boolean success = DatabaseHelper.migrateDatabase(MainActivity.this, newLocation, customPath);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();

                        if (success) {
                            Toast.makeText(MainActivity.this,
                                "Database migrated successfully!",
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this,
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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

        DatabaseHelper dbHelper = xmppManager.getDatabaseHelper();
        String dbPath = dbHelper != null ? dbHelper.getDatabaseLocation() : "Unknown";

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
