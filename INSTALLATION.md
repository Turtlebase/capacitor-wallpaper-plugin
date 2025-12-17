# Installation Guide

## Step 1: Install the Plugin

### From GitHub

```bash
npm install github:Turtlebase/capacitor-wallpaper-plugin
```

### From Local Directory (for development)

```bash
npm install /path/to/capacitor-wallpaper-plugin
```

## Step 2: Sync Capacitor

```bash
npx cap sync android
```

This will:
- Copy the plugin to your Android project
- Register the plugin automatically
- Set up all necessary permissions

## Step 3: Verify Installation

Check that the plugin was installed:

```bash
ls node_modules/capacitor-wallpaper-plugin
```

You should see the plugin files.

## Step 4: Use in Your App

### TypeScript/JavaScript

```typescript
import { Wallpaper } from 'capacitor-wallpaper-plugin';

async function testWallpaper() {
  try {
    // Check if supported
    const { supported } = await Wallpaper.isSupported();
    console.log('Wallpaper supported:', supported);
    
    // Set static wallpaper
    await Wallpaper.setStatic({
      imageUrl: 'https://picsum.photos/1080/1920',
      screen: 'HOME'
    });
    
    console.log('✅ Wallpaper set successfully!');
  } catch (error) {
    console.error('❌ Error:', error);
  }
}

testWallpaper();
```

## Step 5: Build and Run

```bash
# Build your app
npm run build

# Copy to Android
npx cap copy android

# Open in Android Studio
npx cap open android

# Or run directly
npx cap run android
```

## Troubleshooting

### Plugin Not Found

If you get "plugin not found" error:

1. Make sure you ran `npx cap sync android`
2. Check `node_modules/capacitor-wallpaper-plugin` exists
3. Clean and rebuild:
   ```bash
   cd android
   ./gradlew clean
   cd ..
   npx cap sync android
   ```

### Build Errors

If you get build errors:

1. Make sure you have Java 17 installed
2. Check Android SDK is up to date
3. Clean Gradle cache:
   ```bash
   cd android
   ./gradlew clean
   ./gradlew build
   ```

### Runtime Errors

If the app crashes or shows errors:

1. Check logcat:
   ```bash
   adb logcat | grep "WallpaperPlugin\|VideoLiveWallpaper"
   ```

2. Verify permissions in AndroidManifest.xml
3. Make sure you're running on a physical Android device

## Updating the Plugin

To update to the latest version:

```bash
npm update capacitor-wallpaper-plugin
npx cap sync android
```

## Removing the Plugin

To remove the plugin:

```bash
npm uninstall capacitor-wallpaper-plugin
npx cap sync android
```

## Next Steps

Check the [README.md](README.md) for:
- Full API documentation
- Usage examples
- Video requirements
- Performance tips
