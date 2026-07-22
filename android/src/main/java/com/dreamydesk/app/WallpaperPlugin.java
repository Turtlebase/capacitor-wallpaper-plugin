package com.dreamydesk.app;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorManager;
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
 * Parallax wallpapers: Download image at oversized "cover" resolution, then open native
 *                       Android picker pointed at ParallaxWallpaperService, which pans
 *                       the image based on home-screen scroll + device tilt.
 *
 * @version 1.5.0 - Added parallax live wallpaper support (setParallaxWallpaper,
 *                   updateParallaxSettings, resetParallaxEffect, isParallaxSupported)
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
     * Sets a DIFFERENT image for the home screen and the lock screen in a
     * single call — e.g. "wallpaper A" on home, "wallpaper B" on lock.
     *
     * Both images are downloaded concurrently (two independent network
     * calls, not sequential) to keep total wait time close to that of a
     * single download rather than doubling it. Each is resized to the
     * screen independently via the existing resizeBitmapToScreen, which is
     * fully stateless and safe to call twice with different bitmaps.
     *
     * ALL-OR-NOTHING: if either download fails, NEITHER wallpaper is
     * applied. This deliberately avoids a half-applied state where, say,
     * the new home wallpaper is set but the lock screen still shows an old
     * or default image — the two must change together or not at all, so
     * home always corresponds to the home image and lock always
     * corresponds to the lock image, with no possibility of a mismatch.
     */
    @PluginMethod
    public void setHomeAndLockWallpapers(PluginCall call) {
        Log.d(TAG, "📱 setHomeAndLockWallpapers called");

        context = getContext();

        String homeUrl = call.getString("homeUrl");
        String lockUrl = call.getString("lockUrl");

        if (homeUrl == null || homeUrl.isEmpty()) {
            call.reject("Must provide homeUrl");
            return;
        }
        if (lockUrl == null || lockUrl.isEmpty()) {
            call.reject("Must provide lockUrl");
            return;
        }

        // Two-thread pool so both downloads run concurrently, not one after
        // the other — this is purely for the network fetch, separate from
        // wallpaperExecutor which serializes the actual apply step below.
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
        Future<Bitmap> homeFuture = downloadExecutor.submit(new GetBitmapFromURLCallable(homeUrl));
        Future<Bitmap> lockFuture = downloadExecutor.submit(new GetBitmapFromURLCallable(lockUrl));

        Bitmap homeBmp = null;
        Bitmap lockBmp = null;

        try {
            homeBmp = homeFuture.get();
            lockBmp = lockFuture.get();

            if (homeBmp != null) {
                homeBmp = resizeBitmapToScreen(homeBmp);
            }
            if (lockBmp != null) {
                lockBmp = resizeBitmapToScreen(lockBmp);
            }

            if (homeBmp == null || lockBmp == null) {
                // All-or-nothing: recycle whichever one DID succeed so it
                // doesn't leak, then reject without touching either screen.
                if (homeBmp != null && !homeBmp.isRecycled()) homeBmp.recycle();
                if (lockBmp != null && !lockBmp.isRecycled()) lockBmp.recycle();

                String failed = (homeBmp == null && lockBmp == null)
                        ? "both images"
                        : (homeBmp == null ? "home image" : "lock image");
                call.reject("Failed to download " + failed + " — no wallpaper was changed");
                downloadExecutor.shutdown();
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            if (homeBmp != null && !homeBmp.isRecycled()) homeBmp.recycle();
            if (lockBmp != null && !lockBmp.isRecycled()) lockBmp.recycle();
            call.reject("Download failed: " + e.getMessage() + " — no wallpaper was changed");
            downloadExecutor.shutdown();
            return;
        } finally {
            downloadExecutor.shutdown();
        }

        // Both bitmaps are ready — apply on the serialized wallpaper executor,
        // same thread pool used by every other set*Wallpaper method, so this
        // can't race with a concurrent setImageAsWallpaper/setImageAsLockScreen
        // call from elsewhere in the app.
        wallpaperExecutor.execute(new SetHomeAndLockWallpapersRunnable(homeBmp, lockBmp, call));
    }


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
                openNativeLiveWallpaperPicker(call, LiveWallpaperService.class);
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
                openNativeLiveWallpaperPicker(call, LiveWallpaperService.class);
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
     * Turn an image into a parallax live wallpaper.
     *
     * The app only needs to provide a `url`; everything else (pan range,
     * speed, whether scroll/tilt drive the motion, how much overscan room
     * to render) is optional and defaults to sensible values. The plugin:
     *   1. Downloads the image at an oversized resolution (screen * overscan)
     *      so there's room to pan without exposing edges.
     *   2. Cover+centre-crops it to that oversized target (same technique
     *      used for static wallpapers, just at a larger canvas).
     *   3. Saves it to persistent app storage and records the effect
     *      settings in SharedPreferences.
     *   4. Opens the native live wallpaper picker pointed at
     *      ParallaxWallpaperService, which does the actual scroll/tilt
     *      panning + smoothing at render time.
     */
    @PluginMethod
    public void setParallaxWallpaper(PluginCall call) {
        Log.d(TAG, "🌄 setParallaxWallpaper called");

        context = getContext();

        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        final float intensity = clampFloat(call.getDouble("intensity", 30d).floatValue(), 0f, 100f);
        final float speed = clampFloat(call.getDouble("speed", 0.2d).floatValue(), 0.01f, 1f);
        final float depthStrength = clampFloat(call.getDouble("depthStrength", 1.0d).floatValue(), 0f, 2f);
        final boolean sensorParallax = call.getBoolean("sensorParallax", true);
        final boolean scrollParallax = call.getBoolean("scrollParallax", true);
        final float overscan = clampFloat(call.getDouble("overscan", 1.3d).floatValue(), 1.05f, 2.0f);

        Bitmap bmp;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url, overscan));

        try {
            bmp = future.get();

            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }

            // Cover+crop to the oversized (screen * overscan) canvas — gives the
            // engine pan room while still filling the screen with no letterboxing.
            bmp = resizeBitmapForParallax(bmp, overscan);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            call.reject("Download failed: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        final Bitmap finalBmp = bmp;
        wallpaperExecutor.execute(new SaveParallaxImageRunnable(
            finalBmp, call, intensity, speed, depthStrength, sensorParallax, scrollParallax));
        executorService.shutdown();
    }

    /**
     * Update the intensity/speed/sensor/scroll settings of the currently
     * active parallax wallpaper in place — ParallaxWallpaperService listens
     * for SharedPreferences changes and applies them on the next frame, so
     * no re-download or re-picker step is needed.
     */
    @PluginMethod
    public void updateParallaxSettings(PluginCall call) {
        Log.d(TAG, "🎚️ updateParallaxSettings called");

        context = getContext();
        SharedPreferences prefs = context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        JSObject data = call.getData();

        if (data.has("intensity")) {
            editor.putFloat("parallax_intensity", clampFloat(call.getDouble("intensity", 30d).floatValue(), 0f, 100f));
        }
        if (data.has("speed")) {
            editor.putFloat("parallax_speed", clampFloat(call.getDouble("speed", 0.2d).floatValue(), 0.01f, 1f));
        }
        if (data.has("depthStrength")) {
            editor.putFloat("parallax_depth_strength", clampFloat(call.getDouble("depthStrength", 1.0d).floatValue(), 0f, 2f));
        }
        if (data.has("sensorParallax")) {
            editor.putBoolean("parallax_sensor_enabled", call.getBoolean("sensorParallax", true));
        }
        if (data.has("scrollParallax")) {
            editor.putBoolean("parallax_scroll_enabled", call.getBoolean("scrollParallax", true));
        }
        editor.apply();

        JSObject result = new JSObject();
        result.put("success", true);
        call.resolve(result);
    }

    /**
     * Resets/stops the parallax effect and clears the system wallpaper set
     * by this plugin, reverting to the device default.
     */
    @PluginMethod
    public void resetParallaxEffect(PluginCall call) {
        Log.d(TAG, "♻️ resetParallaxEffect called");

        context = getContext();

        try {
            WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_SYSTEM);

            context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("parallax_image_path")
                .remove("parallax_timestamp")
                .apply();

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);

            Log.d(TAG, "✅ Parallax effect reset, wallpaper cleared to default");
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to reset wallpaper: " + e.getMessage());
            call.reject("Failed to reset wallpaper: " + e.getMessage());
        }
    }

    /**
     * Whether this device can run the parallax wallpaper feature:
     * requires live wallpaper support, and reports whether a motion
     * sensor is present (sensor-based tilt parallax will silently no-op
     * without one, but scroll-based parallax still works).
     */
    @PluginMethod
    public void isParallaxSupported(PluginCall call) {
        context = getContext();

        boolean liveWallpaperSupported = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER);

        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        boolean hasSensor = sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;

        JSObject result = new JSObject();
        result.put("supported", liveWallpaperSupported);
        result.put("hasSensor", hasSensor);
        call.resolve(result);
    }

    /**
     * Opens Android's native live wallpaper chooser for the given service.
     * User can preview and select the wallpaper.
     */
    private void openNativeLiveWallpaperPicker(PluginCall call, Class<?> serviceClass) {
        try {
            Log.d(TAG, "📱 Launching native wallpaper picker for " + serviceClass.getSimpleName());
            
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(getContext(), serviceClass)
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
     * Same cover+centre-crop technique as resizeBitmapToScreen, but targets an
     * OVERSIZED canvas (screen dimensions * overscan) instead of the screen
     * exactly. The extra pixels around every edge are what
     * ParallaxWallpaperService pans across — without this room the image
     * would either show black edges or have to be scaled/cropped live
     * (which the Android WallpaperService canvas surface doesn't support).
     */
    private Bitmap resizeBitmapForParallax(Bitmap bmp, float overscan) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int targetW = Math.round(metrics.widthPixels * overscan);
        int targetH = Math.round(metrics.heightPixels * overscan);

        int srcW = bmp.getWidth();
        int srcH = bmp.getHeight();

        float scaleX = (float) targetW / srcW;
        float scaleY = (float) targetH / srcH;
        float scale = Math.max(scaleX, scaleY);

        int scaledW = Math.round(srcW * scale);
        int scaledH = Math.round(srcH * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true);
        if (scaled != bmp && !bmp.isRecycled()) {
            bmp.recycle();
        }

        int cropX = Math.max(0, (scaledW - targetW) / 2);
        int cropY = Math.max(0, (scaledH - targetH) / 2);
        int cropW = Math.min(targetW, scaledW);
        int cropH = Math.min(targetH, scaledH);

        Bitmap cropped = Bitmap.createBitmap(scaled, cropX, cropY, cropW, cropH);
        if (scaled != cropped && !scaled.isRecycled()) {
            scaled.recycle();
        }

        Log.d(TAG, "📐 Parallax cover+crop: src=" + srcW + "x" + srcH +
              " overscan=" + overscan +
              " target=" + targetW + "x" + targetH +
              " final=" + cropped.getWidth() + "x" + cropped.getHeight());

        return cropped;
    }

    private float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
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
        private float sizeMultiplier;

        private GetBitmapFromURLCallable(String URL) {
            this(URL, 1.0f);
        }

        /**
         * @param sizeMultiplier scales the target decode dimensions above the
         *                       raw screen size (e.g. 1.3 for parallax, which
         *                       needs a larger-than-screen source image to pan
         *                       across). Only affects the inSampleSize chosen
         *                       for pass 2 — never upscales beyond source res.
         */
        private GetBitmapFromURLCallable(String URL, float sizeMultiplier) {
            this.URL = URL;
            this.sizeMultiplier = sizeMultiplier;
        }

        @Override
        public Bitmap call() {
            Bitmap bmp = null;
            HttpURLConnection connection = null;
            InputStream inputStream = null;

            try {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int reqWidth  = Math.round(metrics.widthPixels * sizeMultiplier);
                int reqHeight = Math.round(metrics.heightPixels * sizeMultiplier);

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
     * Persists the oversized parallax bitmap to disk, writes the effect
     * settings (intensity/speed/sensor/scroll) to SharedPreferences so
     * ParallaxWallpaperService can pick them up, then opens the native
     * live wallpaper picker on the main thread.
     */
    private class SaveParallaxImageRunnable implements Runnable {
        private final Bitmap bmp;
        private final PluginCall callbackContext;
        private final float intensity;
        private final float speed;
        private final float depthStrength;
        private final boolean sensorParallax;
        private final boolean scrollParallax;

        private SaveParallaxImageRunnable(Bitmap bmp, PluginCall callbackContext, float intensity,
                                           float speed, float depthStrength, boolean sensorParallax, boolean scrollParallax) {
            this.bmp = bmp;
            this.callbackContext = callbackContext;
            this.intensity = intensity;
            this.speed = speed;
            this.depthStrength = depthStrength;
            this.sensorParallax = sensorParallax;
            this.scrollParallax = scrollParallax;
        }

        @Override
        public void run() {
            FileOutputStream fos = null;
            try {
                // Persistent storage (not cache) — the wallpaper service needs
                // this file to stick around for as long as the wallpaper is active.
                File outFile = new File(context.getFilesDir(), "parallax_wallpaper.jpg");
                fos = new FileOutputStream(outFile);
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                fos.flush();

                context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("parallax_image_path", outFile.getAbsolutePath())
                    .putFloat("parallax_intensity", intensity)
                    .putFloat("parallax_speed", speed)
                                        .putFloat("parallax_depth_strength", depthStrength)
                    .putBoolean("parallax_sensor_enabled", sensorParallax)
                    .putBoolean("parallax_scroll_enabled", scrollParallax)
                    .putLong("parallax_timestamp", System.currentTimeMillis())
                    .apply();

                Log.d(TAG, "✅ Parallax image saved: " + outFile.getAbsolutePath() +
                                            " intensity=" + intensity + " speed=" + speed + " depthStrength=" + depthStrength +
                      " sensor=" + sensorParallax + " scroll=" + scrollParallax);

                // Opening an Activity + resolving the call must happen on the main thread.
                getBridge().executeOnMainThread(() ->
                        openNativeLiveWallpaperPicker(callbackContext, ParallaxWallpaperService.class));

            } catch (IOException e) {
                Log.e(TAG, "❌ Failed to save parallax image: " + e.getMessage());
                getBridge().executeOnMainThread(() ->
                        callbackContext.reject("Failed to save parallax image: " + e.getMessage()));
            } finally {
                try {
                    if (fos != null) fos.close();
                } catch (IOException ignored) {}
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
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

    /**
     * Applies two DIFFERENT already-downloaded, already-resized bitmaps: one
     * to FLAG_SYSTEM (home) and one to FLAG_LOCK (lock screen). Both
     * bitmaps are guaranteed non-null by the caller (setHomeAndLockWallpapers
     * already enforced all-or-nothing at the download stage).
     *
     * Order matters for the failure-reporting message below, but not for
     * correctness: FLAG_SYSTEM and FLAG_LOCK are independent slots in
     * WallpaperManager, so setting one does not affect the other. If the
     * second call throws after the first succeeded, we still reject overall
     * (so the app knows the pairing wasn't fully applied) rather than
     * resolving with a partial success that could be mistaken for "both set."
     */
    private class SetHomeAndLockWallpapersRunnable implements Runnable {
        private Bitmap homeBmp;
        private Bitmap lockBmp;
        private PluginCall callbackContext;

        private SetHomeAndLockWallpapersRunnable(Bitmap homeBmp, Bitmap lockBmp, PluginCall callbackContext) {
            this.homeBmp = homeBmp;
            this.lockBmp = lockBmp;
            this.callbackContext = callbackContext;
        }

        @Override
        public void run() {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            boolean homeApplied = false;
            boolean lockApplied = false;

            try {
                if (IS_NOUGAT_OR_GREATER) {
                    wallpaperManager.setBitmap(homeBmp, null, false, WallpaperManager.FLAG_SYSTEM);
                } else {
                    wallpaperManager.setBitmap(homeBmp);
                }
                homeApplied = true;

                if (IS_NOUGAT_OR_GREATER) {
                    wallpaperManager.setBitmap(lockBmp, null, false, WallpaperManager.FLAG_LOCK);
                    lockApplied = true;
                } else {
                    // Pre-Nougat devices have no separate lock-screen wallpaper
                    // API; FLAG_LOCK is unavailable, so home was applied but a
                    // distinct lock image cannot be. Report this clearly rather
                    // than silently pretending both were set.
                    Log.d(TAG, "⚠️ Device is pre-Android 7.0: no separate lock screen wallpaper API, lock image not applied");
                }

                if (homeBmp != null && !homeBmp.isRecycled()) homeBmp.recycle();
                if (lockBmp != null && !lockBmp.isRecycled()) lockBmp.recycle();

                if (homeApplied && lockApplied) {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("homeApplied", true);
                    result.put("lockApplied", true);
                    getBridge().executeOnMainThread(() -> callbackContext.resolve(result));
                    Log.d(TAG, "✅ Home and lock wallpapers set successfully (different images) - No restart!");
                } else {
                    // homeApplied but lockApplied is false: pre-Nougat case above.
                    getBridge().executeOnMainThread(() ->
                            callbackContext.reject("Home wallpaper was set, but this device does not support a separate lock screen wallpaper (requires Android 7.0+)"));
                }

            } catch (IOException e) {
                if (homeBmp != null && !homeBmp.isRecycled()) homeBmp.recycle();
                if (lockBmp != null && !lockBmp.isRecycled()) lockBmp.recycle();
                final boolean homeWasApplied = homeApplied;
                getBridge().executeOnMainThread(() -> callbackContext.reject(
                        (homeWasApplied
                                ? "Home wallpaper was set, but lock screen failed: "
                                : "Failed to set home wallpaper: ") + e.getMessage()));
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                if (homeBmp != null && !homeBmp.isRecycled()) homeBmp.recycle();
                if (lockBmp != null && !lockBmp.isRecycled()) lockBmp.recycle();
                getBridge().executeOnMainThread(() -> callbackContext.reject("Out of memory: " + e.getMessage()));
                e.printStackTrace();
            }
        }
    }
}