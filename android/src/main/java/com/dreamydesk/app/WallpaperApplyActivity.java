package com.dreamydesk.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WallpaperApplyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String imageUrl = getIntent().getStringExtra("imageUrl");
        String mode = getIntent().getStringExtra("mode");

        new Thread(() -> applyWallpaper(imageUrl, mode)).start();
    }

    private void applyWallpaper(String url, String mode) {
        try {
            Bitmap bitmap = downloadBitmap(url);
            WallpaperManager wm = WallpaperManager.getInstance(this);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ("home".equals(mode)) {
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM);
                } else if ("lock".equals(mode)) {
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK);
                } else {
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM);
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK);
                }
            } else {
                wm.setBitmap(bitmap);
            }

            bitmap.recycle();
        } catch (Exception ignored) {
        } finally {
            finish(); // 🔑 silent exit
        }
    }

    private Bitmap downloadBitmap(String src) throws Exception {
        URL url = new URL(src);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.connect();
        InputStream is = conn.getInputStream();
        return BitmapFactory.decodeStream(is);
    }
}
