/**
 * ParallaxWallpaperScreen.tsx
 * ---------------------------------------------------------------------------
 * Android-only "Parallax" wallpaper flow for an app that already has
 * wallpapers on a CDN (jpg / webP).
 *
 * FLOW:
 *   1. User taps "Set as Parallax" on a wallpaper card -> navigate here with
 *      the CDN url. No native/background work happens on tap — just nav.
 *   2. This screen checks isParallaxSupported() (Android + accelerometer).
 *   3. Shows an APP-SIDE preview built from a plain <img> tag, panned with
 *      CSS transforms in response to drag ("swipe between home screens")
 *      and device tilt. This is a JS approximation, not the real native
 *      renderer — the plugin has no in-app preview API — BUT the pan-amount
 *      math below is deliberately written to match the native engine's
 *      formula exactly (see PROPORTIONAL MATH note), so a 30% strength
 *      slider looks like ~30% travel in both the preview and the real
 *      wallpaper, not a rough guess.
 *   4. Sliders (intensity/speed) and toggles (tilt/swipe) only change local
 *      React state until "Set as Wallpaper" is pressed — nothing is sent to
 *      native code or downloaded yet.
 *   5. "Set as Wallpaper" -> setParallaxWallpaper({ url, intensity, speed,
 *      sensorParallax, scrollParallax }). This call downloads the image
 *      natively, builds the oversized parallax render with those settings
 *      baked in, and opens Android's own "Change live wallpaper" picker.
 *      IMPORTANT: the promise resolves as soon as that picker opens, NOT
 *      when the user actually taps "Set" inside it — there's no callback
 *      for that final confirmation, since it happens in system UI outside
 *      this app.
 *   6. To retune strength on a wallpaper that is ALREADY applied (not
 *      during first-time setup), use updateParallaxSettings() instead — it
 *      updates the live wallpaper in place without re-downloading the image
 *      or reopening the picker.
 *
 * PROPORTIONAL MATH (why 30% here == 30% on the real wallpaper)
 * ---------------------------------------------------------------------------
 * Native (ParallaxWallpaperService.draw), in pixels against the oversized
 * bitmap:
 *   maxPanX = bitmap.width  - surfaceWidth   // == surfaceWidth  * (overscan-1)
 *   maxPanY = bitmap.height - surfaceHeight  // == surfaceHeight * (overscan-1)
 *   amplitudeFraction = intensity / 100
 *   targetPanX = (maxPanX/2) + combinedX * (maxPanX/2) * amplitudeFraction
 *   targetPanY = (maxPanY/2) + combinedY * (maxPanY/2) * amplitudeFraction
 *
 * Preview (below), in pixels against the on-screen <img> container:
 *   maxPanX = containerWidth  * (overscan-1)
 *   maxPanY = containerHeight * (overscan-1)
 *   offsetX = combinedX * (maxPanX/2) * amplitudeFraction
 *   offsetY = combinedY * (maxPanY/2) * amplitudeFraction
 *
 * Both sides use the SAME overscan constant and the SAME amplitudeFraction
 * applied to "half of the available pan room" — so even though the absolute
 * pixel counts differ (small preview vs. full phone screen), the ratio of
 * "how far it actually pans vs. how far it COULD pan" is identical. That
 * ratio is what a person perceives as "strength," so the preview stays
 * honest: it will not look like 80-90% motion when the slider says 30%.
 * ---------------------------------------------------------------------------
 */

import React, { useEffect, useRef, useState } from 'react';
import { registerPlugin, Capacitor } from '@capacitor/core';
import type { WallpaperPluginPlugin, ParallaxWallpaperOptions } from '../src/definitions';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin');

// Must match the plugin's default overscan (see definitions.ts / WallpaperPlugin.java)
const DEFAULT_OVERSCAN = 1.3;

interface ParallaxWallpaperScreenProps {
  /** CDN url of the wallpaper image (jpg or webP), passed in from the wallpaper list. */
  imageUrl: string;
  onClose?: () => void;
}

type Status =
  | { kind: 'idle' }
  | { kind: 'checking' }
  | { kind: 'unsupported'; reason: string }
  | { kind: 'setting' }
  | { kind: 'picker-opened' }
  | { kind: 'error'; message: string };

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n));
}

