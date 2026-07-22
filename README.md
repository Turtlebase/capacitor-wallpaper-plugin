# capacitor-wallpaper-plugin

A Capacitor plugin for setting static and live wallpapers on Android devices.

[![npm version](https://badge.fury.io/js/capacitor-wallpaper-plugin.svg)](https://badge.fury.io/js/capacitor-wallpaper-plugin)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Features

✅ Set static images as wallpaper (Home, Lock, or Both screens)  
✅ Set **different images** for Home and Lock screen in one call, all-or-nothing  
✅ Set videos as live wallpaper  
✅ Set images as **parallax live wallpaper** — pans with home-screen swipe and/or device tilt, with configurable range/speed  
✅ Auto-loop video playback  
✅ Battery-optimized (pauses when screen is off)  
✅ Memory-efficient implementation  
✅ TypeScript support  
✅ Android 5.0+ support  

## Installation

```bash
npm install github:Turtlebase/capacitor-wallpaper-plugin
npx cap sync android
```

## Usage

### Import the plugin

```typescript
import { Wallpaper } from 'capacitor-wallpaper-plugin';
```

### Set Static Wallpaper

```typescript
// Set on home screen only
await Wallpaper.setStatic({
  imageUrl: 'https://example.com/image.jpg',
  screen: 'HOME'
});

// Set on lock screen only
await Wallpaper.setStatic({
  imageUrl: 'https://example.com/image.jpg',
  screen: 'LOCK'
});

// Set on both screens
await Wallpaper.setStatic({
  imageUrl: 'https://example.com/image.jpg',
  screen: 'BOTH'
});
```

> **Note:** the examples above use a `setStatic({ screen })` shape for
> illustration. The actual current API exposes three separate methods
> instead — `setImageAsWallpaper`, `setImageAsLockScreen`, and
> `setImageAsWallpaperAndLockScreen` — shown below with real usage.

```typescript
// Home screen only
await WallpaperPlugin.setImageAsWallpaper({ url: 'https://example.com/image.jpg' });

// Lock screen only
await WallpaperPlugin.setImageAsLockScreen({ url: 'https://example.com/image.jpg' });

// Same image, both screens
await WallpaperPlugin.setImageAsWallpaperAndLockScreen({ url: 'https://example.com/image.jpg' });
```

### Set Different Wallpapers for Home and Lock Screen

If you want a DIFFERENT image on each screen — e.g. the user picks one
wallpaper for home and another for lock — use `setHomeAndLockWallpapers`
instead of calling the single-image methods twice:

```typescript
const result = await WallpaperPlugin.setHomeAndLockWallpapers({
  homeUrl: 'https://example.com/home-wallpaper.jpg',
  lockUrl: 'https://example.com/lock-wallpaper.jpg',
});

// result: { success: true, homeApplied: true, lockApplied: true }
```

Both images download concurrently (not one after another), and the whole
operation is **all-or-nothing**: if either download fails, neither screen
is changed. This guarantees home always ends up showing the home image and
lock always ends up showing the lock image — there is no in-between state
where only one screen updated and the pairing is mismatched.

If the call rejects, the error message tells you which side failed:

```typescript
try {
  await WallpaperPlugin.setHomeAndLockWallpapers({ homeUrl, lockUrl });
} catch (e) {
  // e.message examples:
  // "Failed to download home image — no wallpaper was changed"
  // "Failed to download lock image — no wallpaper was changed"
  // "Home wallpaper was set, but this device does not support a separate
  //  lock screen wallpaper (requires Android 7.0+)"
}
```

That last case — pre-Android 7.0 devices — is the one scenario where home
can apply without lock: `WallpaperManager.FLAG_LOCK` doesn't exist before
Android 7.0 (Nougat), so a distinct lock-screen image isn't possible on
those OS versions. The rejection message says so explicitly rather than
silently pretending both were set.

An example screen (`example/DualWallpaperScreen.tsx`) shows a typical
"pick home / pick lock / Set Both" flow with a single combined success/error
state, matching how most wallpaper apps present this feature.

### Set Live Wallpaper (Video)

```typescript
const result = await Wallpaper.setLive({
  videoUrl: 'https://example.com/video.mp4'
});

if (result.requiresUserAction) {
  console.log('User needs to select the wallpaper from the picker');
}
```

### Set Parallax Wallpaper

Turns any image into a parallax live wallpaper — the image pans as the user
swipes between home screens and/or tilts the device, just like the parallax
wallpapers in apps like Zedge. You only need to supply the image URL; the
plugin downloads it, renders it at an oversized "cover" resolution for pan
room, and builds the whole effect natively. Range, speed, and which inputs
drive the motion are all optional and fully configurable.

> ⚠️ The examples below use the plugin's actual exported object,
> `WallpaperPlugin` (see `src/index.ts`), and its real method names —
> not the `Wallpaper.setStatic(...)`-style names shown elsewhere in this
> README, which don't match the shipped API.

```typescript
import { WallpaperPlugin } from 'capacitor-wallpaper-plugin';

// Minimal — sensible defaults for everything
await WallpaperPlugin.setParallaxWallpaper({
  url: 'https://example.com/image.jpg',
});

// Fully customized
await WallpaperPlugin.setParallaxWallpaper({
  url: 'https://example.com/image.jpg',
  intensity: 40,        // 0-100, how far the image can pan (default 30)
  speed: 0.15,           // 0.01-1, motion smoothing/responsiveness (default 0.12)
  sensorParallax: true,  // pan with device tilt (default true)
  scrollParallax: true,  // pan with home-screen swipe (default true)
  overscan: 1.4,          // 1.05-2.0, how much bigger than the screen to render (default 1.3)
});
```

Tweak an already-active parallax wallpaper without re-downloading the image
or reopening the picker:

```typescript
await WallpaperPlugin.updateParallaxSettings({
  intensity: 60,
  sensorParallax: false, // e.g. turn off tilt, keep only swipe-based motion
});
```

Reset the effect and revert to the device's default wallpaper:

```typescript
await WallpaperPlugin.resetParallaxEffect();
```

Check device support before showing a "Set as Parallax" button:

```typescript
const { supported, hasSensor } = await WallpaperPlugin.isParallaxSupported();
// supported: device can run live wallpapers at all
// hasSensor: device has a motion sensor, so tilt-based parallax will work
```

### Check if Supported

```typescript
const { supported, platform } = await Wallpaper.isSupported();

if (supported) {
  console.log('Wallpaper is supported on', platform);
} else {
  console.log('Wallpaper is not supported');
}
```

## API

### `setStatic(options)`

Set a static image as wallpaper.

**Parameters:**
- `imageUrl` (string): URL of the image to set
- `screen` (string, optional): Which screen to set the wallpaper on
  - `'HOME'` - Home screen only (default)
  - `'LOCK'` - Lock screen only
  - `'BOTH'` - Both home and lock screens

**Returns:** `Promise<{ success: boolean }>`

### `setHomeAndLockWallpapers(options)`

Set a different image on the home screen and lock screen in one call.

**Parameters:**
- `homeUrl` (string, required): URL of the image to set on the home screen
- `lockUrl` (string, required): URL of the image to set on the lock screen

**Returns:** `Promise<{ success: boolean; homeApplied: boolean; lockApplied: boolean }>`

**Behavior:**
- Both URLs download concurrently.
- All-or-nothing: if either download fails, neither screen is changed — the
  promise rejects and the message states which image failed.
- On Android versions before 7.0 (no `FLAG_LOCK` support), the home image is
  applied but the promise still rejects, explaining the lock screen image
  could not be set separately on that OS version.

### `setLive(options)`

Set a video as live wallpaper.

**Parameters:**
- `videoUrl` (string): URL of the video to set (MP4 format with H.264 codec recommended)

**Returns:** `Promise<{ success: boolean; requiresUserAction?: boolean; method?: string }>`

### `isSupported()`

Check if wallpaper functionality is supported on the current device.

**Returns:** `Promise<{ supported: boolean; platform: string }>`

### `setParallaxWallpaper(options)`

Download an image and set it as a parallax live wallpaper, panning with
home-screen swipe and/or device tilt.

**Parameters:**
- `url` (string): URL (or `file://` URI) of the image to use
- `intensity` (number, optional): 0-100 pan range, default `30`
- `speed` (number, optional): 0.01-1 motion smoothing, default `0.12`
- `sensorParallax` (boolean, optional): pan with device tilt, default `true`
- `scrollParallax` (boolean, optional): pan with home-screen swipe, default `true`
- `overscan` (number, optional): 1.05-2.0, how much bigger than the screen the source image is rendered, default `1.3`

**Returns:** `Promise<{ success: boolean }>`

### `updateParallaxSettings(options)`

Update the intensity/speed/sensor/scroll settings of the currently active
parallax wallpaper in place, live — no re-download or re-picker step.

**Parameters:** any subset of `intensity`, `speed`, `sensorParallax`, `scrollParallax`

**Returns:** `Promise<{ success: boolean }>`

### `resetParallaxEffect()`

Stop the parallax effect and clear the system wallpaper set by this plugin,
reverting to the device default.

**Returns:** `Promise<{ success: boolean }>`

### `isParallaxSupported()`

Check whether this device can run the parallax wallpaper feature.

**Returns:** `Promise<{ supported: boolean; hasSensor: boolean }>`

## Video Requirements

For best results, use videos with these specifications:

- **Format:** MP4
- **Codec:** H.264 (AVC)
- **Resolution:** 1080p or lower
- **Frame Rate:** 30fps
- **Bitrate:** 2-5 Mbps
- **Size:** Under 50MB

### Convert Video with FFmpeg

```bash
ffmpeg -i input.mp4 \
  -c:v libx264 \
  -preset slow \
  -crf 23 \
  -vf scale=1080:1920 \
  -r 30 \
  -an \
  output.mp4
```

## Platform Support

| Platform | Supported |
|----------|-----------|
| Android  | ✅ Yes    |
| iOS      | ❌ No     |
| Web      | ❌ No     |

## Requirements

- Capacitor 7.0+
- Android 5.0+ (API 21+)
- Java 17

## Example App

```typescript
import React, { useState } from 'react';
import { Wallpaper } from 'capacitor-wallpaper-plugin';

function WallpaperApp() {
  const [loading, setLoading] = useState(false);

  const setStaticWallpaper = async () => {
    setLoading(true);
    try {
      await Wallpaper.setStatic({
        imageUrl: 'https://picsum.photos/1080/1920',
        screen: 'HOME'
      });
      alert('Wallpaper set successfully!');
    } catch (error) {
      alert('Error: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const setLiveWallpaper = async () => {
    setLoading(true);
    try {
      const result = await Wallpaper.setLive({
        videoUrl: 'https://example.com/video.mp4'
      });
      
      if (result.requiresUserAction) {
        alert('Please select the wallpaper from the picker');
      } else {
        alert('Live wallpaper set successfully!');
      }
    } catch (error) {
      alert('Error: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <button onClick={setStaticWallpaper} disabled={loading}>
        Set Static Wallpaper
      </button>
      <button onClick={setLiveWallpaper} disabled={loading}>
        Set Live Wallpaper
      </button>
    </div>
  );
}
```

## Permissions

The plugin automatically requests these permissions:

- `android.permission.INTERNET` - For downloading images/videos
- `android.permission.SET_WALLPAPER` - For setting wallpapers
- `android.permission.SET_WALLPAPER_HINTS` - For wallpaper hints

## Troubleshooting

### "Plugin not found" error

Make sure you've synced the project:

```bash
npx cap sync android
```

### Video doesn't play

1. Check video format (must be MP4 with H.264 codec)
2. Check video size (should be under 50MB)
3. Check logcat for errors: `adb logcat | grep VideoLiveWallpaper`

### Wallpaper not setting

1. Check device permissions
2. Make sure you're running on a physical Android device
3. Check if device supports live wallpapers

## Performance

- **Memory Usage:** ~50-80 MB while playing video
- **Battery Impact:** Minimal (video pauses when screen is off)
- **CPU Usage:** Low (uses hardware acceleration)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License - Copyright (c) 2024 Umesh Dafda

## Author

**Umesh Dafda**
- GitHub: [@Turtlebase](https://github.com/Turtlebase)

## Support

If you encounter any issues, please [open an issue](https://github.com/Turtlebase/capacitor-wallpaper-plugin/issues) on GitHub.

## Changelog

### 1.5.0
- Added **parallax live wallpaper** support:
  - `setParallaxWallpaper(options)` — turn any image into a parallax wallpaper with configurable intensity/speed/overscan and scroll/tilt toggles
  - `updateParallaxSettings(options)` — live-tweak an active parallax wallpaper's range/speed without re-downloading
  - `resetParallaxEffect()` — stop the effect and revert to the device default wallpaper
  - `isParallaxSupported()` — check device support (live wallpaper + motion sensor)
  - New `ParallaxWallpaperService` renders the effect natively: smooth exponential-lerp panning driven by home-screen scroll offsets and a low-pass-filtered accelerometer signal, over an oversized "cover" render of the source image

### 1.0.0
- Initial release
- Static wallpaper support
- Live wallpaper support
- Auto-loop video playback
- Battery optimization
