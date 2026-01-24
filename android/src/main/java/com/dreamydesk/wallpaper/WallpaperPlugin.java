package com.dreamydesk.wallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@CapacitorPlugin(name = "WallpaperPlugin")
public class WallpaperPlugin extends Plugin {

    private static final String TAG = "WallpaperPlugin";

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "✅ WallpaperPlugin loaded");
    }

    // =====================================================
    // STATIC WALLPAPER (EXPECT RESTART ON ANDROID 12+)
    // =====================================================

    @PluginMethod
    public void setWallpaper(PluginCall call) {
        String path = call.getString("path");
        String target = call.getString("target", "home"); // home | lock | both

        if (path == null || path.isEmpty()) {
            call.reject("Path required");
            return;
        }

        new Thread(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    call.reject("File not found");
                    return;
                }

                WallpaperManager wm =
                        WallpaperManager.getInstance(getContext());

                InputStream is = new FileInputStream(file);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    switch (target) {
                        case "lock":
                            wm.setStream(is, null, true,
                                    WallpaperManager.FLAG_LOCK);
                            break;
                        case "both":
                            wm.setStream(is);
                            break;
                        case "home":
                        default:
                            wm.setStream(is, null, true,
                                    WallpaperManager.FLAG_SYSTEM);
                            break;
                    }
                } else {
                    wm.setStream(is);
                }

                is.close();

                JSObject res = new JSObject();
                res.put("success", true);
                call.resolve(res);

                Log.d(TAG, "✅ Static wallpaper set");

            } catch (Exception e) {
                Log.e(TAG, "❌ Static wallpaper failed", e);
                call.reject(e.getMessage());
            }
        }).start();
    }

    // =====================================================
    // LIVE WALLPAPER (NO RESTART – KEEP YOUR LOGIC)
    // =====================================================

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
        String videoPath = call.getString("path");

        if (videoPath == null || videoPath.isEmpty()) {
            call.reject("Video path required");
            return;
        }

        // Save path for LiveWallpaperService
        getContext()
            .getSharedPreferences("LiveWallpaper", Context.MODE_PRIVATE)
            .edit()
            .putString("video_path", videoPath)
            .apply();

        try {
            Intent intent =
                new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);

            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(
                    getContext(),
                    LiveWallpaperService.class
                )
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);

            JSObject res = new JSObject();
            res.put("success", true);
            call.resolve(res);

            Log.d(TAG, "🎥 Live wallpaper picker opened");

        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
