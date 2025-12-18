package com.dreamydesk.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.media.MediaPlayer;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileInputStream;

/**
 * Live Wallpaper Service
 * Reads video/GIF from file path (already downloaded by WallpaperPlugin)
 * Supports both GIF and MP4 formats
 */
public class LiveWallpaperService extends WallpaperService {

    private static final String TAG = "LiveWallpaperService";

    @Override
    public Engine onCreateEngine() {
        Log.d(TAG, "🎬 Creating wallpaper engine");
        return new VideoWallpaperEngine();
    }

    private class VideoWallpaperEngine extends Engine {
        private final int frameDuration = 40; // ~25 FPS for GIF
        private final Handler handler = new Handler();
        
        // For GIF
        private Movie movie;
        private long movieStart;
        
        // For MP4
        private MediaPlayer mediaPlayer;
        
        private boolean visible = true;
        private SurfaceHolder holder;
        private String wallpaperType;

        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };

        VideoWallpaperEngine() {
            holder = getSurfaceHolder();
            loadWallpaperFromFile();
        }

        /**
         * Load video/GIF from the file path saved by WallpaperPlugin
         */
        private void loadWallpaperFromFile() {
            SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);
            String filePath = prefs.getString("live_wallpaper_path", null);
            wallpaperType = prefs.getString("live_wallpaper_type", "gif");

            if (filePath == null) {
                Log.e(TAG, "❌ No wallpaper file path found");
                return;
            }

            Log.d(TAG, "📂 Loading " + wallpaperType.toUpperCase() + " from: " + filePath);

            File wallpaperFile = new File(filePath);
            if (!wallpaperFile.exists()) {
                Log.e(TAG, "❌ File not found: " + filePath);
                return;
            }

            // Load based on type
            if ("gif".equalsIgnoreCase(wallpaperType)) {
                loadGIFFromFile(wallpaperFile);
            } else if ("mp4".equalsIgnoreCase(wallpaperType)) {
                loadMP4FromFile(wallpaperFile);
            }
        }

        /**
         * Load GIF from file
         */
        private void loadGIFFromFile(File gifFile) {
            try {
                Log.d(TAG, "📂 Loading GIF from file...");
                
                FileInputStream fis = new FileInputStream(gifFile);
                movie = Movie.decodeStream(fis);
                fis.close();

                if (movie != null) {
                    Log.d(TAG, "✅ GIF loaded - Duration: " + movie.duration() + "ms");
                    handler.post(drawRunner);
                } else {
                    Log.e(TAG, "❌ Failed to decode GIF");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading GIF: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * Load MP4 from file
         */
        private void loadMP4FromFile(File mp4File) {
            try {
                Log.d(TAG, "📂 Loading MP4 from file...");
                
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(mp4File.getAbsolutePath());
                mediaPlayer.setSurface(holder.getSurface());
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f); // Mute
                
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        Log.d(TAG, "✅ MediaPlayer prepared - Duration: " + mp.getDuration() + "ms");
                        if (visible) {
                            mp.start();
                            Log.d(TAG, "▶️ MP4 playback started");
                        }
                    }
                });

                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.e(TAG, "❌ MediaPlayer error: " + what + ", " + extra);
                        return false;
                    }
                });

                mediaPlayer.prepareAsync();

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading MP4: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            
            if ("gif".equalsIgnoreCase(wallpaperType)) {
                if (visible) {
                    handler.post(drawRunner);
                } else {
                    handler.removeCallbacks(drawRunner);
                }
            } else if ("mp4".equalsIgnoreCase(wallpaperType) && mediaPlayer != null) {
                try {
                    if (visible) {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            Log.d(TAG, "▶️ Resumed");
                        }
                    } else {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            Log.d(TAG, "⏸️ Paused");
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                }
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "🖼️ Surface created");
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "🗑️ Surface destroyed");
            
            this.visible = false;
            handler.removeCallbacks(drawRunner);
            
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                    mediaPlayer = null;
                    Log.d(TAG, "✅ MediaPlayer released");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing: " + e.getMessage());
                }
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "📐 Surface: " + width + "x" + height);
            
            this.holder = holder;
            
            if (mediaPlayer != null) {
                mediaPlayer.setSurface(holder.getSurface());
            }
        }

        private void draw() {
            if (visible && movie != null && "gif".equalsIgnoreCase(wallpaperType)) {
                drawGIFFrame();
            }
        }

        private void drawGIFFrame() {
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

                    // Scale to fill screen
                    float scaleX = (float) width / movie.width();
                    float scaleY = (float) height / movie.height();
                    float scale = Math.max(scaleX, scaleY);

                    float scaledWidth = movie.width() * scale;
                    float scaledHeight = movie.height() * scale;
                    float left = (width - scaledWidth) / 2;
                    float top = (height - scaledHeight) / 2;

                    // Draw
                    canvas.drawColor(android.graphics.Color.BLACK);
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
