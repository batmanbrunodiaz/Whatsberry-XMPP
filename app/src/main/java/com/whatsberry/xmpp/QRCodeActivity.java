package com.whatsberry.xmpp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * QR Code Activity
 * Shows QR code for pairing WhatsApp
 */
public class QRCodeActivity extends Activity {
    private ImageView ivQRCode;
    private TextView tvStatus;
    private Button btnGetQR, btnContinue, btnLogout;
    private ProgressDialog progressDialog;

    private WhatsAppManager whatsAppManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode);

        whatsAppManager = WhatsAppManager.getInstance();

        initializeViews();
        setupListeners();

        // First register with gateway, then get QR code
        registerAndGetQR();
    }

    private void initializeViews() {
        ivQRCode = (ImageView) findViewById(R.id.ivQRCode);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        btnGetQR = (Button) findViewById(R.id.btnGetQR);
        btnContinue = (Button) findViewById(R.id.btnContinue);
        btnLogout = (Button) findViewById(R.id.btnLogout);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        tvStatus.setText("Registering with WhatsApp gateway...");
        btnContinue.setEnabled(false);
    }

    private void setupListeners() {
        btnGetQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerAndGetQR();
            }
        });

        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openContactsActivity();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });
    }

    private void registerAndGetQR() {
        progressDialog.setMessage("Registering with gateway using Ad-Hoc Commands...");
        progressDialog.show();

        // Use Ad-Hoc Commands (the proper way for slidge-whatsapp)
        whatsAppManager.registerWithGateway(new WhatsAppManager.RegistrationCallback() {
            @Override
            public void onRegistrationSuccess() {
                // Registration successful, now get QR code
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText("Registration successful! Getting QR code...");
                        getQRCode();
                    }
                });
            }

            @Override
            public void onRegistrationError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        tvStatus.setText("Registration error: " + error);
                        Toast.makeText(QRCodeActivity.this,
                            "Gateway registration failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // Removed getQRViaMessage() - now using getQRCode() with Ad-Hoc Commands

    private void getQRCode() {
        progressDialog.setMessage("Requesting QR code from WhatsApp gateway...");
        progressDialog.show();

        whatsAppManager.getQRCode(new WhatsAppManager.QRCodeCallback() {
            @Override
            public void onQRCodeReceived(String qrData) {
                progressDialog.dismiss();
                displayQRCode(qrData);
                tvStatus.setText("Scan this QR code with your WhatsApp mobile app");
                btnContinue.setEnabled(true);
                Toast.makeText(QRCodeActivity.this, "QR Code received! Scan it with WhatsApp", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onQRCodeError(String error) {
                progressDialog.dismiss();
                tvStatus.setText("Error: " + error);
                Toast.makeText(QRCodeActivity.this, "QR Code error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void displayQRCode(String qrData) {
        try {
            // Generate QR code bitmap
            Bitmap bitmap = encodeAsBitmap(qrData, 512, 512);
            ivQRCode.setImageBitmap(bitmap);
            ivQRCode.setVisibility(View.VISIBLE);
        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate QR code: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap encodeAsBitmap(String contents, int width, int height) throws WriterException {
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(contents,
                    BarcodeFormat.QR_CODE, width, height, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }

        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    private void logout() {
        progressDialog.setMessage("Logging out...");
        progressDialog.show();

        whatsAppManager.logoutWhatsApp(new WhatsAppManager.LogoutCallback() {
            @Override
            public void onLogoutSuccess() {
                progressDialog.dismiss();
                XMPPManager.getInstance().disconnect();
                Toast.makeText(QRCodeActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onLogoutError(String error) {
                progressDialog.dismiss();
                Toast.makeText(QRCodeActivity.this, "Logout error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openContactsActivity() {
        Intent intent = new Intent(this, ContactsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
