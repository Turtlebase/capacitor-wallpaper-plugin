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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Live Wallpaper Service supporting both GIF and MP4 formats
 */
public class LiveWallpaperService extends WallpaperService {

    private static final String TAG = "LiveWallpaperService";

    @Override
    public Engine onCreateEngine() {
        Log.d(TAG, "Creating wallpaper engine");
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
            loadWallpaperFromUrl();
        }

        private void loadWallpaperFromUrl() {
            SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);
            String wallpaperUrl = prefs.getString("live_wallpaper_url", null);
            wallpaperType = prefs.getString("live_wallpaper_type", "gif");

            if (wallpaperUrl == null) {
                Log.e(TAG, "No wallpaper URL provided");
                return;
            }

            Log.d(TAG, "Loading " + wallpaperType + " from: " + wallpaperUrl);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if ("gif".equalsIgnoreCase(wallpaperType)) {
                            loadGIF(wallpaperUrl);
                        } else if ("mp4".equalsIgnoreCase(wallpaperType)) {
                            loadMP4(wallpaperUrl);
                        } else {
                            Log.e(TAG, "Unsupported type: " + wallpaperType);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading wallpaper: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        private void loadGIF(String gifUrl) {
            try {
                Log.d(TAG, "Downloading GIF...");
                URL url = new URL(gifUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.connect();

                InputStream input = new BufferedInputStream(connection.getInputStream());
                movie = Movie.decodeStream(input);
                input.close();
                connection.disconnect();

                if (movie != null) {
                    Log.d(TAG, "✅ GIF loaded successfully. Duration: " + movie.duration() + "ms");
                    handler.post(drawRunner);
                } else {
                    Log.e(TAG, "❌ Failed to decode GIF");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading GIF: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void loadMP4(String videoUrl) {
            try {
                Log.d(TAG, "Downloading MP4...");
                
                // Download video to cache directory
                URL url = new URL(videoUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.connect();

                // Save to cache
                File cacheDir = getCacheDir();
                File videoFile = new File(cacheDir, "live_wallpaper.mp4");

                InputStream input = new BufferedInputStream(connection.getInputStream());
                FileOutputStream output = new FileOutputStream(videoFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                output.flush();
                output.close();
                input.close();
                connection.disconnect();

                Log.d(TAG, "✅ MP4 downloaded: " + totalBytes + " bytes");

                // Initialize MediaPlayer
                setupMediaPlayer(videoFile.getAbsolutePath());

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading MP4: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void setupMediaPlayer(String videoPath) {
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(videoPath);
                mediaPlayer.setSurface(holder.getSurface());
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f); // Mute the video
                
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        Log.d(TAG, "✅ MediaPlayer prepared. Duration: " + mp.getDuration() + "ms");
                        if (visible) {
                            mp.start();
                            Log.d(TAG, "▶️ MP4 playback started");
                        }
                    }
                });

                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.e(TAG, "❌ MediaPlayer error: what=" + what + " extra=" + extra);
                        return false;
                    }
                });

                mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer mp, int what, int extra) {
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            Log.d(TAG, "✅ Video rendering started");
                        }
                        return false;
                    }
                });

                mediaPlayer.prepareAsync();

            } catch (Exception e) {
                Log.e(TAG, "❌ Error setting up MediaPlayer: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            
            if ("gif".equalsIgnoreCase(wallpaperType)) {
                // Handle GIF visibility
                if (visible) {
                    handler.post(drawRunner);
                } else {
                    handler.removeCallbacks(drawRunner);
                }
            } else if ("mp4".equalsIgnoreCase(wallpaperType) && mediaPlayer != null) {
                // Handle MP4 visibility
                try {
                    if (visible) {
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                            Log.d(TAG, "▶️ MP4 playback resumed");
                        }
                    } else {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            Log.d(TAG, "⏸️ MP4 playback paused");
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error changing playback state: " + e.getMessage());
                }
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "Surface created");
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "Surface destroyed");
            
            this.visible = false;
            handler.removeCallbacks(drawRunner);
            
            // Clean up MediaPlayer
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
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "Surface changed: " + width + "x" + height);
            
            this.holder = holder;
            
            // For MP4, update surface
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

                    // Calculate current frame
                    long now = android.os.SystemClock.uptimeMillis();
                    if (movieStart == 0) {
                        movieStart = now;
                    }

                    int duration = movie.duration();
                    if (duration == 0) {
                        duration = 1000; // Default 1 second if duration is 0
                    }

                    int relTime = (int) ((now - movieStart) % duration);
                    movie.setTime(relTime);

                    // Scale to fill screen while maintaining aspect ratio
                    float scaleX = (float) width / movie.width();
                    float scaleY = (float) height / movie.height();
                    float scale = Math.max(scaleX, scaleY);

                    // Center the GIF
                    float scaledWidth = movie.width() * scale;
                    float scaledHeight = movie.height() * scale;
                    float left = (width - scaledWidth) / 2;
                    float top = (height - scaledHeight) / 2;

                    // Clear canvas
                    canvas.drawColor(android.graphics.Color.BLACK);

                    // Draw GIF
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

            // Schedule next frame
            handler.removeCallbacks(drawRunner);
            if (visible) {
                handler.postDelayed(drawRunner, frameDuration);
            }
        }
    }
}
