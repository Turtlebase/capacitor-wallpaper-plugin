/**
 * Options for setParallaxWallpaper.
 * The app only needs to supply the image `url` — everything else is optional
 * and lets you customize the range/speed/behaviour of the effect. The plugin
 * downloads the image, builds the parallax wallpaper entirely on the native
 * side, and opens Android's picker so the user can confirm.
 */
export interface ParallaxWallpaperOptions {
  /** URL (or local file:// URI) of the image to use. */
  url: string;

  /**
   * How far the image is allowed to pan, as a percentage (0-100) of the
   * available overscan room. Higher = more dramatic movement.
   * Default: 30
   */
  intensity?: number;

  /**
   * Smoothing/responsiveness of the motion, from 0.01 (very smooth/slow,
   * heavily damped) to 1 (snaps instantly to the target position).
    * Default: 0.2
   */
  speed?: number;

    /**
    * Strength of the pseudo-3D perspective tilt effect.
    * 0 disables perspective rotation, 1 is default, 2 is very strong.
    * Default: 1
    */
    depthStrength?: number;

  /**
   * Enable tilt-based parallax driven by the device's accelerometer.
   * Default: true
   */
  sensorParallax?: boolean;

  /**
   * Enable swipe-based parallax driven by home-screen page scrolling
   * (the standard "pan while swiping between home screens" effect).
   * Default: true
   */
  scrollParallax?: boolean;

  /**
   * How much larger than the screen the source image is rendered
   * (1.05 - 2.0), giving the effect room to pan without showing edges.
   * Higher values allow more pan range but use more memory.
   * Default: 1.3
   */
  overscan?: number;
}

/** Options for tweaking an already-active parallax wallpaper in place. */
export interface ParallaxSettingsUpdate {
  intensity?: number;
  speed?: number;
  depthStrength?: number;
  sensorParallax?: boolean;
  scrollParallax?: boolean;
}

export interface WallpaperPluginPlugin {
  setImageAsWallpaper(options: { url: string }): Promise<{ success: boolean }>;
  setImageAsLockScreen(options: { url: string }): Promise<{ success: boolean }>;
  setImageAsWallpaperAndLockScreen(options: { url: string }): Promise<{ success: boolean }>;
  setLiveWallpaper(options: { url: string; type?: 'gif' | 'mp4' }): Promise<{ success: boolean }>;

  /**
   * Turn an image into a parallax live wallpaper. The plugin downloads the
   * image, generates an oversized "cover" render for pan room, wires up
   * scroll + sensor-based parallax with the given range/speed, and opens
   * the native live wallpaper picker for the user to confirm.
   */
  setParallaxWallpaper(options: ParallaxWallpaperOptions): Promise<{ success: boolean }>;

  /**
   * Update intensity/speed/sensor/scroll settings of the currently active
   * parallax wallpaper live, without re-downloading the image or
   * re-opening the picker.
   */
  updateParallaxSettings(options: ParallaxSettingsUpdate): Promise<{ success: boolean }>;

  /**
   * Reset/stop the parallax effect and clear the system wallpaper set by
   * this plugin, reverting to the device default.
   */
  resetParallaxEffect(): Promise<{ success: boolean }>;

  /** Whether this device supports the parallax live wallpaper feature. */
  isParallaxSupported(): Promise<{ supported: boolean; hasSensor: boolean }>;

  isAvailable(): Promise<{ available: boolean }>;
}
