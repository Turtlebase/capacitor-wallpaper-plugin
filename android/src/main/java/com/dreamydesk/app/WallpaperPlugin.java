package com.dreamydesk.app;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.net.Uri;

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
 * Static wallpapers: Set directly by the app (no restart, instant like Zedge)
 * Live wallpapers: Download video/GIF, then open native Android picker for user selection
 * 
 * @version 1.4.0 - Cover+centre-crop for tablets, two-pass inSampleSize decode, background thread apply, main-thread callbacks
 */
@CapacitorPlugin(name = "WallpaperPlugin")
public class WallpaperPlugin extends Plugin {

    private static final String TAG = "WallpaperPlugin";
    private Context context = null;
    private static final boolean IS_NOUGAT_OR_GREATER = Build.VERSION.SDK_INT >= 24;
    // ✅ PATCH 1: Global single-thread executor — avoids spawning many threads
    private static final ExecutorService wallpaperExecutor = Executors.newSingleThreadExecutor();

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

            // ✅ PATCH 3: Resize bitmap to screen dimensions before setting
            if (bmp != null) {
                bmp = resizeBitmapToScreen(bmp);
            }

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

        // ✅ PATCH 4: Apply wallpaper on background thread, not UI thread
        wallpaperExecutor.execute(new SetBackgroundImageRunnable(bmp, call));
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

            // ✅ PATCH 3: Resize bitmap to screen dimensions before setting
            if (bmp != null) {
                bmp = resizeBitmapToScreen(bmp);
            }

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

        // ✅ PATCH 4: Apply wallpaper on background thread, not UI thread
        wallpaperExecutor.execute(new SetLockScreenImageRunnable(bmp, call));
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

            // ✅ PATCH 3: Resize bitmap to screen dimensions before setting
            if (bmp != null) {
                bmp = resizeBitmapToScreen(bmp);
            }

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

        // ✅ PATCH 4: Apply wallpaper on background thread, not UI thread
        wallpaperExecutor.execute(new SetLockScreenAndWallpaperImageRunnable(bmp, call));
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

