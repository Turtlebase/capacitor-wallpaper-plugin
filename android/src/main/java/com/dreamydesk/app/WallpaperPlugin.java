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

@CapacitorPlugin(name = "WallpaperPlugin")
public class WallpaperPlugin extends Plugin {

    private static final String TAG = "WallpaperPlugin";
    private Context context = null;
    private static final boolean IS_NOUGAT_OR_GREATER = Build.VERSION.SDK_INT >= 24;

    // Executor for wallpaper operations (prevents ANR)
    private static final ExecutorService wallpaperExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "WallpaperPlugin loaded successfully!");
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", true);
        call.resolve(result);
    }

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {

        context = getContext();

        String url = call.getString("url");

        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {

            Bitmap bmp = future.get();

            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }

            bmp = resizeBitmapToScreen(bmp);

            wallpaperExecutor.execute(new SetBackgroundImageRunnable(bmp, call));

        } catch (InterruptedException | ExecutionException e) {
            call.reject("Download failed: " + e.getMessage());
        }

        executorService.shutdown();
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {

        context = getContext();

        String url = call.getString("url");

        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {

            Bitmap bmp = future.get();

            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }

            bmp = resizeBitmapToScreen(bmp);

            wallpaperExecutor.execute(new SetLockScreenImageRunnable(bmp, call));

        } catch (InterruptedException | ExecutionException e) {
            call.reject("Download failed: " + e.getMessage());
        }

        executorService.shutdown();
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {

        context = getContext();

        String url = call.getString("url");

        if (url == null || url.isEmpty()) {
            call.reject("Must provide URL");
            return;
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {

            Bitmap bmp = future.get();

            if (bmp == null) {
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }

            bmp = resizeBitmapToScreen(bmp);

            wallpaperExecutor.execute(new SetLockScreenAndWallpaperImageRunnable(bmp, call));

        } catch (InterruptedException | ExecutionException e) {
            call.reject("Download failed: " + e.getMessage());
        }

        executorService.shutdown();
    }

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {

        String videoUrl = call.getString("url");

        if (videoUrl == null || videoUrl.isEmpty()) {
            call.reject("Must provide video URL");
            return;
        }

        try {

            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(getContext(), LiveWallpaperService.class)
            );

            getContext().startActivity(intent);

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Failed to open wallpaper picker");
        }
    }

    // Resize bitmap to device screen resolution
    private Bitmap resizeBitmapToScreen(Bitmap bmp) {

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        return Bitmap.createScaledBitmap(bmp, width, height, true);
    }

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
                connection.connect();

                inputStream = connection.getInputStream();

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                bmp = BitmapFactory.decodeStream(inputStream, null, options);

            } catch (Exception e) {

                Log.e(TAG, "Download error: " + e.getMessage());
            }

            return bmp;
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

                if (IS_NOUGAT_OR_GREATER) {

                    wallpaperManager.setBitmap(
                            bmp,
                            null,
                            false,
                            WallpaperManager.FLAG_SYSTEM
                    );

                } else {

                    wallpaperManager.setBitmap(bmp);
                }

                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }

                JSObject result = new JSObject();
                result.put("success", true);

                getBridge().executeOnMainThread(() -> callbackContext.resolve(result));

            } catch (Exception e) {

                getBridge().executeOnMainThread(() -> callbackContext.reject(e.getMessage()));
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

                wallpaperManager.setBitmap(
                        bmp,
                        null,
                        false,
                        WallpaperManager.FLAG_LOCK
                );

                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }

                JSObject result = new JSObject();
                result.put("success", true);

                getBridge().executeOnMainThread(() -> callbackContext.resolve(result));

            } catch (Exception e) {

                getBridge().executeOnMainThread(() -> callbackContext.reject(e.getMessage()));
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

                    wallpaperManager.setBitmap(
                            bmp,
                            null,
                            false,
                            WallpaperManager.FLAG_LOCK
                    );
                }

                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }

                JSObject result = new JSObject();
                result.put("success", true);

                getBridge().executeOnMainThread(() -> callbackContext.resolve(result));

            } catch (Exception e) {

                getBridge().executeOnMainThread(() -> callbackContext.reject(e.getMessage()));
            }
        }
    }
}
