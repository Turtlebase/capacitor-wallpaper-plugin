
package com.dreamydesk.app;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public class VideoLiveWallpaperService extends WallpaperService {

    private static String videoPath;
    private static final String TAG = "VideoLiveWallpaper";

    public static void setVideoPath(String path) {
        videoPath = path;
        Log.d(TAG, "Video path set to: " + path);
    }

    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    private class VideoEngine extends Engine {
        private MediaPlayer mediaPlayer;
        private boolean isVisible = false;
        private boolean isCreated = false;

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "Surface created");
            isCreated = true;
            initializeMediaPlayer(holder);
        }

        private void initializeMediaPlayer(SurfaceHolder holder) {
            if (mediaPlayer != null) {
                releaseMediaPlayer();
            }
            
            if (videoPath == null || videoPath.isEmpty()) {
                Log.e(TAG, "Video path is null or empty");
                return;
            }
            
            File videoFile = new File(videoPath);
            if (!videoFile.exists()) {
                Log.e(TAG, "Video file does not exist: " + videoPath);
                return;
            }
            
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setSurface(holder.getSurface());
                mediaPlayer.setDataSource(videoPath);
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0, 0);
                
                // Add error listener
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error - what: " + what + ", extra: " + extra);
                    releaseMediaPlayer();
                    return true;
                });
                
                // Add prepared listener
                mediaPlayer.setOnPreparedListener(mp -> {
                    Log.d(TAG, "MediaPlayer prepared");
                    if (isVisible && isCreated) {
                        mp.start();
                        Log.d(TAG, "Playback started");
                    }
                });
                
                // Prepare asynchronously
                mediaPlayer.prepareAsync();
                Log.d(TAG, "MediaPlayer preparing...");
                
            } catch (IOException e) {
                Log.e(TAG, "Error initializing MediaPlayer", e);
                releaseMediaPlayer();
            }
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d(TAG, "Visibility changed: " + visible);
            isVisible = visible;
            
            if (mediaPlayer != null) {
                if (visible) {
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                        Log.d(TAG, "Playback resumed");
                    }
                } else {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        Log.d(TAG, "Playback paused");
                    }
                }
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "Surface destroyed");
            isCreated = false;
            releaseMediaPlayer();
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.d(TAG, "Engine destroyed");
            releaseMediaPlayer();
        }
        
        private void releaseMediaPlayer() {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.reset();
                    mediaPlayer.release();
                    Log.d(TAG, "MediaPlayer released");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing MediaPlayer", e);
                } finally {
                    mediaPlayer = null;
                }
            }
        }
    }
}
