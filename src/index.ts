import { registerPlugin } from '@capacitor/core';

import type { WallpaperPluginPlugin } from './definitions';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin', {
  web: () => import('./web').then((m) => new m.WallpaperPluginWeb()),
});

export * from './definitions';
export { WallpaperPlugin };
