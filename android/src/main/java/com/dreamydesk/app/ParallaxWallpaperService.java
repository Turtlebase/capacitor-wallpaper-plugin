package com.dreamydesk.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;

import java.io.File;

/**
 * Parallax Wallpaper Service
 *
 * Renders a single (oversized) still image and pans it smoothly in response to:
 *  1. Home-screen swipe offsets (the standard launcher "page scroll" parallax), and
 *  2. Device tilt, read from the accelerometer (the "3D depth" effect seen in
 *     apps like Zedge / KLWP parallax wallpapers).
 *
 * The image is expected to already be oversized relative to the screen (done on the
 * plugin/Java side before saving to disk) so there is room to pan without exposing
 * empty edges. All range/speed/behaviour knobs are read from SharedPreferences and
 * can be changed live by the app via WallpaperPlugin#updateParallaxSettings — this
 * engine listens for preference changes and applies them on the fly, no restart needed.
 */
public class ParallaxWallpaperService extends WallpaperService {

    private static final String TAG = "ParallaxWallpaper";
    static final String PREFS_NAME = "WallpaperPrefs";

    static final String KEY_IMAGE_PATH = "parallax_image_path";
    static final String KEY_INTENSITY = "parallax_intensity";       // 0-100
    static final String KEY_SPEED = "parallax_speed";               // 0.01-1
    static final String KEY_DEPTH_STRENGTH = "parallax_depth_strength"; // 0-2
    static final String KEY_SENSOR_ENABLED = "parallax_sensor_enabled";
    static final String KEY_SCROLL_ENABLED = "parallax_scroll_enabled";
    static final String KEY_TIMESTAMP = "parallax_timestamp";

    private static final float DEFAULT_INTENSITY = 30f;
    private static final float DEFAULT_SPEED = 0.2f;
    private static final float DEFAULT_DEPTH_STRENGTH = 1.0f;
    private static final float MAX_PERSPECTIVE_DEGREES = 5.5f;

    @Override
    public Engine onCreateEngine() {
        return new ParallaxEngine();
    }

    private class ParallaxEngine extends Engine implements SensorEventListener,
            SharedPreferences.OnSharedPreferenceChangeListener {

        private SurfaceHolder holder;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Camera camera = new Camera();
        private final Matrix cameraMatrix = new Matrix();
        private final float[] rotationMatrix = new float[9];
        private final float[] orientation = new float[3];

        private SharedPreferences prefs;
        private SensorManager sensorManager;
        private Sensor accelerometer;
        private Sensor gameRotation;

        private Bitmap bitmap;
        private long loadedTimestamp = -1;

        private boolean visible = true;
        private boolean sensorRegistered = false;

        // ----- configurable, hot-reloadable settings -----
        private volatile float intensity = DEFAULT_INTENSITY; // 0-100
        private volatile float speed = DEFAULT_SPEED;         // 0.01-1
        private volatile float depthStrength = DEFAULT_DEPTH_STRENGTH; // 0-2
        private volatile boolean sensorEnabled = true;
        private volatile boolean scrollEnabled = true;

        // ----- pan geometry (recomputed on surface/bitmap change) -----
        private int surfaceW, surfaceH;
        private float maxPanX, maxPanY;

        // ----- targets driven by input sources, combined then smoothed -----
        private float scrollNormX = 0f; // -1..1, from home-screen offset
        private float tiltNormX = 0f;   // -1..1, low-pass filtered tilt
        private float tiltNormY = 0f;

        // ----- smoothed current pan position (top-left of the draw window) -----
        private float currentPanX = -1f; // -1 sentinel = "not yet initialised"
        private float currentPanY = -1f;

        // ----- velocity state for spring-damped motion (replaces plain lerp) -----
        // Modelling pan as a damped spring (critically-damped-ish) instead of a
        // fixed-rate exponential lerp gives a natural ease-out: it starts quickly
        // toward a new target and settles smoothly, rather than moving at a
        // constant fractional rate every frame regardless of distance.
        private float velocityX = 0f;
        private float velocityY = 0f;

        // Acceleration clamp: caps how much velocity can change in one frame, so a
        // sudden tilt or fast swipe can't cause a jarring instantaneous snap —
        // motion always ramps up/down instead of teleporting in direction/speed.
        private static final float MAX_ACCEL_PER_FRAME = 0.55f; // px/frame^2 scaling factor
        private static final float SPRING_DAMPING = 0.86f;      // 0-1, higher = less overshoot

        // low-pass filter state for accelerometer (stage 1: isolate gravity from noise)
        private final float[] gravity = new float[3];
        private static final float LOW_PASS_ALPHA = 0.22f;

        // low-pass filter state for the *normalized* tilt output (stage 2: smooth
        // out residual jitter/twitchiness after normalization, independent of the
        // raw gravity filter above — this is what removes the "nervous" feel).
        private float smoothedTiltNormX = 0f;
        private float smoothedTiltNormY = 0f;
        private static final float TILT_SMOOTHING_ALPHA = 0.2f;

        private boolean frameScheduled = false;
        private long lastFrameNanos = 0L;
        private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
            frameScheduled = false;
            if (!visible) return;
            draw(frameTimeNanos);
            scheduleNextFrame();
        };