        // Check if the URL is a local file path
        if (videoUrl.startsWith("file://")) {
            Log.d(TAG, "🔍 Detected local file URI. Skipping download.");
            try {
                // Directly use the local file path
                File videoFile = new File(Uri.parse(videoUrl).getPath());
                getContext().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("live_wallpaper_path", videoFile.getAbsolutePath())
                    .putString("live_wallpaper_type", type)
                    .putLong("wallpaper_timestamp", System.currentTimeMillis())
                    .apply();

                Log.d(TAG, "✅ Local file path set for LiveWallpaperService: " + videoFile.getAbsolutePath());
                openNativeLiveWallpaperPicker(call);
            } catch (Exception e) {
                Log.e(TAG, "❌ Error handling local file URI: " + e.getMessage());
                call.reject("Error handling local file: " + e.getMessage());
            }
            return;
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
            
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NO_ANIMATION |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            getContext().startActivity(intent);
            
            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
            
            Log.d(TAG, "✅ Native picker opened - user can now select wallpaper");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to open picker: " + e.getMessage());
            call.reject("Failed to open wallpaper picker: " + e.getMessage());
        }
    }

    // ================== HELPER METHODS ==================

    /**
     * ✅ PATCH 2 (v1.4.0 — cover + centre-crop):
     *
     * Old approach: createScaledBitmap(w, h) → stretches the image to fill the screen exactly.
     * That distorts aspect ratio and looks terrible on tablets (wide screen, portrait image).
     *
     * New approach — equivalent to CSS "background-size: cover; background-position: center":
     *   1. Scale the image UP or DOWN uniformly so it fully COVERS the screen
     *      (the smaller dimension matches the screen; the larger overflows).
     *   2. Crop the overflow symmetrically from the centre.
     *
     * Result: image always fills the screen, never stretches, subject stays centred.
     * Works correctly on phones, tablets (landscape & portrait), and foldables.
     */
    private Bitmap resizeBitmapToScreen(Bitmap bmp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;

        int srcW = bmp.getWidth();
        int srcH = bmp.getHeight();

        // Scale factor that makes the image COVER the screen (larger of the two ratios)
        float scaleX = (float) screenW / srcW;
        float scaleY = (float) screenH / srcH;
        float scale  = Math.max(scaleX, scaleY);   // cover = max, contain = min

        // Scaled dimensions — one axis matches screen exactly, the other overflows
        int scaledW = Math.round(srcW * scale);
        int scaledH = Math.round(srcH * scale);

        // Scale the full bitmap first
        Bitmap scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true);

        // Crop the overflow symmetrically (centre-crop)
        int cropX = (scaledW - screenW) / 2;
        int cropY = (scaledH - screenH) / 2;

        Bitmap cropped = Bitmap.createBitmap(scaled, cropX, cropY, screenW, screenH);

        // Recycle the intermediate scaled bitmap if it is a new object
        if (scaled != cropped && !scaled.isRecycled()) {
            scaled.recycle();
        }

        Log.d(TAG, "📐 Cover+crop: src=" + srcW + "x" + srcH +
              " scale=" + scale +
              " scaled=" + scaledW + "x" + scaledH +
              " crop=(" + cropX + "," + cropY + ")" +
              " final=" + screenW + "x" + screenH);

        return cropped;
    }

    /**
     * ✅ PATCH 6: Calculate the largest inSampleSize that keeps the decoded bitmap
     * at or above the required screen dimensions.
     * Powers the two-pass decode in GetBitmapFromURLCallable to avoid loading
     * full 4K/8K images into RAM on low-memory devices (same technique as Zedge/Wallcraft).
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width  = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth  = width  / 2;

            // Keep halving until the result would be smaller than the screen
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth  / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        Log.d(TAG, "📐 inSampleSize calculated: " + inSampleSize +
              " (source " + width + "x" + height +
              " → target " + reqWidth + "x" + reqHeight + ")");
        return inSampleSize;
    }

    // ================== INNER CLASSES ==================

    /**
     * Downloads image from URL with optimized memory usage
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
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int reqWidth  = metrics.widthPixels;
                int reqHeight = metrics.heightPixels;

                // ✅ PATCH 6 — Pass 1: decode bounds only (zero pixels loaded into RAM)
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;

                URL imageUrl = new URL(this.URL);
                connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    BitmapFactory.decodeStream(inputStream, null, options);
                }

                // Close first connection — bounds are now known
                try { if (inputStream != null) inputStream.close(); } catch (IOException ignored) {}
                try { if (connection  != null) connection.disconnect(); } catch (Exception ignored) {}
                inputStream = null;
                connection  = null;

                // ✅ PATCH 6 — Pass 2: decode at reduced sample size (much less RAM)
                options.inSampleSize       = calculateInSampleSize(options, reqWidth, reqHeight);
                options.inJustDecodeBounds = false;
                options.inPreferredConfig  = Bitmap.Config.ARGB_8888;

                URL imageUrl2 = new URL(this.URL);
                connection = (HttpURLConnection) imageUrl2.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    bmp = BitmapFactory.decodeStream(inputStream, null, options);

                    if (bmp != null) {
                        Log.d(TAG, "✅ Bitmap loaded (sampled): " + bmp.getWidth() + "x" + bmp.getHeight() +
                              " (" + (bmp.getByteCount() / 1024 / 1024) + "MB)" +
                              " inSampleSize=" + options.inSampleSize);
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "❌ Download error: " + e.getMessage());
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "❌ Out of memory while loading bitmap: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (connection  != null) connection.disconnect();
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
                
                int responseCode = connection.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    Log.e(TAG, "❌ HTTP error: " + responseCode);
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
                    .putLong("wallpaper_timestamp", System.currentTimeMillis())
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

    /**
     * Sets wallpaper for home screen only
     * FIXED: allowReturn = false to prevent app restart (like Zedge)
     */
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
                if (IS_NOUGAT_OR_GREATER) {
                    wallpaperManager.setBitmap(
                        bmp,
                        null,
                        false,  // ✅ KEY FIX: false = no crop UI, no restart!
                        WallpaperManager.FLAG_SYSTEM
                    );
                } else {
                    wallpaperManager.setBitmap(bmp);
                }
                
                // ✅ Clean up bitmap to prevent memory issues
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
                
                JSObject result = new JSObject();
                result.put("success", true);
                // ✅ PATCH 5: Resolve on main thread to safely update UI (spinner/toast)
                getBridge().executeOnMainThread(() -> callbackContext.resolve(result));
                
                Log.d(TAG, "✅ Wallpaper set successfully (home screen) - No restart!");
                
            } catch (IOException e) {
                // ✅ PATCH 5: Reject on main thread
                getBridge().executeOnMainThread(() -> callbackContext.reject(e.getMessage()));
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                getBridge().executeOnMainThread(() -> callbackContext.reject("Out of memory: " + e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets wallpaper for lock screen only
     * FIXED: allowReturn = false to prevent app restart
     */
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
                    wallpaperManager.setBitmap(
                        bmp,
                        null,
                        false,  // ✅ KEY FIX: false = no crop UI, no restart!
                        WallpaperManager.FLAG_LOCK
                    );
                } else {
                    wallpaperManager.setBitmap(bmp);
                }
                
                // ✅ Clean up bitmap
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
                
                JSObject result = new JSObject();
                result.put("success", true);
                // ✅ PATCH 5: Resolve on main thread to safely update UI (spinner/toast)
                getBridge().executeOnMainThread(() -> callbackContext.resolve(result));
                
                Log.d(TAG, "✅ Wallpaper set successfully (lock screen) - No restart!");
                
            } catch (IOException e) {
                // ✅ PATCH 5: Reject on main thread
                getBridge().executeOnMainThread(() -> callbackContext.reject(e.getMessage()));
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                getBridge().executeOnMainThread(() -> callbackContext.reject("Out of memory: " + e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets wallpaper for both home and lock screens
     * FIXED: allowReturn = false to prevent app restart
     */
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
                // Set for home screen first
                wallpaperManager.setBitmap(bmp);
                
                // Then set for lock screen (Android 7.0+)
                if (IS_NOUGAT_OR_GREATER) {
                    wallpaperManager.setBitmap(bmp, null, false, WallpaperManager.FLAG_LOCK);
                }

                // ✅ Clean up bitmap
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
                
                JSObject result = new JSObject();
                result.put("success", true);
                // ✅ PATCH 5: Resolve on main thread to safely update UI (spinner/toast)
                getBridge().executeOnMainThread(() -> callbackContext.resolve(result));
                
                Log.d(TAG, "✅ Wallpaper set successfully (both screens) - No restart!");
                
            } catch (IOException e) {
                // ✅ PATCH 5: Reject on main thread
                getBridge().executeOnMainThread(() -> callbackContext.reject(e.getMessage()));
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                getBridge().executeOnMainThread(() -> callbackContext.reject("Out of memory: " + e.getMessage()));
                e.printStackTrace();
            }
        }
    }
}
