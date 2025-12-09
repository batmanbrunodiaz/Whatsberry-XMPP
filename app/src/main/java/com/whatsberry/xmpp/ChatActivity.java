package com.whatsberry.xmpp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Chat Activity
 * Chat screen for messaging with a contact
 */
public class ChatActivity extends Activity {
    private static final int PICK_FILE_REQUEST = 1;
    private static final int CAMERA_PHOTO_REQUEST = 2;
    private static final int CAMERA_VIDEO_REQUEST = 3;
    private static final int PICK_CONTACT_REQUEST = 4;

    private ListView lvMessages;
    private EditText etMessage;
    private TextView tvContactName;

    // New UI elements
    private android.widget.Button btnEmoji, btnAttach, btnCameraShortcut, btnAudioOrSend;
    private android.widget.LinearLayout attachmentMenuContainer, audioRecordingOverlay;
    private TextView tvRecordingTime, tvTypingIndicator;
    private android.widget.Button btnCancelRecording, btnSendRecording, btnClearConversation;

    // Attachment menu items
    private android.widget.LinearLayout attachGallery, attachCamera, attachLocation, attachContact, attachDocument, attachAudio;

    private Uri photoUri;
    private Uri videoUri;

    private XMPPManager xmppManager;
    private FileUploadManager fileUploadManager;
    private MessagesAdapter adapter;
    private List<ChatMessage> messages;

    private String contactJid;
    private String contactName;

    // Attachment menu state
    private boolean isAttachmentMenuOpen = false;

    // Audio recording
    private android.media.MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;
    private long recordingStartTime = 0;
    private android.os.Handler recordingHandler = new android.os.Handler();

    // Typing indicators
    private android.os.Handler typingHandler = new android.os.Handler();
    private Runnable typingTimeoutRunnable;
    private boolean isUserTyping = false;

    // Audio playback
    private android.media.MediaPlayer mediaPlayer;
    private String currentlyPlayingUrl;

    // Emoji parsing in EditText
    private boolean isUpdatingEmojiSpans = false;
    private android.os.Handler emojiParseHandler = new android.os.Handler();
    private Runnable emojiParseRunnable = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        xmppManager = XMPPManager.getInstance();
        fileUploadManager = FileUploadManager.getInstance();
        fileUploadManager.setConnection(xmppManager.getConnection());

        // Get contact info from intent
        contactJid = getIntent().getStringExtra("contactJid");
        contactName = getIntent().getStringExtra("contactName");

