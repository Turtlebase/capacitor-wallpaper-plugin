export interface WallpaperPlugin {
  /**
   * Set a static image as wallpaper
   * 
   * @param options - Configuration options for setting static wallpaper
   * @returns Promise with success status
   * 
   * @example
   * ```typescript
   * await Wallpaper.setStatic({
   *   imageUrl: 'https://example.com/image.jpg',
   *   screen: 'HOME'
   * });
   * ```
   */
  setStatic(options: {
    /**
     * URL of the image to set as wallpaper
     */
    imageUrl: string;
    /**
     * Which screen to set the wallpaper on
     * - 'HOME': Home screen only
     * - 'LOCK': Lock screen only
     * - 'BOTH': Both home and lock screens
     * @default 'HOME'
     */
    screen?: 'HOME' | 'LOCK' | 'BOTH';
  }): Promise<{ success: boolean }>;

  /**
   * Set a video as live wallpaper
   * 
   * @param options - Configuration options for setting live wallpaper
   * @returns Promise with success status and user action requirement
   * 
   * @example
   * ```typescript
   * const result = await Wallpaper.setLive({
   *   videoUrl: 'https://example.com/video.mp4'
   * });
   * 
   * if (result.requiresUserAction) {
   *   console.log('User needs to select wallpaper from picker');
   * }
   * ```
   */
  setLive(options: {
    /**
     * URL of the video to set as live wallpaper
     * Supports MP4 format with H.264 codec
     */
    videoUrl: string;
  }): Promise<{
    /**
     * Whether the operation was successful
     */
    success: boolean;
    /**
     * Whether user interaction is required to complete setup
     */
    requiresUserAction?: boolean;
    /**
     * Method used to set wallpaper ('direct' or 'picker')
     */
    method?: string;
  }>;

  /**
   * Check if wallpaper functionality is supported on this device
   * 
   * @returns Promise with support status and platform information
   * 
   * @example
   * ```typescript
   * const { supported, platform } = await Wallpaper.isSupported();
   * if (supported) {
   *   console.log('Wallpaper is supported on', platform);
   * }
   * ```
   */
  isSupported(): Promise<{
    /**
     * Whether wallpaper is supported on this device
     */
    supported: boolean;
    /**
     * Current platform ('android', 'ios', 'web')
     */
    platform: string;
  }>;
}
