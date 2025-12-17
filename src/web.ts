import { WebPlugin } from '@capacitor/core';

import type { WallpaperPlugin } from './definitions';

export class WallpaperWeb extends WebPlugin implements WallpaperPlugin {
  async setStatic(): Promise<{ success: boolean }> {
    throw this.unimplemented('setStatic is not implemented on web.');
  }

  async setLive(): Promise<{ 
    success: boolean; 
    requiresUserAction?: boolean;
    method?: string;
  }> {
    throw this.unimplemented('setLive is not implemented on web.');
  }

  async isSupported(): Promise<{ supported: boolean; platform: string }> {
    return {
      supported: false,
      platform: 'web',
    };
  }
}
