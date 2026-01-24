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
        Log.d(TAG, "WallpaperPlugin loaded");
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", true);
        call.resolve(result);
    }

    // ================= STATIC WALLPAPER (FIXED) =================

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        launchWallpaperActivity(call, "home");
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        launchWallpaperActivity(call, "lock");
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        launchWallpaperActivity(call, "both");
    }

    private void launchWallpaperActivity(PluginCall call, String mode) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        Intent intent = new Intent(getContext(), WallpaperApplyActivity.class);
        intent.putExtra("imageUrl", url);
        intent.putExtra("mode", mode);

        // 🔑 critical: isolate from Capacitor process
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            getContext().startActivity(intent);

            JSObject result = new JSObject();
            result.put("started", true);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Failed to start wallpaper activity: " + e.getMessage());
        }
    }

    // ================= LIVE WALLPAPER (UNCHANGED) =================

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
        Log.d(TAG, "setLiveWallpaper called");

        String videoUrl = call.getString("url");
        String type = call.getString("type", "gif");

        if (videoUrl == null || videoUrl.isEmpty()) {
            call.reject("Must provide video URL");
            return;
        }

        if (videoUrl.startsWith("file://")) {
            File videoFile = new File(Uri.parse(videoUrl).getPath());
            getContext().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("live_wallpaper_path", videoFile.getAbsolutePath())
                    .putString("live_wallpaper_type", type)
                    .apply();

            openNativeLiveWallpaperPicker(call);
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(new DownloadVideoCallable(videoUrl, type));

        try {
            if (future.get()) {
                openNativeLiveWallpaperPicker(call);
            } else {
                call.reject("Video download failed");
            }
        } catch (InterruptedException | ExecutionException e) {
            call.reject(e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private void openNativeLiveWallpaperPicker(PluginCall call) {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(getContext(), LiveWallpaperService.class)
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            getContext().startActivity(intent);

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    // ================= VIDEO DOWNLOAD (UNCHANGED) =================

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
                java.net.URL videoUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) videoUrl.openConnection();

                conn.connect();

                File file = new File(getContext().getCacheDir(), "live_wallpaper." + type);
                java.io.InputStream in = conn.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(file);

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                in.close();
                out.close();

                getContext().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("live_wallpaper_path", file.getAbsolutePath())
                        .putString("live_wallpaper_type", type)
                        .apply();

                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
