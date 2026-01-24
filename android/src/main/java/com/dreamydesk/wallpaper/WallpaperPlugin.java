package com.dreamydesk.wallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
    // STATIC WALLPAPER - HOME SCREEN
    // =====================================================
    
    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        setWallpaperInternal(call, WallpaperManager.FLAG_SYSTEM);
    }

    // =====================================================
    // STATIC WALLPAPER - LOCK SCREEN
    // =====================================================
    
    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        setWallpaperInternal(call, WallpaperManager.FLAG_LOCK);
    }

    // =====================================================
    // STATIC WALLPAPER - BOTH SCREENS
    // =====================================================
    
    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        setWallpaperInternal(call, -1); // Both
    }

    // =====================================================
    // INTERNAL WALLPAPER SETTER
    // =====================================================
    
    private void setWallpaperInternal(PluginCall call, int flag) {
        String path = call.getString("path");

        if (path == null || path.isEmpty()) {
            Log.e(TAG, "❌ Path is null or empty");
            call.reject("Path required");
            return;
        }

        Log.d(TAG, "📂 Setting wallpaper from: " + path);

        new Thread(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    Log.e(TAG, "❌ File not found: " + path);
                    // Use Handler instead of getActivity()
                    new Handler(Looper.getMainLooper()).post(() -> {
                        call.reject("File not found");
                    });
                    return;
                }

                Log.d(TAG, "✅ File exists, size: " + file.length() + " bytes");

                WallpaperManager wm = WallpaperManager.getInstance(getContext());
                InputStream is = new FileInputStream(file);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && flag != -1) {
                    // Android 7+ with specific flag
                    wm.setStream(is, null, true, flag);
                    Log.d(TAG, "✅ Wallpaper set with flag: " + flag);
                } else {
                    // Both screens or older Android
                    wm.setStream(is);
                    Log.d(TAG, "✅ Wallpaper set (both screens)");
                }

                is.close();

                JSObject res = new JSObject();
                res.put("success", true);
                
                // Use Handler instead of getActivity()
                new Handler(Looper.getMainLooper()).post(() -> {
                    call.resolve(res);
                });

                Log.d(TAG, "✅ SUCCESS");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage(), e);
                // Use Handler instead of getActivity()
                new Handler(Looper.getMainLooper()).post(() -> {
                    call.reject("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    // =====================================================
    // LIVE WALLPAPER
    // =====================================================
    
    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
        String videoPath = call.getString("path");

        if (videoPath == null || videoPath.isEmpty()) {
            call.reject("Video path required");
            return;
        }

        Log.d(TAG, "🎥 Setting live wallpaper: " + videoPath);

        // Save path for LiveWallpaperService
        getContext()
            .getSharedPreferences("LiveWallpaper", Context.MODE_PRIVATE)
            .edit()
            .putString("video_path", videoPath)
            .apply();

        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);

            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(getContext(), LiveWallpaperService.class)
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);

            JSObject res = new JSObject();
            res.put("success", true);
            call.resolve(res);

            Log.d(TAG, "✅ Live wallpaper picker opened");

        } catch (Exception e) {
            Log.e(TAG, "❌ Live wallpaper failed", e);
            call.reject(e.getMessage());
        }
    }
}
