package com.dreamydesk.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LiveWallpaperService extends WallpaperService {

    private static final String TAG = "LiveWallpaperService";

    @Override
    public Engine onCreateEngine() {
        Log.d(TAG, "Creating wallpaper engine");
        return new GIFWallpaperEngine();
    }

    private class GIFWallpaperEngine extends Engine {
        private final int frameDuration = 40;
        private final Handler handler = new Handler();
        private Movie movie;
        private long movieStart;
        private boolean visible = true;
        private SurfaceHolder holder;

        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };

        GIFWallpaperEngine() {
            holder = getSurfaceHolder();
            loadGifFromUrl();
        }

        private void loadGifFromUrl() {
            SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);
            String gifUrl = prefs.getString("live_wallpaper_url", null);

            if (gifUrl == null) {
                Log.e(TAG, "No GIF URL provided");
                return;
            }

            Log.d(TAG, "Loading GIF from: " + gifUrl);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(gifUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        
                        InputStream input = new BufferedInputStream(connection.getInputStream());
                        movie = Movie.decodeStream(input);
                        input.close();
                        
                        if (movie != null) {
                            Log.d(TAG, "GIF loaded successfully");
                            handler.post(drawRunner);
                        } else {
                            Log.e(TAG, "Failed to decode GIF");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading GIF: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                handler.post(drawRunner);
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.visible = false;
            handler.removeCallbacks(drawRunner);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.holder = holder;
        }

        private void draw() {
            if (visible && movie != null) {
                drawFrame();
            }
        }

        private void drawFrame() {
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    int width = canvas.getWidth();
                    int height = canvas.getHeight();

                    long now = android.os.SystemClock.uptimeMillis();
                    if (movieStart == 0) {
                        movieStart = now;
                    }

                    int duration = movie.duration();
                    if (duration == 0) duration = 1000;

                    int relTime = (int) ((now - movieStart) % duration);
                    movie.setTime(relTime);

                    float scaleX = (float) width / movie.width();
                    float scaleY = (float) height / movie.height();
                    float scale = Math.max(scaleX, scaleY);

                    float scaledWidth = movie.width() * scale;
                    float scaledHeight = movie.height() * scale;
                    float left = (width - scaledWidth) / 2;
                    float top = (height - scaledHeight) / 2;

                    canvas.save();
                    canvas.translate(left, top);
                    canvas.scale(scale, scale);
                    movie.draw(canvas, 0, 0);
                    canvas.restore();
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            handler.removeCallbacks(drawRunner);
            if (visible) {
                handler.postDelayed(drawRunner, frameDuration);
            }
        }
    }
}
