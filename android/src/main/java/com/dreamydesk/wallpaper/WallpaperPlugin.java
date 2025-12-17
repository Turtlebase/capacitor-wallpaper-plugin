
package com.dreamydesk.app.plugins;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import com.dreamydesk.app.VideoLiveWallpaperService;
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

@CapacitorPlugin(name = "Wallpaper")  // Keep as "Wallpaper" - easier
public class WPUtils extends Plugin {

    private static final String TAG = "WallpaperPlugin";

    @PluginMethod
    public void setStatic(PluginCall call) {
        String imageUrl = call.getString("imageUrl");
        String screen = call.getString("screen", "HOME");

        if (imageUrl == null || imageUrl.isEmpty()) {
            call.reject("A valid 'imageUrl' must be provided.");
            return;
        }

        // FIX: Use new Thread instead of getBridge().getWebView().post()
        new Thread(() -> {
            try {
                Bitmap bitmap = getBitmapFromURL(imageUrl);
                if (bitmap == null) {
                    call.reject("Failed to download or decode image.");
                    return;
                }

                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
                int flag = WallpaperManager.FLAG_SYSTEM; // Default to HOME
                if ("LOCK".equals(screen) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    flag = WallpaperManager.FLAG_LOCK;
                } else if ("BOTH".equals(screen) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    flag = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                }
                
                wallpaperManager.setBitmap(bitmap, null, true, flag);
                
                JSObject result = new JSObject();
                result.put("success", true);
                call.resolve(result);
            } catch (IOException e) {
                Log.e(TAG, "Error setting static wallpaper", e);
                call.reject("Failed to set wallpaper: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void setLive(PluginCall call) {
        String videoUrl = call.getString("videoUrl");
        if (videoUrl == null || videoUrl.isEmpty()) {
            call.reject("A 'videoUrl' must be provided.");
            return;
        }

        // FIX: Use new Thread instead of getBridge().getWebView().post()
        new Thread(() -> {
            try {
                Log.d(TAG, "Downloading video from: " + videoUrl);
                
                // Download the video to a file that the service can access
                File videoFile = downloadVideo(getContext(), videoUrl);
                Log.d(TAG, "Video downloaded to: " + videoFile.getAbsolutePath());
                
                // Store the path so the service can find it
                VideoLiveWallpaperService.setVideoPath(videoFile.getAbsolutePath());

                // Launch live wallpaper picker
                Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(getContext(), VideoLiveWallpaperService.class));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                
                JSObject result = new JSObject();
                result.put("success", true);
                result.put("requiresUserAction", true);
                call.resolve(result);

            } catch (Exception e) {
                Log.e(TAG, "Failed to set live wallpaper", e);
                call.reject("Failed to set live wallpaper: " + e.getMessage());
            }
        }).start();
    }
    
    @PluginMethod
    public void test(PluginCall call) {
        Log.d(TAG, "✅ TEST METHOD CALLED - Plugin is working!");
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "Plugin is working!");
        call.resolve(result);
    }
    
    private File downloadVideo(Context context, String videoUrl) throws IOException {
        URL url = new URL(videoUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.connect();
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        
        InputStream inputStream = connection.getInputStream();

        // FIX: Use filesDir instead of cacheDir for persistence
        File videoFile = new File(context.getFilesDir(), "live_wallpaper.mp4");
        if(videoFile.exists()){
            boolean deleted = videoFile.delete();
            Log.d(TAG, "Existing video deleted: " + deleted);
        }
        
        FileOutputStream outputStream = new FileOutputStream(videoFile);

        byte[] buffer = new byte[8192];  // Larger buffer for better performance
        int bytesRead;
        long total = 0;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            total += bytesRead;
        }
        
        Log.d(TAG, "Downloaded " + total + " bytes");
        
        outputStream.flush();
        outputStream.close();
        inputStream.close();
        connection.disconnect();
        
        return videoFile;
    }

    private Bitmap getBitmapFromURL(String src) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(src).openConnection();
        connection.setDoInput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.connect();
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        
        InputStream input = connection.getInputStream();
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        input.close();
        connection.disconnect();
        
        return bitmap;
    }
}
