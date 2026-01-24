package com.dreamydesk.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class WallpaperApplyActivity extends Activity {

    private static final String TAG = "WallpaperApplyActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(this::apply).start();
    }

    private void apply() {
        try {
            String path = getIntent().getStringExtra("path");
            int flag = getIntent().getIntExtra("flag", -1);

            if (path == null) {
                finish();
                return;
            }

            File imageFile = new File(path);
            if (!imageFile.exists()) {
                finish();
                return;
            }

            WallpaperManager wm = WallpaperManager.getInstance(this);
            InputStream is = new FileInputStream(imageFile);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && flag != -1) {
                wm.setStream(is, null, false, flag);
            } else {
                wm.setStream(is);
            }

            is.close();
            Log.d(TAG, "✅ Wallpaper applied successfully");

        } catch (Throwable t) {
            Log.e(TAG, "❌ Failed to apply wallpaper", t);
        } finally {
            finish(); // 🔑 silent exit
        }
    }
}
