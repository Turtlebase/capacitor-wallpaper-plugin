package com.dreamydesk.app;

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
        Log.d(TAG, "✅ WallpaperPlugin loaded (Gallery-style, SAFE)");
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject res = new JSObject();
        res.put("available", true);
        call.resolve(res);
    }

    // =========================================================
    // ✅ STATIC WALLPAPER — FILE PATH ONLY (NO RESTART)
    // =========================================================

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        applyStatic(call, "home");
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        applyStatic(call, "lock");
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        applyStatic(call, "both");
    }

    private void applyStatic(PluginCall call, String target) {
        String path = call.getString("path");

        if (path == null || path.isEmpty()) {
            call.reject("Local file path required");
            return;
        }

        try {
            File imageFile = new File(path);
            if (!imageFile.exists()) {
                call.reject("File does not exist");
                return;
            }

            WallpaperManager wm = WallpaperManager.getInstance(getContext());
            InputStream is = new FileInputStream(imageFile);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ("home".equals(target)) {
                    wm.setStream(is, null, false, WallpaperManager.FLAG_SYSTEM);
                } else if ("lock".equals(target)) {
                    wm.setStream(is, null, false, WallpaperManager.FLAG_LOCK);
                } else {
                    wm.setStream(is, null, false, WallpaperManager.FLAG_SYSTEM);
                    wm.setStream(is, null, false, WallpaperManager.FLAG_LOCK);
                }
            } else {
                wm.setStream(is);
            }

            is.close();

            JSObject res = new JSObject();
            res.put("success", true);
            call.resolve(res);

            Log.d(TAG, "✅ Static wallpaper set (NO restart)");

        } catch (Exception e) {
            Log.e(TAG, "❌ Static wallpaper failed", e);
            call.reject(e.getMessage());
        }
    }

    // =========================================================
    // 🎥 LIVE WALLPAPER — PRESERVED EXACTLY
    // =========================================================

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
        String videoUrl = call.getString("url");
        String type = call.getString("type", "gif");

        if (videoUrl == null || videoUrl.isEmpty()) {
            call.reject("Must provide video URL");
            return;
        }

        // Already downloaded
        if (videoUrl.startsWith("file://")) {
            File videoFile = new File(Uri.parse(videoUrl).getPath());
            saveLiveWallpaperPrefs(videoFile.getAbsolutePath(), type);
            openLiveWallpaperPicker(call);
            return;
        }

        // Download to cache (same as before)
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(new DownloadVideoCallable(videoUrl, type));

        try {
            if (future.get()) {
                openLiveWallpaperPicker(call);
            } else {
                call.reject("Video download failed");
            }
        } catch (InterruptedException | ExecutionException e) {
            call.reject(e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private void openLiveWallpaperPicker(PluginCall call) {
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

    private void saveLiveWallpaperPrefs(String path, String type) {
        getContext()
                .getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("live_wallpaper_path", path)
                .putString("live_wallpaper_type", type)
                .apply();
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

                saveLiveWallpaperPrefs(file.getAbsolutePath(), type);
                return true;

            } catch (Exception e) {
                Log.e(TAG, "❌ Live wallpaper download failed", e);
                return false;
            }
        }
    }
}
