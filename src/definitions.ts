export interface WallpaperPlugin {
  /**
   * ----------------------------------------
   * STATIC WALLPAPERS
   * ----------------------------------------
   * Sets image as wallpaper using system Intent chooser
   * - NO VISIBLE RESTART (app stays alive)
   * - Automatic fallback to direct method if Intent fails
   * - path: Local file path (NOT URL)
   * - Example: /data/data/com.app/cache/wallpapers/image.jpg
   */

  setImageAsWallpaper(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  setImageAsLockScreen(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  setImageAsWallpaperAndLockScreen(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  /**
   * ----------------------------------------
   * LIVE WALLPAPER (VIDEO)
   * ----------------------------------------
   * Sets video as live wallpaper
   * - Opens system live wallpaper picker
   * - path: Local video file path
   * - Supports MP4, GIF
   */

  setLiveWallpaper(options: {
    path: string;
  }): Promise<{
    success: boolean;
  }>;

  /**
   * ----------------------------------------
   * AVAILABILITY CHECK
   * ----------------------------------------
   * Check if wallpaper plugin is available
   */

  isAvailable(): Promise<{
    available: boolean;
  }>;
}
