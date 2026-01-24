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
        Log.d(TAG, "✅ WallpaperPlugin loaded (Gallery-style)");
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject res = new JSObject();
        res.put("available", true);
        call.resolve(res);
    }

    // ============================================================
    // ✅ STATIC WALLPAPER — GALLERY / FILES STYLE (NO RESTART)
    // ============================================================

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        applyStaticWallpaper(call, WallpaperManager.FLAG_SYSTEM);
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        applyStaticWallpaper(call, WallpaperManager.FLAG_LOCK);
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        applyStaticWallpaper(call, -1); // both
    }

    private void applyStaticWallpaper(PluginCall call, int flag) {
        String url = call.getString("url");

        if (url == null || url.isEmpty()) {
            call.reject("URL required");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<File> future = executor.submit(new DownloadToCacheTask(url));

        try {
            File imageFile = future.get();
            if (imageFile == null || !imageFile.exists()) {
                call.reject("Download failed");
                executor.shutdown();
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

            Log.d(TAG, "✅ Wallpaper applied using setStream() — NO restart");

        } catch (Exception e) {
            call.reject(e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    // ============================================================
    // 📥 DOWNLOAD IMAGE → CACHE (LIKE GALLERY)
    // ============================================================

    private class DownloadToCacheTask implements Callable<File> {
        private final String imageUrl;

        DownloadToCacheTask(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        @Override
        public File call() {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                File outFile = new File(
                        getContext().getCacheDir(),
                        "wallpaper_temp.jpg"
                );

                InputStream in = conn.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                in.close();
                out.close();
                conn.disconnect();

                return outFile;

            } catch (Exception e) {
                Log.e(TAG, "❌ Image download failed", e);
                return null;
            }
        }
    }

    // ============================================================
    // 🎥 LIVE WALLPAPER — KEEP AS YOU HAD (UNCHANGED)
    // ============================================================

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
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
