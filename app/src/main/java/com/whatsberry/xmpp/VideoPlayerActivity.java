package com.whatsberry.xmpp;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

public class VideoPlayerActivity extends Activity {
    private static final String TAG = "VideoPlayerActivity";
    private VideoView videoView;
    private ProgressBar progressBar;
    private String videoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        videoView = (VideoView) findViewById(R.id.video_view);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);

        videoUrl = getIntent().getStringExtra("VIDEO_URL");
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Loading video from: " + videoUrl);

        // Setup media controller
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        // Set video URI
        videoView.setVideoURI(Uri.parse(videoUrl));

        // Listeners
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "Video prepared");
                progressBar.setVisibility(View.GONE);
                videoView.start();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "Video error - what: " + what + ", extra: " + extra);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(VideoPlayerActivity.this, "Failed to load video", Toast.LENGTH_LONG).show();
                return true;
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "Video playback completed");
            }
        });

        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}
