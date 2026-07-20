package com.streampal.broadcaster;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS         = "streampal_prefs";
    private static final String KEY_STREAM    = "stream_key";
    private static final String RTMP_SERVER   = "rtmp://157.245.208.49:1935/sp";
    private static final int    PERM_REQUEST  = 100;

    private EditText streamKeyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen dark background
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#06080a"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 64, 64, 64);

        // Logo
        TextView logo = new TextView(this);
        logo.setText("StreamPal");
        logo.setTextColor(Color.parseColor("#60a5fa"));
        logo.setTextSize(32);
        logo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, 0, 0, 8);

        // Tagline
        TextView tagline = new TextView(this);
        tagline.setText("Broadcaster");
        tagline.setTextColor(Color.parseColor("#64748b"));
        tagline.setTextSize(14);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, 0, 0, 48);

        // Stream key label
        TextView label = new TextView(this);
        label.setText("STREAM KEY");
        label.setTextColor(Color.parseColor("#64748b"));
        label.setTextSize(11);
        label.setPadding(0, 0, 0, 8);
        label.setLetterSpacing(0.15f);

        // Stream key input
        streamKeyInput = new EditText(this);
        streamKeyInput.setHint("your-stream-key");
        streamKeyInput.setHintTextColor(Color.parseColor("#30384a"));
        streamKeyInput.setTextColor(Color.WHITE);
        streamKeyInput.setTextSize(16);
        streamKeyInput.setBackgroundColor(Color.parseColor("#0d1014"));
        streamKeyInput.setPadding(32, 28, 32, 28);
        streamKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        // Load saved key
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedKey = prefs.getString(KEY_STREAM, "");
        streamKeyInput.setText(savedKey);

        // Server display
        TextView serverLabel = new TextView(this);
        serverLabel.setText("SERVER");
        serverLabel.setTextColor(Color.parseColor("#64748b"));
        serverLabel.setTextSize(11);
        serverLabel.setPadding(0, 24, 0, 8);
        serverLabel.setLetterSpacing(0.15f);

        TextView serverVal = new TextView(this);
        serverVal.setText(RTMP_SERVER);
        serverVal.setTextColor(Color.parseColor("#30384a"));
        serverVal.setTextSize(12);
        serverVal.setPadding(0, 0, 0, 48);

        // Go Live button
        Button goLiveBtn = new Button(this);
        goLiveBtn.setText("GO LIVE");
        goLiveBtn.setTextColor(Color.WHITE);
        goLiveBtn.setTextSize(18);
        goLiveBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        goLiveBtn.setBackgroundColor(Color.parseColor("#2563eb"));
        goLiveBtn.setPadding(0, 32, 0, 32);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        goLiveBtn.setLayoutParams(btnParams);

        goLiveBtn.setOnClickListener(v -> {
            String key = streamKeyInput.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter your stream key", Toast.LENGTH_SHORT).show();
                return;
            }
            // Save the key
            prefs.edit().putString(KEY_STREAM, key).apply();
            // Check permissions then launch stream
            checkPermissionsAndStream(key);
        });

        root.addView(logo);
        root.addView(tagline);
        root.addView(label);
        root.addView(streamKeyInput);
        root.addView(serverLabel);
        root.addView(serverVal);
        root.addView(goLiveBtn);

        setContentView(root);
    }

    private void checkPermissionsAndStream(String key) {
        boolean camOk  = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)       == PackageManager.PERMISSION_GRANTED;
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        if (!camOk || !audioOk) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    PERM_REQUEST);
            // Store key for after permission grant
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STREAM, key).apply();
        } else {
            launchStream(key);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERM_REQUEST) {
            boolean allGranted = true;
            for (int r : results) { if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; } }
            if (allGranted) {
                String key = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STREAM, "");
                if (!key.isEmpty()) launchStream(key);
            } else {
                Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchStream(String key) {
        String url = RTMP_SERVER + "/" + key;
        android.util.Log.d("StreamPal", "Launching StreamActivity with url=" + url);
        Intent intent = new Intent(this, StreamActivity.class);
        intent.putExtra("url",     url);
        intent.putExtra("width",   1280);
        intent.putExtra("height",  720);
        intent.putExtra("fps",     30);
        intent.putExtra("bitrate", 1500 * 1024);
        startActivity(intent);
    }
}
