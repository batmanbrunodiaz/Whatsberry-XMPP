package com.whatsberry.xmpp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

public class ImageViewerActivity extends Activity {
    private static final String TAG = "ImageViewerActivity";
    private ImageView imageView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        imageView = (ImageView) findViewById(R.id.fullscreen_image);
        progressBar = (ProgressBar) findViewById(R.id.loading_progress);

        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(this, "No image URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadImage(imageUrl);

        // Click to close
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void loadImage(final String imageUrl) {
        progressBar.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(imageUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setDoInput(true);

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Image load response code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        final Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap);
                                    imageView.setVisibility(View.VISIBLE);
                                } else {
                                    Toast.makeText(ImageViewerActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to load image. Response code: " + responseCode);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(ImageViewerActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Error loading image", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(ImageViewerActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }
}
