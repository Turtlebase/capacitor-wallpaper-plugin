# Parallax Setup (Simple)

This guide is for app-side integration in your IDE with a quick preview screen.

## 1) Install Plugin In Your App

```bash
npm install capacitor-wallpaper-plugin
npx cap sync android
```

If you are testing directly from GitHub branch:

```bash
npm install github:Turtlebase/capacitor-wallpaper-plugin#main
npx cap sync android
```

## 2) Minimal Preview Page (React)

Create a screen like this (example: `ParallaxPreviewPage.tsx`).

```tsx
import React, { useEffect, useState } from 'react';
import { registerPlugin, Capacitor } from '@capacitor/core';
import type { WallpaperPluginPlugin } from 'capacitor-wallpaper-plugin/dist/esm/definitions';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin');

export default function ParallaxPreviewPage() {
  const [supported, setSupported] = useState(false);
  const [loading, setLoading] = useState(false);

  // Custom config
  const imageUrl = 'https://your-cdn.com/wallpapers/your-image.jpg';
  const intensity = 40;       // 0..100
  const speed = 0.3;          // 0.01..1
  const depthStrength = 1.2;  // 0..2
  const overscan = 1.3;       // 1.05..2

  useEffect(() => {
    (async () => {
      if (Capacitor.getPlatform() !== 'android') return;
      const res = await WallpaperPlugin.isParallaxSupported();
      setSupported(res.supported);
    })();
  }, []);

  const setParallax = async () => {
    try {
      setLoading(true);
      await WallpaperPlugin.setParallaxWallpaper({
        url: imageUrl,
        intensity,
        speed,
        depthStrength,
        overscan,
        sensorParallax: true,
        scrollParallax: true,
      });
      alert('Wallpaper picker opened. Tap Set inside Android picker.');
    } catch (e: any) {
      alert(e?.message || 'Failed to open wallpaper picker');
    } finally {
      setLoading(false);
    }
  };

  const updateLiveSettings = async () => {
    try {
      await WallpaperPlugin.updateParallaxSettings({
        intensity: 50,
        speed: 0.35,
        depthStrength: 1.4,
        sensorParallax: true,
        scrollParallax: true,
      });
      alert('Parallax settings updated live');
    } catch (e: any) {
      alert(e?.message || 'Failed to update settings');
    }
  };

  if (!supported) {
    return <div style={{ padding: 16 }}>Parallax not supported on this device.</div>;
  }

  return (
    <div style={{ padding: 16, display: 'grid', gap: 12 }}>
      <h2>Parallax Preview</h2>

      {/* Simple visual preview card in app UI */}
      <div
        style={{
          height: 240,
          borderRadius: 16,
          backgroundImage: `url(${imageUrl})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          boxShadow: '0 12px 32px rgba(0,0,0,0.25)',
        }}
      />

      <button onClick={setParallax} disabled={loading}>
        {loading ? 'Opening picker...' : 'Set As Parallax Wallpaper'}
      </button>

      <button onClick={updateLiveSettings}>Update Parallax Settings Live</button>
    </div>
  );
}
```

## 3) Notes

- The plugin uses a flat JPG/WebP image and creates pseudo-3D movement from tilt + perspective.
- For smooth result on most devices, start with:
  - intensity: 35 to 50
  - speed: 0.25 to 0.4
  - depthStrength: 1.0 to 1.5
  - overscan: 1.2 to 1.35
- updateParallaxSettings changes currently active parallax wallpaper instantly (no re-download).
