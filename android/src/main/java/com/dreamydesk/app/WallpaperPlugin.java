package com.dreamydesk.app;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Capacitor Wallpaper Plugin
 * 
 * Static wallpapers: Set directly by the app
 * Live wallpapers: Download video/GIF, then open native Android picker for user selection
 * 
 * @version 1.0.0
 */
@CapacitorPlugin(name = "WallpaperPlugin")
public class WallpaperPlugin extends Plugin {

    private static final String TAG = "WallpaperPlugin";
    private Context context = null;
    private static final boolean IS_NOUGAT_OR_GREATER = Build.VERSION.SDK_INT >= 24;

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "✅ WallpaperPlugin loaded successfully!");
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", true);
        call.resolve(result);
    }

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        Log.d(TAG, "📱 setImageAsWallpaper called");
        
        context = getContext();

        String url = call.getString("url");
        
        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        Bitmap bmp = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {
            bmp = future.get();
            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            call.reject("Download failed: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        getBridge().executeOnMainThread(new SetBackgroundImageRunnable(bmp, call));
        executorService.shutdown();
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        Log.d(TAG, "📱 setImageAsLockScreen called");
        
        context = getContext();

        String url = call.getString("url");
        
        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        Bitmap bmp = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {
            bmp = future.get();
            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            call.reject("Download failed: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        getBridge().executeOnMainThread(new SetLockScreenImageRunnable(bmp, call));
        executorService.shutdown();
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        Log.d(TAG, "📱 setImageAsWallpaperAndLockScreen called");
        
        context = getContext();

        String url = call.getString("url");
        
        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        Bitmap bmp = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {
            bmp = future.get();
            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            call.reject("Download failed: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        getBridge().executeOnMainThread(new SetLockScreenAndWallpaperImageRunnable(bmp, call));
        executorService.shutdown();
    }

    /**
     * Download video/GIF from Firebase and open native Android live wallpaper picker
     * User selects the wallpaper themselves - following Android guidelines
     */
    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
        Log.d(TAG, "📱 setLiveWallpaper called");
        
        String videoUrl = call.getString("url");
        String type = call.getString("type", null);
        
        if (videoUrl == null || videoUrl.isEmpty()) {
            call.reject("Must provide video URL");
            return;
        }

        // Auto-detect type from URL if not provided
        if (type == null || type.isEmpty()) {
            String urlLower = videoUrl.toLowerCase();
            if (urlLower.contains(".mp4") || urlLower.contains("mp4?") || urlLower.contains("mp4&")) {
                type = "mp4";
                Log.d(TAG, "🔍 Auto-detected: MP4");
            } else if (urlLower.contains(".gif") || urlLower.contains("gif?") || urlLower.contains("gif&")) {
                type = "gif";
                Log.d(TAG, "🔍 Auto-detected: GIF");
            } else {
                type = "gif";
                Log.w(TAG, "⚠️ Defaulting to GIF");
            }
        }

        final String finalType = type;
        Log.d(TAG, "🎬 Downloading " + finalType.toUpperCase());

        // Download in background
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new DownloadVideoCallable(videoUrl, finalType));

        try {
            boolean success = future.get();
            if (success) {
                Log.d(TAG, "✅ Download complete - opening native picker");
                openNativeLiveWallpaperPicker(call);
            } else {
                call.reject("Failed to download video");
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            call.reject("Error: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    /**
     * Opens Android's native live wallpaper chooser
     * User can preview and select the wallpaper
     */
    private void openNativeLiveWallpaperPicker(PluginCall call) {
        try {
            Log.d(TAG, "📱 Launching native wallpaper picker");
            
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(getContext(), LiveWallpaperService.class)
            );
            
            getActivity().startActivity(intent);
            
            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
            
            Log.d(TAG, "✅ Native picker opened - user can now select wallpaper");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to open picker: " + e.getMessage());
            call.reject("Failed to open wallpaper picker: " + e.getMessage());
        }
    }

    // ================== INNER CLASSES ==================

    /**
     * Downloads image from URL
     */
    private class GetBitmapFromURLCallable implements Callable<Bitmap> {
        private String URL;

        private GetBitmapFromURLCallable(String URL) {
            this.URL = URL;
        }

        @Override
        public Bitmap call() {
            Bitmap bmp = null;
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            
            try {
                URL imageUrl = new URL(this.URL);
                connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.connect();
                
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    bmp = BitmapFactory.decodeStream(inputStream);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            return bmp;
        }
    }

    /**
     * Downloads video/GIF to cache directory
     * LiveWallpaperService will read from this location
     */
    private class DownloadVideoCallable implements Callable<Boolean> {
        private String url;
        private String type;

        private DownloadVideoCallable(String url, String type) {
            this.url = url;
            this.type = type;
        }

        @Override
        public Boolean call() {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            
            try {
                Log.d(TAG, "⬇️ Downloading " + type.toUpperCase() + " from: " + url);
                
                URL videoUrl = new URL(this.url);
                connection = (HttpURLConnection) videoUrl.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(60000);
                connection.setReadTimeout(60000);
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "❌ HTTP error: " + connection.getResponseCode());
                    return false;
                }
                
                // Save to app's cache directory
                File cacheDir = getContext().getCacheDir();
                String fileName = "live_wallpaper." + type;
                File videoFile = new File(cacheDir, fileName);
                
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(videoFile);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                outputStream.flush();
                
                Log.d(TAG, "✅ Downloaded " + totalBytes + " bytes");
                Log.d(TAG, "💾 Saved to: " + videoFile.getAbsolutePath());
                
                // Save path for LiveWallpaperService to use
                getContext().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("live_wallpaper_path", videoFile.getAbsolutePath())
                    .putString("live_wallpaper_type", type)
                    .apply();
                
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Download error: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class SetBackgroundImageRunnable implements Runnable {
        private Bitmap bmp;
        private PluginCall callbackContext;

        private SetBackgroundImageRunnable(Bitmap bmp, PluginCall callbackContext) {
            this.bmp = bmp;
            this.callbackContext = callbackContext;
        }

        @Override
        public void run() {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            try {
                wallpaperManager.setBitmap(bmp);
                JSObject result = new JSObject();
                result.put("success", true);
                callbackContext.resolve(result);
            } catch (IOException e) {
                callbackContext.reject(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private class SetLockScreenImageRunnable implements Runnable {
        private Bitmap bmp;
        private PluginCall callbackContext;

        private SetLockScreenImageRunnable(Bitmap bmp, PluginCall callbackContext) {
            this.bmp = bmp;
            this.callbackContext = callbackContext;
        }

        @Override
        public void run() {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            try {
                if (IS_NOUGAT_OR_GREATER) {
                    wallpaperManager.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
                } else {
                    wallpaperManager.setBitmap(bmp);
                }
                JSObject result = new JSObject();
                result.put("success", true);
                callbackContext.resolve(result);
            } catch (IOException e) {
                callbackContext.reject(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private class SetLockScreenAndWallpaperImageRunnable implements Runnable {
        private Bitmap bmp;
        private PluginCall callbackContext;

        private SetLockScreenAndWallpaperImageRunnable(Bitmap bmp, PluginCall callbackContext) {
            this.bmp = bmp;
            this.callbackContext = callbackContext;
        }

        @Override
        public void run() {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            try {
                wallpaperManager.setBitmap(bmp);
                if (IS_NOUGAT_OR_GREATER) {
                    wallpaperManager.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
                }
                JSObject result = new JSObject();
                result.put("success", true);
                callbackContext.resolve(result);
            } catch (IOException e) {
                callbackContext.reject(e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
