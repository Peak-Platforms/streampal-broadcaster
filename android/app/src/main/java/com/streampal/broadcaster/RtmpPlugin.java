package com.streampal.broadcaster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "RtmpStream")
public class RtmpPlugin extends Plugin {

    public static final String ACTION_STOP         = "com.streampal.STOP_STREAM";
    public static final String ACTION_FLIP         = "com.streampal.FLIP_CAMERA";
    public static final String ACTION_MUTE         = "com.streampal.MUTE_AUDIO";
    public static final String ACTION_TOGGLE_RECORD = "com.streampal.TOGGLE_RECORD";
    public static final String ACTION_RECORD_STATE  = "com.streampal.RECORD_STATE";
    public static final String ACTION_CONNECTED    = "com.streampal.CONNECTED";
    public static final String ACTION_FAILED       = "com.streampal.FAILED";
    public static final String ACTION_DISCONNECTED = "com.streampal.DISCONNECTED";
    public static final String ACTION_STATS        = "com.streampal.STATS";

    private BroadcastReceiver eventReceiver;
    private boolean receiverRegistered = false;

    @PluginMethod
    public void startStream(PluginCall call) {
        ensureReceiverRegistered();
        String url     = call.getString("url");
        int    width   = call.getInt("width",   1280);
        int    height  = call.getInt("height",  720);
        int    fps     = call.getInt("fps",     30);
        int    bitrate = call.getInt("bitrate", 1500 * 1024);

        if (url == null || url.isEmpty()) {
            call.reject("RTMP URL required");
            return;
        }

        Intent intent = new Intent(getContext(), StreamActivity.class);
        intent.putExtra("url",     url);
        intent.putExtra("width",   width);
        intent.putExtra("height",  height);
        intent.putExtra("fps",     fps);
        intent.putExtra("bitrate", bitrate);
        getContext().startActivity(intent);
        call.resolve();
    }

    @PluginMethod
    public void stopStream(PluginCall call) {
        getContext().sendBroadcast(new Intent(ACTION_STOP));
        call.resolve();
    }

    @PluginMethod
    public void flipCamera(PluginCall call) {
        getContext().sendBroadcast(new Intent(ACTION_FLIP));
        call.resolve();
    }

    @PluginMethod
    public void muteAudio(PluginCall call) {
        getContext().sendBroadcast(new Intent(ACTION_MUTE));
        call.resolve();
    }

    @PluginMethod
    public void toggleRecord(PluginCall call) {
        getContext().sendBroadcast(new Intent(ACTION_TOGGLE_RECORD));
        call.resolve();
    }

    private void ensureReceiverRegistered() {
        if (receiverRegistered) return;
        receiverRegistered = true;
        eventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                JSObject data = new JSObject();
                if (ACTION_CONNECTED.equals(action)) {
                    data.put("status", "connected");
                    notifyListeners("streamEvent", data);
                } else if (ACTION_FAILED.equals(action)) {
                    data.put("status", "failed");
                    data.put("reason", intent.getStringExtra("reason"));
                    notifyListeners("streamEvent", data);
                } else if (ACTION_DISCONNECTED.equals(action)) {
                    data.put("status", "disconnected");
                    notifyListeners("streamEvent", data);
                } else if (ACTION_STATS.equals(action)) {
                    data.put("status", "stats");
                    data.put("bitrate",  intent.getLongExtra("bitrate", 0));
                    data.put("fps",      intent.getIntExtra("fps", 0));
                    data.put("dropped",  intent.getIntExtra("dropped", 0));
                    notifyListeners("streamEvent", data);
                } else if (ACTION_RECORD_STATE.equals(action)) {
                    data.put("status", "recordState");
                    data.put("recording", intent.getBooleanExtra("recording", false));
                    notifyListeners("streamEvent", data);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CONNECTED);
        filter.addAction(ACTION_FAILED);
        filter.addAction(ACTION_DISCONNECTED);
        filter.addAction(ACTION_STATS);
        filter.addAction(ACTION_RECORD_STATE);
        ContextCompat.registerReceiver(getContext(), eventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void handleOnDestroy() {
        if (eventReceiver != null) {
            getContext().unregisterReceiver(eventReceiver);
        }
    }
}