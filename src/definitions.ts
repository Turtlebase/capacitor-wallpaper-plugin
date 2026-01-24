export interface WallpaperPluginPlugin {
  /**
   * ----------------------------------------
   * STATIC WALLPAPERS (Gallery-style)
   * ----------------------------------------
   * IMPORTANT:
   * - `path` = local file path
   * - Example: /data/data/com.app/cache/wallpapers/wallpaper_123.jpg
   * - Plugin NEVER downloads images
   */

  setImageAsWallpaper(options: {
    path: string;        // local file path
  }): Promise<{
    success: boolean;
  }>;

  setImageAsLockScreen(options: {
    path: string;        // local file path
  }): Promise<{
    success: boolean;
  }>;

  setImageAsWallpaperAndLockScreen(options: {
    path: string;        // local file path
  }): Promise<{
    success: boolean;
  }>;

  /**
   * ----------------------------------------
   * LIVE WALLPAPER
   * ----------------------------------------
   * - Supports GIF / MP4
   * - Path OR URL allowed (plugin may cache internally)
   */

  setLiveWallpaper(options: {
    path?: string;       // preferred (local file)
    url?: string;        // optional fallback
    type?: 'gif' | 'mp4';
  }): Promise<{
    success: boolean;
  }>;

  /**
   * ----------------------------------------
   * AVAILABILITY CHECK
   * ----------------------------------------
   */

  isAvailable(): Promise<{
    available: boolean;
  }>;
}
