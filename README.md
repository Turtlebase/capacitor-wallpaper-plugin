# capacitor-wallpaper-plugin

A Capacitor plugin for setting static and live wallpapers on Android devices.

[![npm version](https://badge.fury.io/js/capacitor-wallpaper-plugin.svg)](https://badge.fury.io/js/capacitor-wallpaper-plugin)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Features

✅ Set static images as wallpaper (Home, Lock, or Both screens)  
✅ Set videos as live wallpaper  
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

### Set Live Wallpaper (Video)

```typescript
const result = await Wallpaper.setLive({
  videoUrl: 'https://example.com/video.mp4'
});

if (result.requiresUserAction) {
  console.log('User needs to select the wallpaper from the picker');
}
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

### `setLive(options)`

Set a video as live wallpaper.

**Parameters:**
- `videoUrl` (string): URL of the video to set (MP4 format with H.264 codec recommended)

**Returns:** `Promise<{ success: boolean; requiresUserAction?: boolean; method?: string }>`

### `isSupported()`

Check if wallpaper functionality is supported on the current device.

**Returns:** `Promise<{ supported: boolean; platform: string }>`

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

### 1.0.0
- Initial release
- Static wallpaper support
- Live wallpaper support
- Auto-loop video playback
- Battery optimization