        if (contactJid == null) {
            Toast.makeText(this, "Error: No contact specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupListeners();
        setupMessageListener();
        loadMessageHistory();

        // Mark all messages from this contact as read
        markMessagesAsRead();
    }

    private void initializeViews() {
        lvMessages = (ListView) findViewById(R.id.lvMessages);
        etMessage = (EditText) findViewById(R.id.etMessage);
        tvContactName = (TextView) findViewById(R.id.tvContactName);
        tvTypingIndicator = (TextView) findViewById(R.id.tvTypingIndicator);

        // New UI elements
        btnEmoji = (android.widget.Button) findViewById(R.id.btnEmoji);
        btnAttach = (android.widget.Button) findViewById(R.id.btnAttach);
        btnCameraShortcut = (android.widget.Button) findViewById(R.id.btnCameraShortcut);
        btnAudioOrSend = (android.widget.Button) findViewById(R.id.btnAudioOrSend);

        // Attachment menu
        attachmentMenuContainer = (android.widget.LinearLayout) findViewById(R.id.attachmentMenuContainer);
        attachGallery = (android.widget.LinearLayout) findViewById(R.id.attachGallery);
        attachCamera = (android.widget.LinearLayout) findViewById(R.id.attachCamera);
        attachLocation = (android.widget.LinearLayout) findViewById(R.id.attachLocation);
        attachContact = (android.widget.LinearLayout) findViewById(R.id.attachContact);
        attachDocument = (android.widget.LinearLayout) findViewById(R.id.attachDocument);
        attachAudio = (android.widget.LinearLayout) findViewById(R.id.attachAudio);

        // Recording overlay
        audioRecordingOverlay = (android.widget.LinearLayout) findViewById(R.id.audioRecordingOverlay);
        tvRecordingTime = (TextView) findViewById(R.id.tvRecordingTime);
        btnCancelRecording = (android.widget.Button) findViewById(R.id.btnCancelRecording);
        btnSendRecording = (android.widget.Button) findViewById(R.id.btnSendRecording);

        // Clear conversation button
        btnClearConversation = (android.widget.Button) findViewById(R.id.btnClearConversation);

        tvContactName.setText(contactName != null ? contactName : contactJid);

        messages = new ArrayList<>();
        adapter = new MessagesAdapter();
        lvMessages.setAdapter(adapter);
    }

    private void setupListeners() {
        // Emoji button
        btnEmoji.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmojiPicker();
            }
        });

        // Attach button - toggles attachment menu
        btnAttach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
            }
        });

        // Camera shortcut
        btnCameraShortcut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });

        // Audio or Send button - changes based on message text
        btnAudioOrSend.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                String messageText = etMessage.getText().toString().trim();
                if (!messageText.isEmpty()) {
                    // Send button behavior
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        sendMessage();
                        return true;
                    }
                } else {
                    // Audio hold-to-record behavior
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        startRecordingHold();
                        return true;
                    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP ||
                               event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                        stopRecordingHold();
                        return true;
                    }
                }
                return false;
            }
        });

        // Update button icon when text changes
        etMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    btnAudioOrSend.setText("➤");
                    // Send "composing" state if not already typing
                    if (!isUserTyping) {
                        isUserTyping = true;
                        xmppManager.sendTypingState(contactJid, true);
                    }

                    // Reset typing timeout
                    if (typingTimeoutRunnable != null) {
                        typingHandler.removeCallbacks(typingTimeoutRunnable);
                    }
                    typingTimeoutRunnable = new Runnable() {
                        @Override
                        public void run() {
                            // Send "paused" state after 3 seconds of inactivity
                            isUserTyping = false;
                            xmppManager.sendTypingState(contactJid, false);
                        }
                    };
                    typingHandler.postDelayed(typingTimeoutRunnable, 3000);
                } else {
                    btnAudioOrSend.setText("🎤");
                    // Send "paused" state when text is empty
                    if (isUserTyping) {
                        isUserTyping = false;
                        xmppManager.sendTypingState(contactJid, false);
                        if (typingTimeoutRunnable != null) {
                            typingHandler.removeCallbacks(typingTimeoutRunnable);
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(final android.text.Editable editable) {
                // Skip if we're already updating to prevent infinite loop
                if (isUpdatingEmojiSpans) {
                    return;
                }

                // Cancel any pending emoji parsing
                if (emojiParseRunnable != null) {
                    emojiParseHandler.removeCallbacks(emojiParseRunnable);
                }

                // Schedule emoji parsing with 300ms delay to avoid parsing every keystroke
                emojiParseRunnable = new Runnable() {
                    @Override
                    public void run() {
                        isUpdatingEmojiSpans = true;
                        try {
                            int cursorPosition = etMessage.getSelectionStart();
                            CharSequence parsedText = TwemojiParser.parseEmojis(ChatActivity.this, editable.toString());

                            if (parsedText instanceof android.text.Spannable) {
                                android.text.Spannable spannable = (android.text.Spannable) parsedText;

                                // Remove old emoji ImageSpans
                                android.text.style.ImageSpan[] oldSpans = editable.getSpans(0, editable.length(), android.text.style.ImageSpan.class);
                                for (android.text.style.ImageSpan span : oldSpans) {
                                    editable.removeSpan(span);
                                }

                                // Apply new emoji ImageSpans
                                android.text.style.ImageSpan[] newSpans = spannable.getSpans(0, spannable.length(), android.text.style.ImageSpan.class);
                                for (android.text.style.ImageSpan span : newSpans) {
                                    int start = spannable.getSpanStart(span);
                                    int end = spannable.getSpanEnd(span);
                                    int flags = spannable.getSpanFlags(span);
                                    editable.setSpan(new android.text.style.ImageSpan(span.getDrawable(), android.text.style.ImageSpan.ALIGN_BASELINE), start, end, flags);
                                }

                                // Restore cursor position
                                if (cursorPosition >= 0 && cursorPosition <= editable.length()) {
                                    etMessage.setSelection(cursorPosition);
                                }
                            }
                        } finally {
                            isUpdatingEmojiSpans = false;
                        }
                    }
                };
                emojiParseHandler.postDelayed(emojiParseRunnable, 300);
            }
        });

        // Attachment menu items
        attachGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
                openFilePicker();
            }
        });

        attachCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
                takePhoto();
            }
        });

        attachLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
                shareLocation();
            }
        });

        attachContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
                shareContact();
            }
        });

        attachDocument.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
                openFilePicker();
            }
        });

        attachAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAttachmentMenu();
                startRecording();
            }
        });

        // Recording overlay controls
        btnCancelRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelRecording();
            }
        });

        btnSendRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        // Clear conversation button
        btnClearConversation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearConversationDialog();
            }
        });
    }

    private void showEmojiPicker() {
        EmojiPickerDialog dialog = new EmojiPickerDialog(this, new EmojiPickerDialog.EmojiSelectedListener() {
            @Override
            public void onEmojiSelected(String emoji) {
                // Insert emoji at cursor position
                int start = etMessage.getSelectionStart();
                int end = etMessage.getSelectionEnd();
                etMessage.getText().replace(Math.min(start, end), Math.max(start, end), emoji, 0, emoji.length());
            }
        });
        dialog.show();
    }

    private void toggleAttachmentMenu() {
        if (isAttachmentMenuOpen) {
            attachmentMenuContainer.setVisibility(View.GONE);
            isAttachmentMenuOpen = false;
        } else {
            attachmentMenuContainer.setVisibility(View.VISIBLE);
            isAttachmentMenuOpen = true;
        }
    }

    private void startRecordingHold() {
        // Show recording overlay
        audioRecordingOverlay.setVisibility(View.VISIBLE);
        startRecording();
    }

    private void stopRecordingHold() {
        if (isRecording) {
            stopRecording();
        }
        audioRecordingOverlay.setVisibility(View.GONE);
    }

    private void cancelRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e("ChatActivity", "Error stopping recording", e);
            }
            mediaRecorder = null;
        }
        isRecording = false;
        recordingHandler.removeCallbacksAndMessages(null);
        audioRecordingOverlay.setVisibility(View.GONE);

        // Delete the audio file
        if (audioFilePath != null) {
            File file = new File(audioFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void shareLocation() {
        final android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            // First try to use last known location (fastest)
            android.location.Location lastKnown = null;

            // Try GPS first
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            }

            // Fallback to Network if GPS unavailable
            if (lastKnown == null && locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            }

            // If we have a recent last known location, use it immediately
            if (lastKnown != null) {
                long locationAge = System.currentTimeMillis() - lastKnown.getTime();
                // If location is less than 5 minutes old, use it
                if (locationAge < 5 * 60 * 1000) {
                    sendLocationMessage(lastKnown);
                    return;
                }
            }

            // Otherwise, request fresh location with timeout
            Toast.makeText(this, "Getting current location...", Toast.LENGTH_SHORT).show();

            final boolean[] locationReceived = {false};

            final android.location.LocationListener locationListener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (!locationReceived[0] && location != null) {
                        locationReceived[0] = true;
                        locationManager.removeUpdates(this);
                        sendLocationMessage(location);
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}
            };

            // Request location update from best provider
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, locationListener, null);
            } else if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, locationListener, null);
            } else {
                Toast.makeText(this, "Please enable location services", Toast.LENGTH_LONG).show();
                return;
            }

            // Set timeout - if no location after 10 seconds, use last known or fail
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!locationReceived[0]) {
                        locationManager.removeUpdates(locationListener);

                        // Try last known location as fallback
                        android.location.Location fallback = null;
                        try {
                            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                                fallback = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                            }
                            if (fallback == null && locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                                fallback = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                            }
                        } catch (SecurityException e) {
                            // Ignore
                        }

                        if (fallback != null) {
                            Toast.makeText(ChatActivity.this, "Using last known location", Toast.LENGTH_SHORT).show();
                            sendLocationMessage(fallback);
                        } else {
                            Toast.makeText(ChatActivity.this, "Unable to get location. Please try again.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }, 10000); // 10 second timeout

        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendLocationMessage(android.location.Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        final String locationUrl = "https://www.google.com/maps?q=" + latitude + "," + longitude;

        xmppManager.sendMessage(contactJid, "📍 Location: " + locationUrl, new XMPPManager.MessageCallback() {
            @Override
            public void onMessageReceived(String from, String message, boolean isSent) {
                // Not used for sending
            }

            @Override
            public void onMessageSent() {
                ChatMessage msg = new ChatMessage();
                msg.text = "📍 Location: " + locationUrl;
                msg.isSent = true;
                msg.timestamp = System.currentTimeMillis();
                msg.status = MessageStatus.DELIVERED;
                messages.add(msg);
                adapter.notifyDataSetChanged();
                lvMessages.setSelection(adapter.getCount() - 1);
                Toast.makeText(ChatActivity.this, "Location sent!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMessageError(String error) {
                Toast.makeText(ChatActivity.this, "Failed to share location: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareContact() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        try {
            startActivityForResult(intent, PICK_CONTACT_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to access contacts", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // All files
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FILE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Please install a file manager", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // Create a file to store the photo
            try {
                File photoFile = createImageFile();
                if (photoFile != null) {
                    photoUri = Uri.fromFile(photoFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(intent, CAMERA_PHOTO_REQUEST);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error creating photo file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void takeVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // Create a file to store the video
            try {
                File videoFile = createVideoFile();
                if (videoFile != null) {
                    videoUri = Uri.fromFile(videoFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1); // High quality
                    intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60); // 60 seconds max
                    startActivityForResult(intent, CAMERA_VIDEO_REQUEST);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error creating video file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);

        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private File createVideoFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String videoFileName = "MP4_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES);

        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        return File.createTempFile(videoFileName, ".mp4", storageDir);
    }

    private void startRecording() {
        try {
            // Create audio file
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);

            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs();
            }

            File audioFile = new File(storageDir, "AUDIO_" + timeStamp + ".3gp");
            audioFilePath = audioFile.getAbsolutePath();

            // Initialize MediaRecorder
            mediaRecorder = new android.media.MediaRecorder();
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordingStartTime = System.currentTimeMillis();

            // Show recording overlay if hold-to-record
            if (audioRecordingOverlay.getVisibility() == View.VISIBLE) {
                updateRecordingTime();
            } else {
                Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
    }

    private void updateRecordingTime() {
        if (isRecording) {
            long elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000;
            long minutes = elapsed / 60;
            long seconds = elapsed % 60;
            tvRecordingTime.setText(String.format("%d:%02d", minutes, seconds));

            // Update every second
            recordingHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateRecordingTime();
                }
            }, 1000);
        }
    }

    private void stopRecording() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            isRecording = false;
            recordingHandler.removeCallbacksAndMessages(null);

            // Upload the recorded audio
            if (audioFilePath != null) {
                File audioFile = new File(audioFilePath);
                if (audioFile.exists()) {
                    Toast.makeText(this, "Uploading audio...", Toast.LENGTH_SHORT).show();
                    uploadAndSendFile(audioFile);
                } else {
                    Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show();
                }
            }

        } catch (Exception e) {
            Toast.makeText(this, "Failed to stop recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
            recordingHandler.removeCallbacksAndMessages(null);
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
    }

    private void playAudio(final String audioUrl) {
        try {
            // If already playing this URL, stop it
            if (currentlyPlayingUrl != null && currentlyPlayingUrl.equals(audioUrl)) {
                stopAudio();
                return;
            }

            // Stop any currently playing audio
            stopAudio();

            // Initialize MediaPlayer
            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);

            // Set data source (URL)
            mediaPlayer.setDataSource(audioUrl);

            // Prepare async
            mediaPlayer.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(android.media.MediaPlayer mp) {
                    mp.start();
                    currentlyPlayingUrl = audioUrl;
                    Toast.makeText(ChatActivity.this, "Playing audio...", Toast.LENGTH_SHORT).show();
                }
            });

            // Handle completion
            mediaPlayer.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(android.media.MediaPlayer mp) {
                    stopAudio();
                }
            });

            // Handle errors
            mediaPlayer.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    Toast.makeText(ChatActivity.this, "Error playing audio", Toast.LENGTH_SHORT).show();
                    stopAudio();
                    return true;
                }
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to play audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopAudio();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
            mediaPlayer = null;
        }
        currentlyPlayingUrl = null;
    }

    private void openFile(String fileUrl) {
        try {
            String lowerUrl = fileUrl.toLowerCase();
            Intent intent;

            if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif")) {
                // Open image in ImageViewerActivity
                intent = new Intent(this, ImageViewerActivity.class);
                intent.putExtra("IMAGE_URL", fileUrl);
                startActivity(intent);

            } else if (lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".3gp") ||
                      lowerUrl.endsWith(".webm")) {
                // Open video in VideoPlayerActivity
                intent = new Intent(this, VideoPlayerActivity.class);
                intent.putExtra("VIDEO_URL", fileUrl);
                startActivity(intent);

            } else if (lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".ogg") ||
                      lowerUrl.endsWith(".m4a") || lowerUrl.endsWith(".opus") ||
                      lowerUrl.contains("format=mp3")) {
                // Open audio in VoicePlayerActivity
                // Add ?format=mp3 for opus/ogg conversion (BB10 compatibility)
                String audioUrl = fileUrl;
                if (!audioUrl.contains("format=mp3")) {
                    if (audioUrl.contains("?")) {
                        audioUrl += "&format=mp3";
                    } else {
                        audioUrl += "?format=mp3";
                    }
                }
                intent = new Intent(this, VoicePlayerActivity.class);
                intent.putExtra("AUDIO_URL", audioUrl);
                startActivity(intent);

            } else {
                // Fallback to system viewer for other files
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(fileUrl));
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("ChatActivity", "Error opening file", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                Uri fileUri = data.getData();
                String filePath = getFilePathFromUri(fileUri);
                if (filePath != null) {
                    File file = new File(filePath);
                    uploadAndSendFile(file);
                } else {
                    Toast.makeText(this, "Could not access file", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == CAMERA_PHOTO_REQUEST && resultCode == RESULT_OK) {
            // Photo taken successfully
            if (photoUri != null) {
                String filePath = photoUri.getPath();
                if (filePath != null) {
                    File photoFile = new File(filePath);
                    if (photoFile.exists()) {
                        uploadAndSendFile(photoFile);
                    } else {
                        Toast.makeText(this, "Photo file not found", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == CAMERA_VIDEO_REQUEST && resultCode == RESULT_OK) {
            // Video recorded successfully
            if (videoUri != null) {
                String filePath = videoUri.getPath();
                if (filePath != null) {
                    File videoFile = new File(filePath);
                    if (videoFile.exists()) {
                        uploadAndSendFile(videoFile);
                    } else {
                        Toast.makeText(this, "Video file not found", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Failed to record video", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            // Contact picked
            if (data != null) {
                Uri contactUri = data.getData();
                String[] projection = {
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                };

                Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER);

                    String contactName = cursor.getString(nameIndex);
                    String contactNumber = cursor.getString(numberIndex);
                    cursor.close();

                    // Send contact as vCard format
                    String contactMessage = "BEGIN:VCARD\nVERSION:3.0\nFN:" + contactName + "\nTEL:" + contactNumber + "\nEND:VCARD";

                    xmppManager.sendMessage(contactJid, "👤 Contact: " + contactName + "\n" + contactNumber, new XMPPManager.MessageCallback() {
                        @Override
                        public void onMessageReceived(String from, String message, boolean isSent) {
                            // Not used for sending
                        }

                        @Override
                        public void onMessageSent() {
                            ChatMessage msg = new ChatMessage();
                            msg.text = "👤 Contact: " + contactName + "\n" + contactNumber;
                            msg.isSent = true;
                            msg.timestamp = System.currentTimeMillis();
                            msg.status = MessageStatus.DELIVERED;
                            messages.add(msg);
                            adapter.notifyDataSetChanged();
                            lvMessages.setSelection(adapter.getCount() - 1);
                        }

                        @Override
                        public void onMessageError(String error) {
                            Toast.makeText(ChatActivity.this, "Failed to share contact: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
    }

    private String getFilePathFromUri(Uri uri) {
        String filePath = null;
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(projection[0]);
                filePath = cursor.getString(columnIndex);
            }
            cursor.close();
        }

        // Fallback for direct file paths
        if (filePath == null) {
            filePath = uri.getPath();
        }

        return filePath;
    }

    private void uploadAndSendFile(final File file) {
        Toast.makeText(this, "Uploading " + file.getName() + "...", Toast.LENGTH_SHORT).show();

        fileUploadManager.uploadFile(file, new FileUploadManager.UploadCallback() {
            @Override
            public void onUploadSuccess(String downloadUrl) {
                // Send file via XMPP with OOB (Out of Band Data)
                xmppManager.sendFileMessage(contactJid, downloadUrl, file.getName(), new XMPPManager.MessageCallback() {
                    @Override
                    public void onMessageReceived(String from, String message, boolean isSent) {
                        // Not used
                    }

                    @Override
                    public void onMessageSent() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ChatActivity.this, "File sent!", Toast.LENGTH_SHORT).show();
                                // Add file message to chat
                                ChatMessage msg = new ChatMessage();
                                msg.text = file.getName();
                                msg.isSent = true;
                                msg.timestamp = System.currentTimeMillis();
                                msg.fileUrl = downloadUrl;
                                msg.isFile = true;
                                msg.status = MessageStatus.DELIVERED;
                                messages.add(msg);
                                adapter.notifyDataSetChanged();
                                lvMessages.setSelection(adapter.getCount() - 1);
                            }
                        });
                    }

                    @Override
                    public void onMessageError(String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ChatActivity.this, "Failed to send: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }

            @Override
            public void onUploadError(String error) {
                Toast.makeText(ChatActivity.this, "Upload failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupMessageListener() {
        xmppManager.setMessageCallback(new XMPPManager.MessageCallback() {
            @Override
            public void onMessageReceived(String from, String message, boolean isSent) {
                // Log all incoming messages for debugging
                Log.d("ChatActivity", "MESSAGE RECEIVED - From: " + from + ", Contact: " + contactJid + ", Message: " + message);

                // Check if message is from/to current contact
                if (from.contains(contactJid) || from.startsWith(contactJid)) {
                    Log.d("ChatActivity", "Message matches current contact, adding to UI");
                    addMessage(message, isSent); // isSent = true for carbon copies of sent messages
                } else {
                    Log.w("ChatActivity", "Message doesn't match current contact, ignoring");
                }
            }

            @Override
            public void onMessageSent() {
                // Message sent confirmation handled in sendMessage()
            }

            @Override
            public void onMessageError(String error) {
                Toast.makeText(ChatActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        // Setup typing state listener
        xmppManager.setTypingStateCallback(new XMPPManager.TypingStateCallback() {
            @Override
            public void onTypingStateChanged(String from, boolean isTyping) {
                // Check if typing state is from current contact
                if (from.contains(contactJid) || from.startsWith(contactJid)) {
                    tvTypingIndicator.setVisibility(isTyping ? View.VISIBLE : View.GONE);
                }
            }
        });

        // Setup message retraction listener
        xmppManager.setMessageRetractCallback(new XMPPManager.MessageRetractCallback() {
            @Override
            public void onMessageRetracted(String from, final String stanzaId) {
                // Check if retraction is from current contact
                if (from.contains(contactJid) || from.startsWith(contactJid)) {
                    Log.d("ChatActivity", "Received retraction for stanza ID: " + stanzaId);

                    // Find and remove message with matching stanza ID from UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = messages.size() - 1; i >= 0; i--) {
                                ChatMessage msg = messages.get(i);
                                if (stanzaId.equals(msg.stanzaId)) {
                                    messages.remove(i);
                                    adapter.notifyDataSetChanged();
                                    Toast.makeText(ChatActivity.this,
                                        "Message deleted by sender", Toast.LENGTH_SHORT).show();
                                    Log.d("ChatActivity", "Removed message from UI");
                                    break;
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private void loadMessageHistory() {
        // Load ALL messages (no limit)
        xmppManager.loadMessageHistory(contactJid, Integer.MAX_VALUE, new XMPPManager.MessageHistoryCallback() {
            @Override
            public void onHistoryLoaded(List<XMPPManager.HistoricalMessage> historyMessages) {
                // Add historical messages to the chat
                for (XMPPManager.HistoricalMessage histMsg : historyMessages) {
                    ChatMessage msg = new ChatMessage();
                    msg.databaseId = histMsg.databaseId; // Assign database ID for edit/delete
                    msg.stanzaId = histMsg.stanzaId; // Assign stanza ID for retraction
                    msg.text = histMsg.body;
                    msg.isSent = histMsg.isSent;
                    msg.timestamp = histMsg.timestamp;
                    msg.status = histMsg.isSent ? MessageStatus.DELIVERED : null;

                    // Set file fields if this is a multimedia message
                    if (histMsg.fileUrl != null && !histMsg.fileUrl.isEmpty()) {
                        msg.fileUrl = histMsg.fileUrl;
                        msg.isFile = true;
                    }

                    messages.add(msg);
                }

                // Update UI
                adapter.notifyDataSetChanged();

                // Scroll to bottom
                lvMessages.post(new Runnable() {
                    @Override
                    public void run() {
                        if (adapter.getCount() > 0) {
                            lvMessages.setSelection(adapter.getCount() - 1);
                        }
                    }
                });

                Toast.makeText(ChatActivity.this,
                        "Loaded " + historyMessages.size() + " messages",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onHistoryError(String error) {
                Toast.makeText(ChatActivity.this,
                        "Could not load history: " + error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Mark all messages from this contact as read
     */
    private void markMessagesAsRead() {
        try {
            DatabaseHelper dbHelper = xmppManager.getDatabaseHelper();
            if (dbHelper != null && contactJid != null) {
                int marked = dbHelper.markMessagesAsRead(contactJid);
                Log.d("ChatActivity", "Marked " + marked + " messages as read for " + contactJid);
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Error marking messages as read", e);
        }
    }

    private void sendMessage() {
        final String messageText = etMessage.getText().toString().trim();

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear input immediately
        etMessage.setText("");

        // Add to local list
        addMessage(messageText, true);
        final int messageIndex = messages.size() - 1;

        // Send via XMPP
        xmppManager.sendMessage(contactJid, messageText, new XMPPManager.MessageCallback() {
            @Override
            public void onMessageReceived(String from, String message, boolean isSent) {
                // Not used here
            }

            @Override
            public void onMessageSent() {
                // Message sent successfully - update status to DELIVERED
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (messageIndex >= 0 && messageIndex < messages.size()) {
                            messages.get(messageIndex).status = MessageStatus.DELIVERED;
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }

            @Override
            public void onMessageError(String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ChatActivity.this,
                                "Failed to send: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * Detect if a message is a call notification
     * Returns MessageType based on message content
     */
    private MessageType detectCallNotification(String text) {
        if (text == null) {
            return MessageType.NORMAL;
        }

        String lowerText = text.toLowerCase();

        // Check for incoming call notification
        if (lowerText.contains("incoming call from")) {
            return MessageType.CALL_INCOMING;
        }

        // Check for missed call notification
        if (lowerText.contains("missed call from")) {
            return MessageType.CALL_MISSED;
        }

        return MessageType.NORMAL;
    }

    /**
     * Extract contact name from call notification message
     * Format: "Incoming call from Kevin Morán (xmpp:+5213141720100@whatsapp.localhost) at 2025-12-07 19:23:10+00:00"
     */
    private String extractContactNameFromCallMessage(String text) {
        if (text == null) {
            return null;
        }

        try {
            // Look for "from " followed by contact name, ending with " ("
            int fromIndex = text.toLowerCase().indexOf("from ");
            if (fromIndex != -1) {
                int startIndex = fromIndex + 5; // Skip "from "
                int endIndex = text.indexOf(" (", startIndex);
                if (endIndex != -1) {
                    return text.substring(startIndex, endIndex).trim();
                }
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Error extracting contact name from call message", e);
        }

        return null;
    }

    private void addMessage(String text, boolean isSent) {
        ChatMessage msg = new ChatMessage();
        msg.isSent = isSent;
        msg.timestamp = System.currentTimeMillis();
        msg.status = isSent ? MessageStatus.SENDING : null; // Sent messages start as SENDING

        // Detect if message is a call notification
        msg.type = detectCallNotification(text);

        // Detect if message is a file URL
        boolean isUrl = text.startsWith("http://") || text.startsWith("https://");
        boolean isMediaUrl = isUrl && (
            text.contains("/attachments/") ||
            text.endsWith(".jpg") || text.endsWith(".jpeg") || text.endsWith(".png") ||
            text.endsWith(".gif") || text.endsWith(".mp4") || text.endsWith(".3gp") ||
            text.endsWith(".mp3") || text.endsWith(".ogg") || text.endsWith(".m4a")
        );

        if (isMediaUrl) {
            // This is a file URL
            msg.fileUrl = text;
            msg.isFile = true;
            // Extract filename from URL
            String filename = text.substring(text.lastIndexOf("/") + 1);
            msg.text = filename;
        } else {
            // Regular text message
            msg.text = text;
            msg.isFile = false;
        }

        messages.add(msg);
        adapter.notifyDataSetChanged();

        // Scroll to bottom
        lvMessages.post(new Runnable() {
            @Override
            public void run() {
                lvMessages.setSelection(adapter.getCount() - 1);
            }
        });
    }

    private boolean isAudioFile(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".ogg") ||
               lowerUrl.endsWith(".m4a") || lowerUrl.endsWith(".3gp") ||
               lowerUrl.endsWith(".aac") || lowerUrl.endsWith(".opus") ||
               lowerUrl.endsWith(".webm") || lowerUrl.contains("convert_audio");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set this chat as currently open (prevent notifications)
        MainTabsActivity.currentOpenChatJid = contactJid;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear currently open chat
        MainTabsActivity.currentOpenChatJid = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up audio resources
        stopAudio();
        // Clean up recording resources
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                // Ignore
            }
            mediaRecorder = null;
        }
        // Clear currently open chat
        MainTabsActivity.currentOpenChatJid = null;
    }

    // Message model
    private static class ChatMessage {
        long databaseId = -1; // ID from database, -1 if not yet stored
        String stanzaId; // XMPP stanza ID for message retraction
        String text;
        boolean isSent;
        long timestamp;
        String fileUrl; // URL for image/file attachments
        boolean isFile; // true if this is a file/image message
        MessageStatus status; // Message delivery status (sent/delivered/read)
        MessageType type; // Type of message (normal, call, etc.)
    }

    // Message type enum
    private enum MessageType {
        NORMAL,         // Regular text message
        CALL_INCOMING,  // Incoming call notification
        CALL_MISSED     // Missed call notification
    }

    // Message status enum
    private enum MessageStatus {
        SENDING,    // ⏱ Message being sent
        SENT,       // ✓ Message sent to server
        DELIVERED,  // ✓✓ Message delivered to recipient
        READ        // ✓✓ (blue) Message read by recipient
    }

    // Custom adapter for messages with proper bubble alignment
    private class MessagesAdapter extends ArrayAdapter<ChatMessage> {
        MessagesAdapter() {
            super(ChatActivity.this, 0, messages);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ChatMessage msg = messages.get(position);

            // Use different layouts for sent vs received messages
            int layoutId = msg.isSent ? R.layout.item_message_sent : R.layout.item_message_received;

            View view = convertView;
            // Only reuse view if it's the same type
            if (view != null) {
                int currentLayoutId = (Integer) view.getTag();
                if (currentLayoutId != layoutId) {
                    view = null;
                }
            }

            if (view == null) {
                view = getLayoutInflater().inflate(layoutId, parent, false);
                view.setTag(layoutId);
            }

            TextView tvMessageText = (TextView) view.findViewById(R.id.tvMessageText);
            TextView tvMessageTime = (TextView) view.findViewById(R.id.tvMessageTime);
            TextView tvMessageStatus = (TextView) view.findViewById(R.id.tvMessageStatus);

            // Handle call notifications first
            if (msg.type != null && msg.type != MessageType.NORMAL) {
                View messageBubble = view.findViewById(R.id.messageBubble);

                String displayText = "";

                if (msg.type == MessageType.CALL_INCOMING) {
                    displayText = "📞 Incoming call";
                    // Light blue background for incoming calls
                    if (messageBubble != null) {
                        messageBubble.setBackgroundColor(0xFFE3F2FD);
                    }
                } else if (msg.type == MessageType.CALL_MISSED) {
                    displayText = "📞 Missed call";
                    // Light red background for missed calls
                    if (messageBubble != null) {
                        messageBubble.setBackgroundColor(0xFFFFEBEE);
                    }
                }

                // Extract contact name from message if present
                // Format: "Incoming call from Kevin Morán (xmpp:+5213141720100@whatsapp.localhost) at 2025-12-07 19:23:10+00:00"
                String contactNameExtracted = extractContactNameFromCallMessage(msg.text);
                if (contactNameExtracted != null && !contactNameExtracted.isEmpty()) {
                    displayText += " from " + contactNameExtracted;
                }

                tvMessageText.setText(TwemojiParser.parseEmojis(ChatActivity.this, displayText));
                view.setOnClickListener(null);

            } else if (msg.isFile && msg.fileUrl != null) {
                // Handle different types of files
                final String fileUrl = msg.fileUrl;
                String lowerUrl = fileUrl.toLowerCase();

                if (isAudioFile(fileUrl)) {
                    // Audio file - make it clickable to play
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            playAudio(fileUrl);
                        }
                    });
                    String text = "🎵 " + msg.text + " (tap to play)";
                    tvMessageText.setText(TwemojiParser.parseEmojis(ChatActivity.this, text));

                } else if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                          lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif")) {
                    // Image file - show image icon and make clickable
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openFile(fileUrl);
                        }
                    });
                    String text = "🖼 " + msg.text + " (tap to view)";
                    tvMessageText.setText(TwemojiParser.parseEmojis(ChatActivity.this, text));

                } else if (lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".3gp") ||
                          lowerUrl.endsWith(".webm")) {
                    // Video file - show video icon and make clickable
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openFile(fileUrl);
                        }
                    });
                    String text = "🎬 " + msg.text + " (tap to view)";
                    tvMessageText.setText(TwemojiParser.parseEmojis(ChatActivity.this, text));

                } else {
                    // Other file - show document icon and make clickable
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openFile(fileUrl);
                        }
                    });
                    String text = "📎 " + msg.text + " (tap to open)";
                    tvMessageText.setText(TwemojiParser.parseEmojis(ChatActivity.this, text));
                }
            } else {
                // Regular text message - parse emojis with Twemoji
                CharSequence parsedText = TwemojiParser.parseEmojis(ChatActivity.this, msg.text);
                tvMessageText.setText(parsedText);
                view.setOnClickListener(null);
            }

            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(msg.timestamp));
            tvMessageTime.setText(time);

            // Update message status (only for sent messages)
            if (tvMessageStatus != null && msg.isSent) {
                String statusIcon = "";
                int statusColor = 0xFF666666; // Default gray

                if (msg.status != null) {
                    switch (msg.status) {
                        case SENDING:
                            statusIcon = "⏱";
                            statusColor = 0xFF999999;
                            break;
                        case SENT:
                            statusIcon = "✓";
                            statusColor = 0xFF666666;
                            break;
                        case DELIVERED:
                            statusIcon = "✓✓";
                            statusColor = 0xFF666666;
                            break;
                        case READ:
                            statusIcon = "✓✓";
                            statusColor = 0xFF34B7F1; // WhatsApp blue
                            break;
                    }
                } else {
                    statusIcon = "✓"; // Default to sent
                }

                tvMessageStatus.setText(statusIcon);
                tvMessageStatus.setTextColor(statusColor);
            }

            // Add long press listener for message options (edit/delete)
            final int messagePosition = position;
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showMessageOptionsDialog(messagePosition);
                    return true; // Consume the event
                }
            });

            return view;
        }
    }

    /**
     * Show dialog with message options (Copy, Delete)
     */
    private void showMessageOptionsDialog(final int position) {
        final ChatMessage message = messages.get(position);

        // Build options list
        final List<String> options = new ArrayList<>();
        options.add("Copy");

        // Only allow deleting messages that are in database
        if (message.databaseId != -1) {
            options.add("Delete");
        }

        final String[] optionsArray = options.toArray(new String[0]);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Message options");
        builder.setItems(optionsArray, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String selectedOption = optionsArray[which];

                if (selectedOption.equals("Copy")) {
                    copyMessageToClipboard(message.text);
                } else if (selectedOption.equals("Delete")) {
                    deleteMessage(message, position);
                }
            }
        });
        builder.show();
    }

    /**
     * Copy message text to clipboard
     */
    private void copyMessageToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
    }

    /**
     * Show dialog to edit message
     */
    private void showEditMessageDialog(final ChatMessage message, final int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit message");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(message.text);
        input.setSelection(message.text.length()); // Move cursor to end
        builder.setView(input);

        builder.setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String newText = input.getText().toString().trim();
                if (!newText.isEmpty() && !newText.equals(message.text)) {
                    // Update in database
                    boolean success = xmppManager.getDatabaseHelper().updateMessage(message.databaseId, newText);
                    if (success) {
                        // Update in UI
                        message.text = newText;
                        adapter.notifyDataSetChanged();
                        Toast.makeText(ChatActivity.this, "Message edited", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ChatActivity.this, "Failed to edit message", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Delete message with confirmation
     */
    private void deleteMessage(final ChatMessage message, final int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Delete message");
        builder.setMessage("Are you sure you want to delete this message?");

        builder.setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                // Delete from database
                boolean success = xmppManager.getDatabaseHelper().deleteMessage(message.databaseId);
                if (success) {
                    // Remove from UI
                    messages.remove(position);
                    adapter.notifyDataSetChanged();

                    // If message has stanza ID, send retraction to XMPP server
                    // This syncs the deletion with other clients (like Gajim)
                    if (message.stanzaId != null && !message.stanzaId.isEmpty()) {
                        Log.d("ChatActivity", "Sending retraction for stanza ID: " + message.stanzaId);
                        xmppManager.retractMessage(contactJid, message.stanzaId, new XMPPManager.MessageCallback() {
                            @Override
                            public void onMessageReceived(String from, String messageText, boolean isSent) {
                                // Not used
                            }

                            @Override
                            public void onMessageSent() {
                                Log.d("ChatActivity", "Message retraction sent successfully");
                            }

                            @Override
                            public void onMessageError(String error) {
                                Log.e("ChatActivity", "Failed to send retraction: " + error);
                            }
                        });
                    }

                    Toast.makeText(ChatActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to delete message", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Show confirmation dialog to clear entire conversation
     */
    private void showClearConversationDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Clear conversation");
        builder.setMessage("Delete all messages in this conversation?");

        builder.setPositiveButton("Clear all", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                // Delete from database
                int deleted = xmppManager.getDatabaseHelper().deleteAllMessagesForContact(contactJid);

                // Clear from UI
                messages.clear();
                adapter.notifyDataSetChanged();

                Toast.makeText(ChatActivity.this, "Deleted " + deleted + " messages", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
