package com.whatsberry.xmpp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jivesoftware.smack.AbstractXMPPConnection;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * File Upload Manager (XEP-0363 HTTP File Upload)
 * Handles uploading files to XMPP HTTP upload service
 */
public class FileUploadManager {
    private static final String TAG = "FileUploadManager";

    private static FileUploadManager instance;
    private AbstractXMPPConnection connection;
    private Handler mainHandler;

    // HTTP Upload service - using PHP endpoint (HTTP only, BB10 doesn't support modern TLS)
    // Force SSL disabled in NPM, so HTTP works now
    private static final String UPLOAD_SERVICE = "whatsberry.descarga.media";
    private static final String UPLOAD_URL = "http://whatsberry.descarga.media/upload.php";
    private static final String AUDIO_CONVERT_URL = "http://whatsberry.descarga.media/convert_audio.php";

    private FileUploadManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized FileUploadManager getInstance() {
        if (instance == null) {
            instance = new FileUploadManager();
        }
        return instance;
    }

    public void setConnection(AbstractXMPPConnection connection) {
        this.connection = connection;
    }

    /**
     * Upload a file to the HTTP upload service
     */
    public void uploadFile(final File file, final UploadCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connection == null || !connection.isAuthenticated()) {
                        notifyError(callback, "Not connected to XMPP server");
                        return;
                    }

                    // Get file size
                    long fileSize = file.length();
                    if (fileSize > 100 * 1024 * 1024) { // 100 MB limit (WhatsApp max)
                        notifyError(callback, "File too large (max 100 MB)");
                        return;
                    }

                    // Check if audio file needs conversion
                    boolean needsConversion = needsAudioConversion(file.getName());
                    String uploadEndpoint = needsConversion ? AUDIO_CONVERT_URL : UPLOAD_URL;

                    Log.d(TAG, "Uploading file: " + file.getName() + " (" + fileSize + " bytes)" +
                        (needsConversion ? " [with audio conversion]" : ""));

                    // Upload file via HTTP POST with multipart/form-data
                    URL url = new URL(uploadEndpoint);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    // Setup SSL trust for HTTPS connections
                    if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                        javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection) connection;

                        try {
                            // Try TLSv1.2 first (preferred)
                            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
                            sslContext.init(null, new javax.net.ssl.TrustManager[] {
                                new javax.net.ssl.X509TrustManager() {
                                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                                }
                            }, new java.security.SecureRandom());

                            httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        } catch (Exception e) {
                            // Fallback to TLSv1 for older systems
                            Log.w(TAG, "TLSv1.2 not available, falling back to TLSv1", e);
                            try {
                                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1");
                                sslContext.init(null, new javax.net.ssl.TrustManager[] {
                                    new javax.net.ssl.X509TrustManager() {
                                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                                    }
                                }, new java.security.SecureRandom());

                                httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                            } catch (Exception e2) {
                                Log.e(TAG, "Failed to setup SSL", e2);
                            }
                        }

                        // Set hostname verifier
                        httpsConnection.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                            public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                                return true;
                            }
                        });
                    }

                    String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                    String LINE_FEED = "\r\n";

                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    connection.setConnectTimeout(60000); // 60 seconds
                    connection.setReadTimeout(60000); // 60 seconds

                    OutputStream outputStream = connection.getOutputStream();

                    // Write file part
                    outputStream.write(("--" + boundary + LINE_FEED).getBytes());
                    outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + LINE_FEED).getBytes());
                    outputStream.write(("Content-Type: " + getMimeType(file.getName()) + LINE_FEED).getBytes());
                    outputStream.write((LINE_FEED).getBytes());

                    // Write file content
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    fileInputStream.close();

                    outputStream.write((LINE_FEED).getBytes());
                    outputStream.write(("--" + boundary + "--" + LINE_FEED).getBytes());
                    outputStream.flush();
                    outputStream.close();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Read response JSON
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        // Parse JSON to get URL
                        String responseStr = response.toString();
                        Log.d(TAG, "Upload response: " + responseStr);

                        // Simple JSON parsing (looking for "url" field)
                        int urlStart = responseStr.indexOf("\"url\":\"") + 7;
                        int urlEnd = responseStr.indexOf("\"", urlStart);
                        String downloadUrl = responseStr.substring(urlStart, urlEnd);

                        // Unescape JSON (replace \/ with /)
                        downloadUrl = downloadUrl.replace("\\/", "/");

                        Log.d(TAG, "File uploaded successfully: " + downloadUrl);
                        notifySuccess(callback, downloadUrl);
                    } else {
                        Log.e(TAG, "Upload failed with code: " + responseCode);
                        notifyError(callback, "Upload failed: HTTP " + responseCode);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Upload error", e);
                    notifyError(callback, "Upload error: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Check if audio file needs conversion for BlackBerry 10 compatibility
     * BlackBerry 10 doesn't support opus/ogg but supports MP3
     */
    private boolean needsAudioConversion(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        // Audio formats that need conversion to MP3
        return extension.equals("3gp") || extension.equals("ogg") ||
               extension.equals("opus") || extension.equals("webm") ||
               extension.equals("m4a") || extension.equals("aac");
    }

    /**
     * Get MIME type for file
     */
    private String getMimeType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        // Images
        if (extension.equals("jpg") || extension.equals("jpeg")) return "image/jpeg";
        if (extension.equals("png")) return "image/png";
        if (extension.equals("gif")) return "image/gif";
        if (extension.equals("webp")) return "image/webp";

        // Videos
        if (extension.equals("mp4")) return "video/mp4";
        if (extension.equals("3gp")) return "video/3gpp";
        if (extension.equals("webm")) return "video/webm";

        // Audio
        if (extension.equals("mp3")) return "audio/mpeg";
        if (extension.equals("ogg")) return "audio/ogg";
        if (extension.equals("m4a")) return "audio/mp4";

        // Documents
        if (extension.equals("pdf")) return "application/pdf";
        if (extension.equals("txt")) return "text/plain";

        return "application/octet-stream";
    }

    private void notifySuccess(final UploadCallback callback, final String url) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onUploadSuccess(url);
                }
            });
        }
    }

    private void notifyError(final UploadCallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onUploadError(error);
                }
            });
        }
    }

    public interface UploadCallback {
        void onUploadSuccess(String downloadUrl);
        void onUploadError(String error);
    }
}
