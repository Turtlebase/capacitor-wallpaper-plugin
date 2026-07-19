/**
 * ParallaxSettingsUpdate.tsx
 * ---------------------------------------------------------------------------
 * Companion to ParallaxWallpaperScreen.tsx.
 *
 * Use this when the user ALREADY has a parallax wallpaper applied and just
 * wants to tune strength/speed/tilt/swipe afterwards — e.g. from a
 * "Wallpaper settings" screen. This does NOT re-download the image or
 * reopen Android's picker; it just updates the SharedPreferences the
 * running ParallaxWallpaperService reads on every frame, so changes apply
 * live, instantly, with no flicker or restart.
 *
 * It intentionally has no "preview" step — the user is looking at the real
 * wallpaper on their home screen behind/around this UI (or can back out to
 * check), so there is no fidelity gap to worry about here the way there is
 * on the first-time setup screen.
 * ---------------------------------------------------------------------------
 */

import React, { useState } from 'react';
import { registerPlugin } from '@capacitor/core';
import type { WallpaperPluginPlugin, ParallaxSettingsUpdate } from '../src/definitions';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin');

interface Props {
  initialIntensity?: number;
  initialSpeed?: number;
  initialSensorParallax?: boolean;
  initialScrollParallax?: boolean;
}

export const ParallaxSettingsUpdateScreen: React.FC<Props> = ({
  initialIntensity = 30,
  initialSpeed = 0.12,
  initialSensorParallax = true,
  initialScrollParallax = true,
}) => {
  const [intensity, setIntensity] = useState(initialIntensity);
  const [speed, setSpeed] = useState(initialSpeed);
  const [sensorParallax, setSensorParallax] = useState(initialSensorParallax);
  const [scrollParallax, setScrollParallax] = useState(initialScrollParallax);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Debounced so dragging a slider doesn't spam native calls — update
  // ~150ms after the user stops moving it.
  const debounceRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const pushUpdate = (partial: ParallaxSettingsUpdate) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setSaving(true);
      setError(null);
      try {
        await WallpaperPlugin.updateParallaxSettings(partial);
      } catch (e: any) {
        setError(e?.message ?? 'Failed to update parallax settings.');
      } finally {
        setSaving(false);
      }
    }, 150);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 16 }}>
      <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 14 }}>
        Parallax strength: {Math.round(intensity)}%
        <input
          type="range"
          min={0}
          max={100}
          value={intensity}
          onChange={(e) => {
            const v = Number(e.target.value);
            setIntensity(v);
            pushUpdate({ intensity: v });
          }}
        />
      </label>

      <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 14 }}>
        Motion speed: {speed.toFixed(2)}
        <input
          type="range"
          min={0.01}
          max={1}
          step={0.01}
          value={speed}
          onChange={(e) => {
            const v = Number(e.target.value);
            setSpeed(v);
            pushUpdate({ speed: v });
          }}
        />
      </label>

      <label style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
        <span>React to tilt</span>
        <input
          type="checkbox"
          checked={sensorParallax}
          onChange={(e) => {
            const v = e.target.checked;
            setSensorParallax(v);
            pushUpdate({ sensorParallax: v });
          }}
        />
      </label>

      <label style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
        <span>React to home-screen swipe</span>
        <input
          type="checkbox"
          checked={scrollParallax}
          onChange={(e) => {
            const v = e.target.checked;
            setScrollParallax(v);
            pushUpdate({ scrollParallax: v });
          }}
        />
      </label>

      {saving && <p style={{ fontSize: 12, color: '#999' }}>Saving…</p>}
      {error && <p style={{ fontSize: 12, color: '#e5484d' }}>{error}</p>}
    </div>
  );
};
