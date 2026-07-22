# Dual Wallpaper Setup Guide

This is the exact app-side setup for the dual wallpaper feature in this plugin.

Use this when your app needs to set:
- one image for the home screen
- one different image for the lock screen
- both together in one action

This is the correct flow for wallpaper picker screens, gallery-style wallpaper selection, or any app that wants a home/lock wallpaper pair.

---

## 1. Install and sync the plugin

In your app project run:

```bash
npm install github:Turtlebase/capacitor-wallpaper-plugin
npx cap sync android
```

---

## 2. Register the plugin in your app

Use this exact import pattern:

```ts
import { registerPlugin } from '@capacitor/core';
import type { WallpaperPluginPlugin } from 'capacitor-wallpaper-plugin';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin');
```

---

## 3. Use this exact method

This is the method your app should call for dual wallpaper setup:

```ts
await WallpaperPlugin.setHomeAndLockWallpapers({
  homeUrl: 'https://example.com/home-wallpaper.jpg',
  lockUrl: 'https://example.com/lock-wallpaper.jpg',
});
```

### Success result

On success the plugin resolves with:

```ts
{
  success: true,
  homeApplied: true,
  lockApplied: true
}
```

### Important behavior
- The operation is all-or-nothing.
- If either image fails, neither wallpaper changes.
- On Android versions before 7.0, the lock-screen part is not supported by the OS API, so the call rejects with a clear message.

---

## 4. Full working example (copy-paste ready)

This is a complete React example for an app screen.

```tsx
import React, { useState } from 'react';
import { registerPlugin } from '@capacitor/core';
import type { WallpaperPluginPlugin } from 'capacitor-wallpaper-plugin';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin');

type Status =
  | { kind: 'idle' }
  | { kind: 'setting' }
  | { kind: 'success' }
  | { kind: 'error'; message: string };

export const DualWallpaperScreen: React.FC = () => {
  const [homeUrl, setHomeUrl] = useState('https://example.com/home-wallpaper.jpg');
  const [lockUrl, setLockUrl] = useState('https://example.com/lock-wallpaper.jpg');
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  const handleSetBoth = async () => {
    setStatus({ kind: 'setting' });

    try {
      const result = await WallpaperPlugin.setHomeAndLockWallpapers({
        homeUrl,
        lockUrl,
      });

      if (result.success) {
        setStatus({ kind: 'success' });
      } else {
        setStatus({ kind: 'error', message: 'Could not set both wallpapers.' });
      }
    } catch (e: any) {
      setStatus({
        kind: 'error',
        message: e?.message ?? 'Failed to set wallpapers.',
      });
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 16 }}>
      <h2>Dual Wallpaper Setup</h2>

      <label>Home wallpaper URL</label>
      <input
        value={homeUrl}
        onChange={(e) => setHomeUrl(e.target.value)}
        placeholder="https://example.com/home-wallpaper.jpg"
        style={{ padding: 10 }}
      />

      <label>Lock wallpaper URL</label>
      <input
        value={lockUrl}
        onChange={(e) => setLockUrl(e.target.value)}
        placeholder="https://example.com/lock-wallpaper.jpg"
        style={{ padding: 10 }}
      />

      <button onClick={handleSetBoth} disabled={status.kind === 'setting'}>
        {status.kind === 'setting' ? 'Setting…' : 'Set Both Wallpapers'}
      </button>

      {status.kind === 'error' && <p style={{ color: 'crimson' }}>{status.message}</p>}
      {status.kind === 'success' && <p style={{ color: 'green' }}>Home and lock wallpapers applied successfully.</p>}
    </div>
  );
};
```

---

## 5. Recommended app-side flow

Use this app flow:

1. Let the user select a home wallpaper
2. Let the user select a lock wallpaper
3. Call `setHomeAndLockWallpapers`
4. Show one shared success/error state for both wallpapers

This prevents the app from ending up in a mismatched state where only one side updated.

---

## 6. Common error messages

You should handle these errors in the app UI:

```text
Failed to download home image — no wallpaper was changed
Failed to download lock image — no wallpaper was changed
Home wallpaper was set, but this device does not support a separate lock screen wallpaper (requires Android 7.0+)
```

---

## 7. Exact requirement summary

For this feature to work correctly in your app:
- install the plugin
- sync Capacitor
- register the plugin with the exact name `WallpaperPlugin`
- call `setHomeAndLockWallpapers({ homeUrl, lockUrl })`
- handle success and rejection in your UI

This is the exact dual wallpaper flow the plugin is built to support.
