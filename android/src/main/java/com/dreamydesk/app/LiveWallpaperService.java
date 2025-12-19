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
 * 
 * ✅ FIXED: Properly switches between live wallpapers without keeping old frame
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
        
        // ✅ NEW: Track loaded wallpaper to detect changes
        private String lastLoadedPath = null;
        private String lastLoadedType = null;
        private long lastLoadedTimestamp = 0;

        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };

        VideoWallpaperEngine() {
            holder = getSurfaceHolder();
        }

        /**
         * ✅ NEW: Clean up all resources before loading new wallpaper
         */
        private void cleanupResources() {
            Log.d(TAG, "🧹 Cleaning up old resources");
            
            // Stop GIF drawing
            handler.removeCallbacks(drawRunner);
            movieStart = 0;
            
            // Release MediaPlayer
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                    mediaPlayer = null;
                    Log.d(TAG, "✅ MediaPlayer released");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing MediaPlayer: " + e.getMessage());
                }
            }
            
            // Clear Movie
            movie = null;
        }

        /**
         * Load video/GIF from the file path saved by WallpaperPlugin
         */
        private void loadWallpaperFromFile() {
            SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);
            String filePath = prefs.getString("live_wallpaper_path", null);
            wallpaperType = prefs.getString("live_wallpaper_type", "gif");
            long timestamp = prefs.getLong("wallpaper_timestamp", 0);

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

            // ✅ Update tracking variables
            lastLoadedPath = filePath;
            lastLoadedType = wallpaperType;
            lastLoadedTimestamp = timestamp;

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

        /**
         * ✅ FIXED: Check if wallpaper changed and reload if necessary
         */
        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            
            if (visible) {
                // ✅ Check if wallpaper file has changed
                SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);
                String currentPath = prefs.getString("live_wallpaper_path", null);
                String currentType = prefs.getString("live_wallpaper_type", "gif");
                long currentTimestamp = prefs.getLong("wallpaper_timestamp", 0);
                
                // Detect if wallpaper changed
                boolean needsReload = false;
                
                if (currentPath != null) {
                    // Check if path changed
                    if (lastLoadedPath == null || !lastLoadedPath.equals(currentPath)) {
                        needsReload = true;
                        Log.d(TAG, "🔄 Path changed: " + lastLoadedPath + " -> " + currentPath);
                    }
                    // Check if type changed
                    if (lastLoadedType == null || !lastLoadedType.equals(currentType)) {
                        needsReload = true;
                        Log.d(TAG, "🔄 Type changed: " + lastLoadedType + " -> " + currentType);
                    }
                    // Check if timestamp changed (new wallpaper downloaded)
                    if (currentTimestamp > lastLoadedTimestamp) {
                        needsReload = true;
                        Log.d(TAG, "🔄 Timestamp changed: " + lastLoadedTimestamp + " -> " + currentTimestamp);
                    }
                }
                
                // If wallpaper changed, cleanup and reload
                if (needsReload) {
                    Log.d(TAG, "🔄 Wallpaper changed - reloading");
                    cleanupResources();
                    loadWallpaperFromFile();
                }
                
                // Resume playback
                if ("gif".equalsIgnoreCase(wallpaperType) && movie != null) {
                    handler.post(drawRunner);
                } else if ("mp4".equalsIgnoreCase(wallpaperType) && mediaPlayer != null) {
                    try {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            Log.d(TAG, "▶️ Resumed MP4");
                        }
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error resuming: " + e.getMessage());
                    }
                }
            } else {
                // Pause playback
                if ("gif".equalsIgnoreCase(wallpaperType)) {
                    handler.removeCallbacks(drawRunner);
                } else if ("mp4".equalsIgnoreCase(wallpaperType) && mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            Log.d(TAG, "⏸️ Paused MP4");
                        }
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error pausing: " + e.getMessage());
                    }
                }
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "🖼️ Surface created");
            this.holder = holder;

            // ✅ Load wallpaper when surface is created
            loadWallpaperFromFile();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "🗑️ Surface destroyed");
            
            this.visible = false;
            
            // ✅ Clean up all resources
            cleanupResources();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "📐 Surface changed: " + width + "x" + height);
            
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
