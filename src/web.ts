import { WebPlugin } from '@capacitor/core';

import type { WallpaperPluginPlugin } from './definitions';

export class WallpaperPluginWeb extends WebPlugin implements WallpaperPluginPlugin {
  async setImageAsWallpaper(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setImageAsLockScreen(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setImageAsWallpaperAndLockScreen(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setHomeAndLockWallpapers(): Promise<{ success: boolean; homeApplied: boolean; lockApplied: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setLiveWallpaper(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async setParallaxWallpaper(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async updateParallaxSettings(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async resetParallaxEffect(): Promise<{ success: boolean }> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isParallaxSupported(): Promise<{ supported: boolean; hasSensor: boolean }> {
    return { supported: false, hasSensor: false };
  }

  async isAvailable(): Promise<{ available: boolean }> {
    return { available: false };
  }
}
