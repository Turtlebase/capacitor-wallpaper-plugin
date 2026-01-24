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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@CapacitorPlugin(name = "WallpaperPlugin")
public class WallpaperPlugin extends Plugin {

    private static final String TAG = "WallpaperPlugin";

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "✅ WallpaperPlugin loaded (backward compatible)");
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject res = new JSObject();
        res.put("available", true);
        call.resolve(res);
    }

    // =====================================================
    // ✅ STATIC WALLPAPER (PATH + URL SAFE)
    // =====================================================

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        applyStatic(call, WallpaperManager.FLAG_SYSTEM);
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        applyStatic(call, WallpaperManager.FLAG_LOCK);
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        applyStatic(call, -1); // both
    }

    private void applyStatic(PluginCall call, int flag) {

        // 🔥 CRITICAL FIX: accept BOTH path and url
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            path = call.getString("url");
        }

        if (path == null || path.isEmpty()) {
            Log.e(TAG, "❌ No path or url provided");
            call.reject("Path or URL required");
            return;
        }

        try {
            File imageFile = new File(path);
            if (!imageFile.exists()) {
                call.reject("File not found: " + path);
                return;
            }

            WallpaperManager wm = WallpaperManager.getInstance(getContext());
            InputStream is = new FileInputStream(imageFile);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && flag != -1) {
                wm.setStream(is, null, false, flag);
            } else {
                wm.setStream(is);
            }

            is.close();

            JSObject res = new JSObject();
            res.put("success", true);
            call.resolve(res);

            Log.d(TAG, "✅ Static wallpaper applied");

        } catch (Exception e) {
            Log.e(TAG, "❌ Static wallpaper failed", e);
            call.reject(e.getMessage());
        }
    }

    // =====================================================
    // 🎥 LIVE WALLPAPER (UNCHANGED LOGIC, BUT SAFE INPUT)
    // =====================================================

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {

        // 🔥 accept BOTH path and url
        String videoPath = call.getString("path");
        String videoUrl  = call.getString("url");
        String type = call.getString("type", "gif");

        File videoFile = null;

        if (videoPath != null && !videoPath.isEmpty()) {
            videoFile = new File(videoPath);
        }

        if (videoFile != null && videoFile.exists()) {
            saveLivePrefs(videoFile.getAbsolutePath(), type);
            openLivePicker(call);
            return;
        }

        if (videoUrl == null || videoUrl.isEmpty()) {
            call.reject("Path or URL required");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(new DownloadVideoCallable(videoUrl, type));

        try {
            if (future.get()) {
                openLivePicker(call);
            } else {
                call.reject("Video download failed");
            }
        } catch (Exception e) {
            call.reject(e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private void saveLivePrefs(String path, String type) {
        getContext().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("live_wallpaper_path", path)
                .putString("live_wallpaper_type", type)
                .apply();
    }

    private void openLivePicker(PluginCall call) {
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

        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    private class DownloadVideoCallable implements Callable<Boolean> {
        private final String url;
        private final String type;

        DownloadVideoCallable(String url, String type) {
            this.url = url;
            this.type = type;
        }

        @Override
        public Boolean call() {
            try {
                URL videoUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) videoUrl.openConnection();
                conn.connect();

                File file = new File(getContext().getCacheDir(), "live_wallpaper." + type);
                InputStream in = conn.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(file);

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                in.close();
                out.close();
                conn.disconnect();

                saveLivePrefs(file.getAbsolutePath(), type);
                return true;

            } catch (Exception e) {
                Log.e(TAG, "❌ Live wallpaper download failed", e);
                return false;
            }
        }
    }
}
