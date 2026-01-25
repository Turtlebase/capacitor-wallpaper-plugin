export interface WallpaperPlugin {
  /**
   * Set image as home screen wallpaper
   * Uses Intent chooser (NO VISIBLE RESTART)
   */
  setImageAsWallpaper(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  /**
   * Set image as lock screen wallpaper
   * Uses Intent chooser (NO VISIBLE RESTART)
   */
  setImageAsLockScreen(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  /**
   * Set image as both home and lock screen wallpaper
   * Uses Intent chooser (NO VISIBLE RESTART)
   */
  setImageAsWallpaperAndLockScreen(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  /**
   * Set video as live wallpaper
   */
  setLiveWallpaper(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  /**
   * Check if plugin is available
   */
  isAvailable(): Promise<{
    available: boolean;
  }>;
}