        ParallaxEngine() {
            holder = getSurfaceHolder();
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                gameRotation = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            }
            readSettingsFromPrefs();
        }

        // =========================================================
        // SETTINGS
        // =========================================================
        private void readSettingsFromPrefs() {
            intensity = clamp(prefs.getFloat(KEY_INTENSITY, DEFAULT_INTENSITY), 0f, 100f);
            speed = clamp(prefs.getFloat(KEY_SPEED, DEFAULT_SPEED), 0.01f, 1f);
            depthStrength = clamp(prefs.getFloat(KEY_DEPTH_STRENGTH, DEFAULT_DEPTH_STRENGTH), 0f, 2f);
            sensorEnabled = prefs.getBoolean(KEY_SENSOR_ENABLED, true);
            scrollEnabled = prefs.getBoolean(KEY_SCROLL_ENABLED, true);
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null) return;

            switch (key) {
                case KEY_INTENSITY:
                case KEY_SPEED:
                case KEY_DEPTH_STRENGTH:
                case KEY_SCROLL_ENABLED:
                    readSettingsFromPrefs();
                    break;
                case KEY_SENSOR_ENABLED:
                    readSettingsFromPrefs();
                    updateSensorRegistration();
                    break;
                case KEY_IMAGE_PATH:
                case KEY_TIMESTAMP:
                    loadImageIfChanged();
                    break;
                default:
                    break;
            }
        }

        // =========================================================
        // IMAGE LOADING
        // =========================================================
        private void loadImageIfChanged() {
            String path = prefs.getString(KEY_IMAGE_PATH, null);
            long timestamp = prefs.getLong(KEY_TIMESTAMP, 0);

            if (path == null) {
                Log.e(TAG, "No parallax image path found");
                return;
            }

            if (timestamp == loadedTimestamp && bitmap != null) {
                return; // already loaded, nothing changed
            }

            File file = new File(path);
            if (!file.exists()) {
                Log.e(TAG, "Parallax image file not found: " + path);
                return;
            }

            recycleBitmap();

            // Image was already sized/compressed for panning room by the plugin,
            // so a direct decode is fine here (no need to re-sample).
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // lighter for a long-lived wallpaper bitmap
            bitmap = BitmapFactory.decodeFile(path, opts);

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode parallax image");
                return;
            }

            loadedTimestamp = timestamp;
            // reset smoothing so we don't jump from a stale position or carry
            // over velocity/tilt state from a previously-loaded wallpaper
            currentPanX = -1f;
            currentPanY = -1f;
            velocityX = 0f;
            velocityY = 0f;
            smoothedTiltNormX = 0f;
            smoothedTiltNormY = 0f;
            recomputePanBounds();

            Log.d(TAG, "Parallax image loaded: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        }

        private void recycleBitmap() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }

        private void recomputePanBounds() {
            if (bitmap == null || surfaceW == 0 || surfaceH == 0) return;

            maxPanX = Math.max(0, bitmap.getWidth() - surfaceW);
            maxPanY = Math.max(0, bitmap.getHeight() - surfaceH);

            if (currentPanX < 0) currentPanX = maxPanX / 2f;
            if (currentPanY < 0) currentPanY = maxPanY / 2f;
        }

        // =========================================================
        // SENSOR (TILT)
        // =========================================================
        private void updateSensorRegistration() {
            boolean hasAnyTiltSensor = gameRotation != null || accelerometer != null;
            boolean shouldRegister = visible && sensorEnabled && hasAnyTiltSensor;

            if (shouldRegister && !sensorRegistered) {
                Sensor activeSensor = gameRotation != null ? gameRotation : accelerometer;
                sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_GAME);
                sensorRegistered = true;
            } else if (!shouldRegister && sensorRegistered) {
                sensorManager.unregisterListener(this);
                sensorRegistered = false;
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientation);

                float roll = orientation[2];
                float pitch = orientation[1];
                tiltNormX = clamp((float) (roll / 0.6f), -1f, 1f);
                tiltNormY = clamp((float) (-pitch / 0.6f), -1f, 1f);
            } else if (type == Sensor.TYPE_ACCELEROMETER) {
                // Simple low-pass filter to isolate gravity/tilt from jitter/motion noise
                gravity[0] = LOW_PASS_ALPHA * event.values[0] + (1 - LOW_PASS_ALPHA) * gravity[0];
                gravity[1] = LOW_PASS_ALPHA * event.values[1] + (1 - LOW_PASS_ALPHA) * gravity[1];

                // gravity[0] (x) ranges roughly -9.8..9.8 as the phone tilts left/right.
                // gravity[1] (y) ranges roughly -9.8..9.8 as the phone tilts up/down.
                // Normalize to -1..1 with a soft cap so normal handheld tilt covers the full range.
                float rawX = gravity[0] / 6f;
                float rawY = gravity[1] / 6f;

                tiltNormX = clamp(rawX, -1f, 1f);
                tiltNormY = clamp(-rawY, -1f, 1f); // invert so tilting top-away pans up
            } else {
                return;
            }

            // Second-pass smoothing on the normalized value itself. The gravity
            // low-pass above removes high-frequency accelerometer noise, but the
            // resulting tiltNormX/Y can still carry small hand-tremor jitter that
            // reads as a "nervous" twitch once amplified by intensity. This extra
            // stage removes that without adding perceptible input lag.
            smoothedTiltNormX += (tiltNormX - smoothedTiltNormX) * TILT_SMOOTHING_ALPHA;
            smoothedTiltNormY += (tiltNormY - smoothedTiltNormY) * TILT_SMOOTHING_ALPHA;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }

        // =========================================================
        // HOME SCREEN SWIPE OFFSET
        // =========================================================
        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                                      float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            // xOffset is 0 (left-most home screen) .. 1 (right-most). Centre it to -1..1.
            scrollNormX = clamp((xOffset - 0.5f) * 2f, -1f, 1f);
        }

        // =========================================================
        // DRAW LOOP
        // =========================================================
        private void draw(long frameTimeNanos) {
            if (!visible || bitmap == null) return;

            if (lastFrameNanos == 0L) {
                lastFrameNanos = frameTimeNanos;
            }
            float dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = frameTimeNanos;
            dt = clamp(dt, 1f / 240f, 1f / 20f);

            // Combine input sources. Scroll only drives X (matches launcher paging);
            // tilt drives both X and Y for the "3D" feel. Each source is weighted
            // so combining both doesn't exceed the configured intensity.
            float scrollWeight = scrollEnabled ? 1f : 0f;
            float sensorWeight = sensorEnabled ? 1f : 0f;

            float combinedX = clamp(scrollNormX * scrollWeight * 0.65f + smoothedTiltNormX * sensorWeight * 0.75f, -1f, 1f);
            float combinedY = clamp(smoothedTiltNormY * sensorWeight, -1f, 1f);

            float amplitudeFraction = intensity / 100f;
            float targetPanX = (maxPanX / 2f) + combinedX * (maxPanX / 2f) * amplitudeFraction;
            float targetPanY = (maxPanY / 2f) + combinedY * (maxPanY / 2f) * amplitudeFraction;

            targetPanX = clamp(targetPanX, 0, maxPanX);
            targetPanY = clamp(targetPanY, 0, maxPanY);

            if (currentPanX < 0) currentPanX = targetPanX;
            if (currentPanY < 0) currentPanY = targetPanY;

            // --- Natural ease-out motion: acceleration-clamped damped spring ---
            // Instead of a flat exponential lerp (constant fractional step every
            // frame, which reads as slightly mechanical), treat the pan position
            // as being pulled toward the target by a spring:
            //   1. desired velocity = distance-to-target * speed  (like the old lerp,
            //      but treated as a velocity request, not a direct position jump)
            //   2. actual velocity change is capped per frame (MAX_ACCEL_PER_FRAME),
            //      so direction/speed changes ramp in instead of snapping
            //   3. velocity itself is damped each frame (SPRING_DAMPING), so motion
            //      settles into the target smoothly (ease-out) instead of
            //      overshooting or stopping abruptly
            float desiredVelX = (targetPanX - currentPanX) * speed * (dt * 60f);
            float desiredVelY = (targetPanY - currentPanY) * speed * (dt * 60f);

            float panRangeForAccel = Math.max(1f, Math.max(maxPanX, maxPanY));
            float maxAccel = Math.max(0.01f, speed) * MAX_ACCEL_PER_FRAME * panRangeForAccel * (dt * 60f);
            velocityX += clamp(desiredVelX - velocityX, -maxAccel, maxAccel);
            velocityY += clamp(desiredVelY - velocityY, -maxAccel, maxAccel);

            float damping = (float) Math.pow(SPRING_DAMPING, dt * 60f);
            velocityX *= damping;
            velocityY *= damping;

            currentPanX += velocityX;
            currentPanY += velocityY;
            currentPanX = clamp(currentPanX, 0, maxPanX);
            currentPanY = clamp(currentPanY, 0, maxPanY);

            // --- Subtle depth "breathing": a faint scale-up as tilt magnitude
            // increases, on top of the pan. Real depth-layered parallax (e.g.
            // KLWP-style) reads as 3D partly because elements grow slightly
            // closer as you tilt toward them, not just because they slide. A
            // tiny scale range (up to +2%) is enough to sell that impression
            // without ever exposing empty edges (bitmap is already oversized
            // via `overscan`, which reserves room beyond what panning alone uses).
            float tiltMagnitude = clamp(
                (float) Math.sqrt(smoothedTiltNormX * smoothedTiltNormX + smoothedTiltNormY * smoothedTiltNormY),
                0f, 1f
            );
            float breatheScale = 1f + tiltMagnitude * amplitudeFraction * 0.02f;

            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;

                canvas.drawColor(Color.BLACK);
                canvas.save();
                canvas.translate(-currentPanX, -currentPanY);

                float pivotX = surfaceW / 2f + currentPanX;
                float pivotY = surfaceH / 2f + currentPanY;
                float perspectiveDegrees = MAX_PERSPECTIVE_DEGREES * depthStrength;
                float perspectiveX = smoothedTiltNormX * amplitudeFraction * perspectiveDegrees;
                float perspectiveY = smoothedTiltNormY * amplitudeFraction * perspectiveDegrees;
                camera.save();
                camera.rotateY(perspectiveX);
                camera.rotateX(-perspectiveY);
                camera.getMatrix(cameraMatrix);
                camera.restore();
                cameraMatrix.preTranslate(-pivotX, -pivotY);
                cameraMatrix.postTranslate(pivotX, pivotY);
                canvas.concat(cameraMatrix);

                if (breatheScale != 1f) {
                    canvas.scale(breatheScale, breatheScale, pivotX, pivotY);
                }
                canvas.drawBitmap(bitmap, 0, 0, paint);
                canvas.restore();
            } catch (Exception e) {
                Log.e(TAG, "Draw error: " + e.getMessage());
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {}
                }
            }

        }

        private void scheduleNextFrame() {
            if (!visible || frameScheduled) return;
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }

        private void cancelFrameLoop() {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            frameScheduled = false;
        }

        // =========================================================
        // LIFECYCLE
        // =========================================================
        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;

            if (visible) {
                loadImageIfChanged();
                updateSensorRegistration();
                lastFrameNanos = 0L;
                scheduleNextFrame();
            } else {
                cancelFrameLoop();
                updateSensorRegistration(); // will unregister since visible=false
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            this.holder = holder;
            loadImageIfChanged();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.holder = holder;
            surfaceW = width;
            surfaceH = height;
            recomputePanBounds();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            cancelFrameLoop();
            updateSensorRegistration();
            if (prefs != null) {
                prefs.unregisterOnSharedPreferenceChangeListener(this);
            }
            recycleBitmap();
        }
    }
}
