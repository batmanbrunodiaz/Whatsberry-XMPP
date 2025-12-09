package com.whatsberry.xmpp;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class VoicePlayerActivity extends Activity {
    private static final String TAG = "VoicePlayerActivity";

    private MediaPlayer mediaPlayer;
    private ImageButton playPauseButton;
    private SeekBar seekBar;
    private TextView currentTime;
    private TextView totalTime;
    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private String audioUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_player);

        playPauseButton = (ImageButton) findViewById(R.id.play_pause_button);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        currentTime = (TextView) findViewById(R.id.current_time);
        totalTime = (TextView) findViewById(R.id.total_time);

        audioUrl = getIntent().getStringExtra("AUDIO_URL");
        if (audioUrl == null || audioUrl.isEmpty()) {
            Toast.makeText(this, "No audio URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        prepareAudio(audioUrl);

        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    pauseAudio();
                } else {
                    playAudio();
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void prepareAudio(String url) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(url);

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    int duration = mediaPlayer.getDuration();
                    seekBar.setMax(duration);
                    totalTime.setText(formatTime(duration));
                    playPauseButton.setEnabled(true);
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    isPlaying = false;
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                    handler.removeCallbacks(updateSeekBar);
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error - what: " + what + ", extra: " + extra);
                    Toast.makeText(VoicePlayerActivity.this, "Error playing audio", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            mediaPlayer.prepareAsync();
            playPauseButton.setEnabled(false);

        } catch (Exception e) {
            Log.e(TAG, "Error preparing audio", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void playAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            isPlaying = true;
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            handler.post(updateSeekBar);
        }
    }

    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            handler.removeCallbacks(updateSeekBar);
        }
    }

    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(currentPosition);
                currentTime.setText(formatTime(currentPosition));
                handler.postDelayed(this, 100);
            }
        }
    };

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            pauseAudio();
        }
    }
}
