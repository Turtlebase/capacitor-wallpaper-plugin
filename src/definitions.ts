export interface WallpaperPluginPlugin {
  setImageAsWallpaper(options: { url: string }): Promise<{ success: boolean }>;
  setImageAsLockScreen(options: { url: string }): Promise<{ success: boolean }>;
  setImageAsWallpaperAndLockScreen(options: { url: string }): Promise<{ success: boolean }>;
  setLiveWallpaper(options: { url: string; type?: 'gif' | 'mp4' }): Promise<{ success: boolean }>;
  isAvailable(): Promise<{ available: boolean }>;
}
