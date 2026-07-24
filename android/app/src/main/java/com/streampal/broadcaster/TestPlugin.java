package com.streampal.broadcaster;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "TestPlugin")
public class TestPlugin extends Plugin {
    @PluginMethod
    public void ping(PluginCall call) {
        call.resolve();
    }
}