export const ParallaxWallpaperScreen: React.FC<ParallaxWallpaperScreenProps> = ({
  imageUrl,
  onClose,
}) => {
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  // --- Slider / toggle state — purely local until "Set as Wallpaper" is pressed.
  const [intensity, setIntensity] = useState(30); // 0-100, plugin default 30
  const [speed, setSpeed] = useState(0.12); // 0.01 (slow/smooth) - 1 (instant), plugin default 0.12
  const [sensorParallax, setSensorParallax] = useState(true);
  const [scrollParallax, setScrollParallax] = useState(true);
  const [hasSensor, setHasSensor] = useState(true);

  // --- Preview geometry: measure the actual rendered container so the pan
  // math below can compute "room available to pan" the same way native does.
  const previewRef = useRef<HTMLDivElement>(null);
  const [containerSize, setContainerSize] = useState({ w: 0, h: 0 });
  const [previewOffset, setPreviewOffset] = useState({ x: 0, y: 0 });

  // Raw, unscaled inputs (-1..1) from tilt and drag, combined the same way
  // native combines scroll + tilt before applying amplitudeFraction.
  const tiltRaw = useRef({ x: 0, y: 0 });
  const dragRaw = useRef({ x: 0, y: 0 });
  const dragStart = useRef<{ x: number; y: number } | null>(null);

  useEffect(() => {
    if (!previewRef.current) return;
    const el = previewRef.current;
    const ro = new ResizeObserver(() => setContainerSize({ w: el.clientWidth, h: el.clientHeight }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // ---- Step 2: check device support before showing anything -------------
  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (Capacitor.getPlatform() !== 'android') {
        setStatus({ kind: 'unsupported', reason: 'Parallax wallpapers are only available on Android.' });
        return;
      }

      setStatus({ kind: 'checking' });
      try {
        const { supported, hasSensor: sensorAvailable } = await WallpaperPlugin.isParallaxSupported();
        if (cancelled) return;

        if (!supported) {
          setStatus({ kind: 'unsupported', reason: 'This device does not support live wallpapers.' });
          return;
        }

        setHasSensor(sensorAvailable);
        if (!sensorAvailable) setSensorParallax(false); // no accelerometer -> disable tilt option
        setStatus({ kind: 'idle' });
      } catch {
        if (!cancelled) setStatus({ kind: 'error', message: 'Could not check device support.' });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * Recompute the preview's on-screen offset from the raw -1..1 tilt/drag
   * inputs, using the exact same proportional formula as the native
   * ParallaxWallpaperService.draw() — see the file header for the full
   * derivation. This is the function that keeps "30% on the slider" honest.
   */
  const recomputeOffset = () => {
    const amplitudeFraction = intensity / 100;

    const combinedX = clamp(
      dragRaw.current.x * (scrollParallax ? 1 : 0) * 0.6 +
        tiltRaw.current.x * (sensorParallax ? 1 : 0) * 0.6,
      -1,
      1,
    );
    const combinedY = clamp(tiltRaw.current.y * (sensorParallax ? 1 : 0), -1, 1);

    const maxPanX = containerSize.w * (DEFAULT_OVERSCAN - 1);
    const maxPanY = containerSize.h * (DEFAULT_OVERSCAN - 1);

    const offsetX = combinedX * (maxPanX / 2) * amplitudeFraction;
    const offsetY = combinedY * (maxPanY / 2) * amplitudeFraction;

    setPreviewOffset({ x: offsetX, y: offsetY });
  };

  // Recompute whenever inputs that affect the formula change, even without
  // new tilt/drag events (e.g. moving the intensity slider while tilted).
  useEffect(() => {
    recomputeOffset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intensity, sensorParallax, scrollParallax, containerSize]);

  // ---- Fake preview: tilt via devicemotion (approximate sensor parallax) --
  useEffect(() => {
    if (!sensorParallax) {
      tiltRaw.current = { x: 0, y: 0 };
      recomputeOffset();
      return;
    }

    const handleTilt = (e: DeviceOrientationEvent) => {
      const gamma = e.gamma ?? 0; // left-right tilt, -90..90
      const beta = e.beta ?? 0; // front-back tilt, -180..180
      tiltRaw.current = {
        x: clamp(gamma / 45, -1, 1),
        y: clamp(-beta / 45, -1, 1), // invert, matches native's "tilt away = pan up"
      };
      recomputeOffset();
    };

    window.addEventListener('deviceorientation', handleTilt);
    return () => window.removeEventListener('deviceorientation', handleTilt);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sensorParallax]);

  // ---- Fake preview: drag to simulate "swipe between home screens" -------
  const onPointerDown = (e: React.PointerEvent) => {
    if (!scrollParallax) return;
    dragStart.current = { x: e.clientX, y: e.clientY };
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!scrollParallax || !dragStart.current || containerSize.w === 0) return;
    const dx = e.clientX - dragStart.current.x;
    // Normalize drag distance against container width so it's -1..1, same
    // shape as native's scrollNormX (which comes from launcher page offset).
    dragRaw.current = { x: clamp(dx / (containerSize.w / 2), -1, 1), y: 0 };
    recomputeOffset();
  };
  const onPointerUp = () => {
    dragStart.current = null;
    dragRaw.current = { x: 0, y: 0 };
    recomputeOffset();
  };

  // ---- Step 5: apply the real wallpaper -----------------------------------
  const handleSetWallpaper = async () => {
    setStatus({ kind: 'setting' });
    try {
      const options: ParallaxWallpaperOptions = {
        url: imageUrl,
        intensity,
        speed,
        sensorParallax,
        scrollParallax,
        overscan: DEFAULT_OVERSCAN,
      };

      const { success } = await WallpaperPlugin.setParallaxWallpaper(options);

      if (success) {
        // Only means the picker was launched. The user still has to tap
        // "Set wallpaper" inside Android's own picker to actually apply it —
        // there is no event we can await for that final confirmation.
        setStatus({ kind: 'picker-opened' });
      } else {
        setStatus({ kind: 'error', message: 'Could not start the wallpaper picker.' });
      }
    } catch (e: any) {
      setStatus({ kind: 'error', message: e?.message ?? 'Failed to set parallax wallpaper.' });
    }
  };

  // ---- Render --------------------------------------------------------------
  if (status.kind === 'unsupported') {
    return (
      <div style={styles.centered}>
        <p>{status.reason}</p>
        {onClose && <button onClick={onClose}>Go back</button>}
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div
        ref={previewRef}
        style={styles.previewWrapper}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={onPointerUp}
      >
        <img
          src={imageUrl}
          alt="Wallpaper preview"
          style={{
            ...styles.previewImage,
            transform: `translate(${previewOffset.x}px, ${previewOffset.y}px) scale(${DEFAULT_OVERSCAN})`,
            transition: `transform ${0.3 / Math.max(speed, 0.01)}s ease-out`,
          }}
        />
        <div style={styles.previewHint}>Drag to preview parallax</div>
      </div>

      <div style={styles.controls}>
        <label style={styles.label}>
          Parallax strength: {Math.round(intensity)}%
          <input
            type="range"
            min={0}
            max={100}
            value={intensity}
            onChange={(e) => setIntensity(Number(e.target.value))}
          />
        </label>

        <label style={styles.label}>
          Motion speed: {speed.toFixed(2)}
          <input
            type="range"
            min={0.01}
            max={1}
            step={0.01}
            value={speed}
            onChange={(e) => setSpeed(Number(e.target.value))}
          />
        </label>

        <label style={styles.toggleRow}>
          <span>React to tilt {hasSensor ? '' : '(no sensor on this device)'}</span>
          <input
            type="checkbox"
            checked={sensorParallax}
            disabled={!hasSensor}
            onChange={(e) => setSensorParallax(e.target.checked)}
          />
        </label>

        <label style={styles.toggleRow}>
          <span>React to home-screen swipe</span>
          <input
            type="checkbox"
            checked={scrollParallax}
            onChange={(e) => setScrollParallax(e.target.checked)}
          />
        </label>
      </div>

      {status.kind === 'error' && <p style={styles.error}>{status.message}</p>}
      {status.kind === 'picker-opened' && (
        <p style={styles.info}>
          Finish setting it in the picker that just opened — tap "Set wallpaper" there to apply it.
        </p>
      )}

      <button
        style={styles.setButton}
        onClick={handleSetWallpaper}
        disabled={status.kind === 'setting' || status.kind === 'checking'}
      >
        {status.kind === 'setting' ? 'Opening picker…' : 'Set as Wallpaper'}
      </button>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: { display: 'flex', flexDirection: 'column', gap: 16, padding: 16 },
  centered: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: 32 },
  previewWrapper: {
    position: 'relative',
    width: '100%',
    aspectRatio: '9/16',
    overflow: 'hidden',
    borderRadius: 16,
    background: '#000',
    touchAction: 'none',
  },
  previewImage: {
    position: 'absolute',
    top: '0',
    left: '0',
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  },
  previewHint: {
    position: 'absolute',
    bottom: 8,
    left: 0,
    right: 0,
    textAlign: 'center',
    color: 'rgba(255,255,255,0.7)',
    fontSize: 12,
  },
  controls: { display: 'flex', flexDirection: 'column', gap: 12 },
  label: { display: 'flex', flexDirection: 'column', gap: 4, fontSize: 14 },
  toggleRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 14 },
  setButton: {
    padding: '14px 20px',
    borderRadius: 12,
    border: 'none',
    background: '#6C4FE0',
    color: '#fff',
    fontWeight: 600,
    fontSize: 16,
  },
  error: { color: '#e5484d', fontSize: 13 },
  info: { color: '#999', fontSize: 13 },
};
