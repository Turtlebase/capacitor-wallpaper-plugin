package com.dreamydesk.app;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public class VideoLiveWallpaperService extends WallpaperService {
    private static final String TAG = "VideoLiveWallpaper";
    private static String videoPath = null;

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
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            Log.d(TAG, "Engine onCreate");
            setTouchEventsEnabled(false);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "Surface created");
            isCreated = true;
            initializeMediaPlayer(holder);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "Surface changed: " + width + "x" + height);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "Surface destroyed");
            isCreated = false;
            releaseMediaPlayer();
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
                mediaPlayer.setVolume(0f, 0f);
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.e(TAG, "MediaPlayer error - what: " + what + ", extra: " + extra);
                        releaseMediaPlayer();
                        return true;
                    }
                });
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        Log.d(TAG, "MediaPlayer prepared successfully");
                        if (isVisible && isCreated) {
                            mp.start();
                            Log.d(TAG, "Playback started");
                        }
                    }
                });
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        Log.d(TAG, "Video completed, restarting...");
                        if (isVisible && isCreated) {
                            mp.start();
                        }
                    }
                });
                mediaPlayer.prepareAsync();
                Log.d(TAG, "MediaPlayer preparing asynchronously...");
            } catch (IOException e) {
                Log.e(TAG, "Error initializing MediaPlayer", e);
                releaseMediaPlayer();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid video file", e);
                releaseMediaPlayer();
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception", e);
                releaseMediaPlayer();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error initializing MediaPlayer", e);
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
