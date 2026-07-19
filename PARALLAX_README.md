# Parallax Wallpaper — Integration Guide

Android-only. Adds a "parallax" live wallpaper mode to the existing
`WallpaperPlugin`: pass a CDN image URL, pick a strength/speed, and the
plugin builds a live wallpaper that pans the image in response to
home-screen swipes and/or device tilt.

This document covers **only** the parallax feature. For the rest of the
plugin (static wallpaper, lock screen, GIF/MP4 live wallpaper) see
`README.md` / `INSTALLATION.md`.

---

## 1. What it actually does (read this first)

- **You only ever pass a URL.** No pre-processing, no server work. The
  plugin downloads the image natively, renders it oversized, and stores it
  in app-private storage.
- **There is no native "preview" API.** The plugin does not expose a way to
  render the parallax effect inside your app's UI before applying it. If you
  want a preview screen (recommended), you build it yourself in JS/CSS. An
  example that does this correctly is included — see §4.
- **"Set Wallpaper" opens Android's system picker; it does not apply
  directly.** `setParallaxWallpaper()` resolves as soon as that system
  picker Intent launches — **not** when the user actually taps "Set
  wallpaper" inside it. There is no callback for that final confirmation;
  it happens in system UI outside your app's process. Design your UI
  around "we opened the picker for you" rather than "wallpaper is now set."
- **Strength (`intensity`) is a linear, proportional multiplier**, not a
  vague slider. See §3 for the exact formula — this matters if you build
  your own preview, so the preview doesn't mislead the user about how
  strong the effect will really be.

---

## 2. API surface (parallax-only)

```ts
setParallaxWallpaper(options: {
  url: string;              // required — CDN url or local file:// URI
  intensity?: number;       // 0-100, default 30 — % of max pan distance used
  speed?: number;           // 0.01-1, default 0.12 — motion smoothing/responsiveness
  sensorParallax?: boolean; // default true — react to device tilt
  scrollParallax?: boolean; // default true — react to home-screen swipe
  overscan?: number;        // 1.05-2.0, default 1.3 — how much bigger than
                            // the screen the image is rendered, i.e. how
                            // much room there is to pan
}): Promise<{ success: boolean }>;

updateParallaxSettings(options: {
  intensity?: number;
  speed?: number;
  sensorParallax?: boolean;
  scrollParallax?: boolean;
}): Promise<{ success: boolean }>;

resetParallaxEffect(): Promise<{ success: boolean }>;

isParallaxSupported(): Promise<{ supported: boolean; hasSensor: boolean }>;
```

- `setParallaxWallpaper` — first-time setup. Downloads image, builds render,
  opens the system live-wallpaper picker. Use once per new wallpaper choice.
- `updateParallaxSettings` — tunes an **already-applied** parallax
  wallpaper live (no re-download, no picker, no restart). Use from a
  settings screen after the fact.
- `resetParallaxEffect` — clears the wallpaper back to device default.
- `isParallaxSupported` — call before showing any parallax UI. Checks for
  `FEATURE_LIVE_WALLPAPER` and accelerometer presence separately, so you can
  still offer parallax with tilt disabled on devices with no sensor.

---

## 3. The intensity formula (for building an honest preview)

Native pan math (`ParallaxWallpaperService.draw()`):

```
maxPanX = bitmap.width  - surfaceWidth     // == surfaceWidth  * (overscan - 1)
maxPanY = bitmap.height - surfaceHeight    // == surfaceHeight * (overscan - 1)

amplitudeFraction = intensity / 100

targetPanX = (maxPanX / 2) + combinedX * (maxPanX / 2) * amplitudeFraction
targetPanY = (maxPanY / 2) + combinedY * (maxPanY / 2) * amplitudeFraction
```

`combinedX`/`combinedY` are the tilt + swipe inputs, each normalized to
`-1..1` and weighted so they don't add up to more range than `intensity`
allows.

**Key point:** `intensity` is a straight percentage of *available pan room*,
not a percentage of some fixed pixel count. A 30% slider always means "30%
of however much room this specific image/screen has to pan," on both the
real wallpaper and in any preview you build — as long as your preview uses
the same ratio-based formula against its own container size. See the
included example for the implementation.

---

## 4. Included examples

