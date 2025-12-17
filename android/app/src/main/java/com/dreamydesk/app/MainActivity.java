package com.dreamydesk.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.dreamydesk.app.plugins.WPUtils;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(WPUtils.class);
    }
}
