package com.streampal.broadcaster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.pedro.common.ConnectChecker;
import com.pedro.library.rtmp.RtmpCamera2;
import com.pedro.library.view.OpenGlView;

import java.util.Timer;
import java.util.TimerTask;

public class StreamActivity extends AppCompatActivity
        implements ConnectChecker, SurfaceHolder.Callback {

    private RtmpCamera2 rtmpCamera;
    private String      rtmpUrl;
    private int         width, height, fps, bitrate;
    private boolean     muted  = false;
    private boolean     isLive = false;

    private Timer   statsTimer;
    private Handler mainHandler;
    private BroadcastReceiver controlReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mainHandler = new Handler(Looper.getMainLooper());

        Intent i = getIntent();
        rtmpUrl = i.getStringExtra("url");
        width   = i.getIntExtra("width",   1280);
        height  = i.getIntExtra("height",  720);
        fps     = i.getIntExtra("fps",     30);
        bitrate = i.getIntExtra("bitrate", 1500 * 1024);

        // Create OpenGlView programmatically to avoid R.layout issues
        OpenGlView openGlView = new OpenGlView(this);
        setContentView(openGlView);
        openGlView.getHolder().addCallback(this);

        rtmpCamera = new RtmpCamera2(openGlView, this);
        registerControlReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStream();
        if (controlReceiver != null) unregisterReceiver(controlReceiver);
        if (statsTimer != null) statsTimer.cancel();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreviewAndStream();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (rtmpCamera.isStreaming()) rtmpCamera.stopStream();
        if (rtmpCamera.isOnPreview()) rtmpCamera.stopPreview();
    }

    private void startPreviewAndStream() {
        try {
            if (!rtmpCamera.isOnPreview()) {
                rtmpCamera.startPreview(width, height);
            }
            if (rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(width, height, fps, bitrate, 0)) {
                rtmpCamera.startStream(rtmpUrl);
                isLive = true;
                startStatsTimer();
            } else {
                sendEvent(RtmpPlugin.ACTION_FAILED, "Prepare failed");
            }
        } catch (Exception e) {
            sendEvent(RtmpPlugin.ACTION_FAILED, e.getMessage());
        }
    }

    private void stopStream() {
        isLive = false;
        if (statsTimer != null) { statsTimer.cancel(); statsTimer = null; }
        if (rtmpCamera != null) {
            if (rtmpCamera.isStreaming()) rtmpCamera.stopStream();
            if (rtmpCamera.isOnPreview()) rtmpCamera.stopPreview();
        }
    }

    @Override public void onConnectionStarted(String url) {}
    @Override public void onConnectionSuccess() { sendEvent(RtmpPlugin.ACTION_CONNECTED, null); }
    @Override public void onConnectionFailed(String reason) {
        sendEvent(RtmpPlugin.ACTION_FAILED, reason);
        mainHandler.post(this::finish);
    }
    @Override public void onDisconnect() {
        sendEvent(RtmpPlugin.ACTION_DISCONNECTED, null);
        mainHandler.post(this::finish);
    }
    @Override public void onAuthError() { sendEvent(RtmpPlugin.ACTION_FAILED, "Auth error"); }
    @Override public void onAuthSuccess() {}
    @Override public void onNewBitrate(long bitrate) {}

    private void startStatsTimer() {
        statsTimer = new Timer();
        statsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isLive) return;
                Intent intent = new Intent(RtmpPlugin.ACTION_STATS);
                intent.putExtra("bitrate", rtmpCamera.getBitrate());
                intent.putExtra("fps",     30);
                intent.putExtra("dropped", 0);
                sendBroadcast(intent);
            }
        }, 2000, 2000);
    }

    private void registerControlReceiver() {
        controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (RtmpPlugin.ACTION_STOP.equals(action)) {
                    stopStream(); finish();
                } else if (RtmpPlugin.ACTION_FLIP.equals(action)) {
                    rtmpCamera.switchCamera();
                } else if (RtmpPlugin.ACTION_MUTE.equals(action)) {
                    muted = !muted;
                    if (muted) rtmpCamera.disableAudio();
                    else       rtmpCamera.enableAudio();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(RtmpPlugin.ACTION_STOP);
        filter.addAction(RtmpPlugin.ACTION_FLIP);
        filter.addAction(RtmpPlugin.ACTION_MUTE);
        registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void sendEvent(String action, String reason) {
        Intent intent = new Intent(action);
        if (reason != null) intent.putExtra("reason", reason);
        sendBroadcast(intent);
    }
}