- **`example/ParallaxWallpaperScreen.tsx`** — first-time setup screen:
  strength/speed sliders, tilt/swipe toggles, and an in-app preview that
  pans a plain `<img>` using the *same proportional formula* as the native
  engine (§3), so what the user sees before committing is honest relative
  to what they'll actually get — not a rough approximation that can look
  far stronger or weaker than the real thing. Ends with "Set as Wallpaper"
  → `setParallaxWallpaper()` → Android's picker opens.

- **`example/ParallaxSettingsUpdate.tsx`** — settings screen for a
  wallpaper that's already applied. Debounced sliders call
  `updateParallaxSettings()` so changes apply live with no re-download or
  picker.

Both are plain React components with inline styles — adapt the styling to
your app's design system; the logic (support check → local slider state →
plugin calls) is what matters.

---

## 5. Typical integration flow

```
[Wallpaper list] 
    → user taps "Set as Parallax" on a wallpaper
    → navigate to ParallaxWallpaperScreen, passing the CDN url as a prop
    → isParallaxSupported() checked on mount; unsupported devices see a
      message instead of the slider UI
    → user adjusts intensity/speed/tilt/swipe, watches the local preview
    → user taps "Set as Wallpaper"
    → setParallaxWallpaper({ url, intensity, speed, sensorParallax, scrollParallax })
    → Android's live wallpaper picker opens; user confirms there
    → (optional) later, user opens wallpaper settings
    → ParallaxSettingsUpdateScreen calls updateParallaxSettings() as they
      adjust sliders, live, no picker involved
```

---

## 6. Motion engine (premium/natural feel)

The native engine (`ParallaxWallpaperService.java`) does three things beyond
a naive "move image toward target" implementation, so the effect reads as
smooth and intentional rather than mechanical:

1. **Two-stage tilt smoothing.** Raw accelerometer input is first low-passed
   to isolate gravity/tilt from motion noise, then the *normalized* tilt
   value is smoothed again. The second pass removes residual hand-tremor
   jitter that would otherwise get amplified by `intensity` and read as a
   nervous twitch.
2. **Acceleration-clamped, damped-spring pan motion**, replacing a flat
   exponential lerp. Position is driven by a velocity that:
   - is requested toward the target each frame (like the old lerp),
   - can only change by a capped amount per frame (no instant direction/speed
     snaps from a sudden tilt or fast swipe),
   - is damped every frame so it settles into the target smoothly (ease-out)
     instead of moving at constant speed and stopping abruptly.
3. **Subtle scale "breathing" tied to tilt magnitude** (up to +2%, scaled by
   `intensity`). This mimics the slight zoom real depth-layered parallax
   wallpapers have as you tilt toward the image, which is a big part of why
   they read as 3D rather than "a photo sliding around." It stays inside the
   existing `overscan` margin, so no edges are ever exposed.

None of this changes the public API or the `intensity`/`speed` semantics —
`speed` still controls responsiveness (now via the spring's velocity
request rather than a raw lerp factor) and `intensity` still linearly scales
pan range exactly as described in §3. Existing preview code using the §3
formula does not need to change; it approximates the *target* position,
which is still proportionally correct — only the native side's *path* to
that target is now smoother.

---

## 7. Gotchas to design around

1. Portrait, subject-centered art (a tall figure with the face in the upper
   third, like a deity/character wallpaper) can pan the face partially out
   of frame at high intensity on some aspect ratios. Consider defaulting to
   a lower suggested intensity (~20-25%) for this kind of composition rather
   than the general-purpose default of 30%.
2. No "wallpaper successfully applied" event exists — only "picker opened
   successfully." Don't show a success checkmark implying the wallpaper is
   now live; tell the user to confirm in the picker that just opened.
2. No native in-app preview exists — anything shown before `setParallaxWallpaper`
   is a JS approximation you own. Keep it proportionally honest (§3) so
   strength expectations aren't broken.
3. Android only — there is no iOS implementation in this plugin. Gate the
   "Set as Parallax" button off entirely on iOS, or hide it based on
   `Capacitor.getPlatform()`.
4. If `hasSensor` is `false`, disable/hide the tilt toggle — swipe-only
   parallax still works fine without an accelerometer.
