package com.dreamydesk.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.io.FileInputStream;

/**
 * Live Wallpaper Service
 * Supports GIF + MP4
 * MP4 uses ExoPlayer (Media3)
 * GIF uses Movie (manual rendering)
 */
public class LiveWallpaperService extends WallpaperService {

    private static final String TAG = "LiveWallpaperService";

    @Override
    public Engine onCreateEngine() {
        return new VideoWallpaperEngine();
    }

    private class VideoWallpaperEngine extends Engine {

        // ===== COMMON =====
        private final Handler handler = new Handler();
        private SurfaceHolder holder;
        private boolean visible = true;

        private String wallpaperType;
        private String lastLoadedPath;
        private String lastLoadedType;
        private long lastLoadedTimestamp;

        // ===== GIF =====
        private static final int GIF_FRAME_DELAY = 40; // ~25 FPS
        private Movie movie;
        private long movieStart;

        // ===== MP4 (ExoPlayer) =====
        private ExoPlayer exoPlayer;

        private final Runnable drawRunner = this::draw;

        VideoWallpaperEngine() {
            holder = getSurfaceHolder();
        }

        // =========================================================
        // CLEANUP
        // =========================================================
        private void cleanupResources() {
            Log.d(TAG, "🧹 Cleaning up resources");

            handler.removeCallbacks(drawRunner);
            movie = null;
            movieStart = 0;

            if (exoPlayer != null) {
                exoPlayer.stop();
                exoPlayer.release();
                exoPlayer = null;
                Log.d(TAG, "✅ ExoPlayer released");
            }
        }

        // =========================================================
        // LOAD WALLPAPER
        // =========================================================
        private void loadWallpaperFromFile() {
            SharedPreferences prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE);

            String filePath = prefs.getString("live_wallpaper_path", null);
            wallpaperType = prefs.getString("live_wallpaper_type", "gif");
            long timestamp = prefs.getLong("wallpaper_timestamp", 0);

            if (filePath == null) {
                Log.e(TAG, "❌ No wallpaper path found");
                return;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "❌ Wallpaper file not found");
                return;
            }

            lastLoadedPath = filePath;
            lastLoadedType = wallpaperType;
            lastLoadedTimestamp = timestamp;

            cleanupResources();

            if ("gif".equalsIgnoreCase(wallpaperType)) {
                loadGIF(file);
            } else if ("mp4".equalsIgnoreCase(wallpaperType)) {
                loadMP4(file);
            }
        }

        // =========================================================
        // GIF
        // =========================================================
        private void loadGIF(File gifFile) {
            try {
                FileInputStream fis = new FileInputStream(gifFile);
                movie = Movie.decodeStream(fis);
                fis.close();

                if (movie != null) {
                    handler.post(drawRunner);
                    Log.d(TAG, "✅ GIF loaded");
                }
            } catch (Exception e) {
                Log.e(TAG, "GIF load error", e);
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
                if (canvas == null) return;

                int width = canvas.getWidth();
                int height = canvas.getHeight();

                long now = android.os.SystemClock.uptimeMillis();
                if (movieStart == 0) movieStart = now;

                int duration = movie.duration();
                if (duration == 0) duration = 1000;

                movie.setTime((int) ((now - movieStart) % duration));

                int gifW = movie.width();
                int gifH = movie.height();

                float screenRatio = (float) width / height;
                float gifRatio = (float) gifW / gifH;

                float scale;
                float dx = 0, dy = 0;

                if (gifRatio > screenRatio) {
                    scale = (float) height / gifH;
                    dx = (width - gifW * scale) / 2f;
                } else {
                    scale = (float) width / gifW;
                    dy = (height - gifH * scale) / 2f;
                }

                canvas.drawColor(android.graphics.Color.BLACK);
                canvas.save();
                canvas.translate(dx, dy);
                canvas.scale(scale, scale);
                movie.draw(canvas, 0, 0);
                canvas.restore();

            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }

            handler.removeCallbacks(drawRunner);
            if (visible) handler.postDelayed(drawRunner, GIF_FRAME_DELAY);
        }

        // =========================================================
        // MP4 (EXOPLAYER)
        // =========================================================
        private void loadMP4(File mp4File) {
            Log.d(TAG, "🎬 Loading MP4 with ExoPlayer");

            exoPlayer = new ExoPlayer.Builder(getApplicationContext()).build();
            exoPlayer.setVideoSurface(holder.getSurface());
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            exoPlayer.setVolume(0f);

            MediaItem mediaItem = MediaItem.fromUri(mp4File.toURI().toString());
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();

            if (visible) exoPlayer.play();
        }

        // =========================================================
        // LIFECYCLE
        // =========================================================
        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;

            if (visible) {
                loadWallpaperFromFile();

                if (exoPlayer != null) exoPlayer.play();
                if (movie != null) handler.post(drawRunner);

            } else {
                handler.removeCallbacks(drawRunner);
                if (exoPlayer != null) exoPlayer.pause();
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            this.holder = holder;
            loadWallpaperFromFile();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.holder = holder;

            if (exoPlayer != null) {
                exoPlayer.setVideoSurface(holder.getSurface());
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            cleanupResources();
        }
    }
}
