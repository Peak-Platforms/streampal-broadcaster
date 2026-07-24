package com.streampal.broadcaster;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RtmpPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
