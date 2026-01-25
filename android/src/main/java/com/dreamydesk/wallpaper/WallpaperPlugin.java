package com.dreamydesk.wallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

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
        setWallpaperWithIntent(call, "home");
    }

    // =====================================================
    // STATIC WALLPAPER - LOCK SCREEN
    // =====================================================
    
    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        setWallpaperWithIntent(call, "lock");
    }

    // =====================================================
    // STATIC WALLPAPER - BOTH SCREENS
    // =====================================================
    
    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        setWallpaperWithIntent(call, "both");
    }

    // =====================================================
    // PRIMARY METHOD: INTENT CHOOSER (NO VISIBLE RESTART)
    // =====================================================
    
    private void setWallpaperWithIntent(PluginCall call, String screen) {
        String path = call.getString("path");

        if (path == null || path.isEmpty()) {
            Log.e(TAG, "❌ Path is null or empty");
            call.reject("Path required");
            return;
        }

        Log.d(TAG, "🎨 Intent method - Screen: " + screen + " - Path: " + path);

        try {
            File file = new File(path);
            if (!file.exists()) {
                Log.e(TAG, "❌ File not found: " + path);
                call.reject("File not found");
                return;
            }

            Log.d(TAG, "✅ File exists, size: " + file.length() + " bytes");

            // Create FileProvider URI (or fallback to file://)
            String authority = getContext().getPackageName() + ".fileprovider";
            Uri contentUri;
            
            try {
                contentUri = FileProvider.getUriForFile(getContext(), authority, file);
                Log.d(TAG, "✅ Using FileProvider URI: " + contentUri);
            } catch (Exception e) {
                contentUri = Uri.fromFile(file);
                Log.w(TAG, "⚠️ FileProvider not available, using file:// URI: " + contentUri);
            }

            // Create Intent
            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(contentUri, "image/*");
            intent.putExtra("mimeType", "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Launch system chooser (app stays alive - NO RESTART)
            getContext().startActivity(Intent.createChooser(intent, "Set as Wallpaper"));

            JSObject res = new JSObject();
            res.put("success", true);
            call.resolve(res);

            Log.d(TAG, "✅ Chooser launched - app stays alive (NO RESTART)");

        } catch (Exception e) {
            Log.e(TAG, "❌ Intent method failed: " + e.getMessage(), e);
            
            // Fallback to direct method
            Log.w(TAG, "⚠️ Falling back to direct method");
            setWallpaperDirect(call, screen);
        }
    }

    // =====================================================
    // FALLBACK METHOD: DIRECT SET (MAY RESTART)
    // =====================================================
    
    private void setWallpaperDirect(PluginCall call, String screen) {
        String path = call.getString("path");

        if (path == null || path.isEmpty()) {
            call.reject("Path required");
            return;
        }

        Log.d(TAG, "📱 Direct fallback - Screen: " + screen);

        // Determine flag
        int flag;
        switch (screen) {
            case "home":
                flag = WallpaperManager.FLAG_SYSTEM;
                break;
            case "lock":
                flag = WallpaperManager.FLAG_LOCK;
                break;
            default:
                flag = -1; // Both
                break;
        }

        setWallpaperInternal(call, flag);
    }

    // =====================================================
    // INTERNAL WALLPAPER SETTER (DIRECT METHOD)
    // =====================================================
    
    private void setWallpaperInternal(PluginCall call, int flag) {
        String path = call.getString("path");

        if (path == null || path.isEmpty()) {
            Log.e(TAG, "❌ Path is null or empty");
            call.reject("Path required");
            return;
        }

        Log.d(TAG, "📂 Direct method - Path: " + path + " - Flag: " + flag);

        new Thread(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    Log.e(TAG, "❌ File not found: " + path);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        call.reject("File not found");
                    });
                    return;
                }

                Log.d(TAG, "✅ File exists, size: " + file.length() + " bytes");

                WallpaperManager wm = WallpaperManager.getInstance(getContext());
                InputStream is = new FileInputStream(file);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && flag != -1) {
                    wm.setStream(is, null, true, flag);
                    Log.d(TAG, "✅ Wallpaper set with flag: " + flag);
                } else {
                    wm.setStream(is);
                    Log.d(TAG, "✅ Wallpaper set (both screens)");
                }

                is.close();

                JSObject res = new JSObject();
                res.put("success", true);
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    call.resolve(res);
                });

                Log.d(TAG, "✅ Direct method SUCCESS");

            } catch (Exception e) {
                Log.e(TAG, "❌ Direct method error: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    call.reject("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    // =====================================================
    // LIVE WALLPAPER (VIDEO)
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
