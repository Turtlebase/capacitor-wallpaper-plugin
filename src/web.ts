import { WebPlugin } from '@capacitor/core';
import type { WallpaperPlugin } from './definitions';

export class WallpaperWeb extends WebPlugin implements WallpaperPlugin {
  async setImageAsWallpaper(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setImageAsLockScreen(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setImageAsWallpaperAndLockScreen(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setLiveWallpaper(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isAvailable(): Promise<{ available: boolean }> {
    return { available: false };
  }
}
