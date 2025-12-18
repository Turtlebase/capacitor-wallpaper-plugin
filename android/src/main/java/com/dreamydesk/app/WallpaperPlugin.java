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

import java.io.IOException;
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

    @Override
    public void load() {
        Log.d(TAG, "✅ WallpaperPlugin loaded successfully!");
    }

    @PluginMethod
    public void setImageAsWallpaper(PluginCall call) {
        Log.d(TAG, "📱 setImageAsWallpaper called");
        
        context = IS_NOUGAT_OR_GREATER ? 
                getActivity().getWindow().getContext() : 
                getActivity().getApplicationContext();

        String url = call.getString("url");
        
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "❌ No URL provided");
            call.reject("Must provide URL");
            return;
        }

        Log.d(TAG, "🌐 Downloading from: " + url);

        Bitmap bmp = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Bitmap> future = executorService.submit(new GetBitmapFromURLCallable(url));

        try {
            bmp = future.get();
            if (bmp == null) {
                Log.e(TAG, "❌ Failed to download image");
                call.reject("Failed to download image");
                executorService.shutdown();
                return;
            }
            Log.d(TAG, "✅ Image downloaded successfully");
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "❌ Error: " + e.getMessage());
            e.printStackTrace();
            call.reject("Failed to download: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        getBridge().executeOnMainThread(new SetBackgroundImageRunnable(bmp, call));
        executorService.shutdown();
    }

    @PluginMethod
    public void setImageAsLockScreen(PluginCall call) {
        Log.d(TAG, "📱 setImageAsLockScreen called");
        
        context = IS_NOUGAT_OR_GREATER ? 
                getActivity().getWindow().getContext() : 
                getActivity().getApplicationContext();

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
            call.reject("Failed to download: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        getBridge().executeOnMainThread(new SetLockScreenImageRunnable(bmp, call));
        executorService.shutdown();
    }

    @PluginMethod
    public void setImageAsWallpaperAndLockScreen(PluginCall call) {
        Log.d(TAG, "📱 setImageAsWallpaperAndLockScreen called");
        
        context = IS_NOUGAT_OR_GREATER ? 
                getActivity().getWindow().getContext() : 
                getActivity().getApplicationContext();

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
            call.reject("Failed to download: " + e.getMessage());
            executorService.shutdown();
            return;
        }

        getBridge().executeOnMainThread(new SetLockScreenAndWallpaperImageRunnable(bmp, call));
        executorService.shutdown();
    }

    @PluginMethod
    public void setLiveWallpaper(PluginCall call) {
        Log.d(TAG, "📱 setLiveWallpaper called");
        
        try {
            String videoUrl = call.getString("url");
            String type = call.getString("type", "gif");
            
            if (videoUrl == null || videoUrl.isEmpty()) {
                call.reject("Must provide video URL");
                return;
            }

            getContext().getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("live_wallpaper_url", videoUrl)
                .putString("live_wallpaper_type", type)
                .apply();

            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(getContext(), LiveWallpaperService.class)
            );
            getActivity().startActivity(intent);

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
            
            Log.d(TAG, "✅ Live wallpaper intent launched");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed: " + e.getMessage());
            call.reject("Failed to set live wallpaper: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", true);
        call.resolve(result);
    }

    private class GetBitmapFromURLCallable implements Callable<Bitmap> {
        private String URL;

        private GetBitmapFromURLCallable(String URL) {
            this.URL = URL;
        }

        @Override
        public Bitmap call() {
            Bitmap bmp = null;
            try {
                Log.d(TAG, "⬇️ Downloading image...");
                URL imageUrl = new URL(this.URL);
                bmp = BitmapFactory.decodeStream(imageUrl.openStream());
                Log.d(TAG, "✅ Download complete");
            } catch (IOException e) {
                Log.e(TAG, "❌ Download failed: " + e.getMessage());
                e.printStackTrace();
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
            Log.d(TAG, "🖼️ Setting wallpaper...");
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            try {
                wallpaperManager.setBitmap(bmp);
                Log.d(TAG, "✅ Wallpaper set successfully");
            } catch (IOException e) {
                Log.e(TAG, "❌ Failed to set wallpaper: " + e.getMessage());
                callbackContext.reject(e.getMessage());
                e.printStackTrace();
                return;
            }
            JSObject result = new JSObject();
            result.put("success", true);
            callbackContext.resolve(result);
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
            Log.d(TAG, "🔒 Setting lock screen wallpaper...");
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            try {
                wallpaperManager.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
                Log.d(TAG, "✅ Lock screen set successfully");
            } catch (IOException e) {
                Log.e(TAG, "❌ Failed: " + e.getMessage());
                callbackContext.reject(e.getMessage());
                e.printStackTrace();
                return;
            }
            JSObject result = new JSObject();
            result.put("success", true);
            callbackContext.resolve(result);
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
            Log.d(TAG, "🖼️ Setting both wallpapers...");
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            try {
                wallpaperManager.setBitmap(bmp);
                wallpaperManager.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
                Log.d(TAG, "✅ Both wallpapers set successfully");
            } catch (IOException e) {
                Log.e(TAG, "❌ Failed: " + e.getMessage());
                callbackContext.reject(e.getMessage());
                e.printStackTrace();
                return;
            }
            JSObject result = new JSObject();
            result.put("success", true);
            callbackContext.resolve(result);
        }
    }
}
