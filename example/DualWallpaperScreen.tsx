/**
 * DualWallpaperScreen.tsx
 * ---------------------------------------------------------------------------
 * Lets the user pick one wallpaper for the home screen and a DIFFERENT one
 * for the lock screen, then apply both together in a single call.
 *
 * Matches how popular wallpaper apps (Zedge, Walli, etc.) do this: two
 * preview slots (Home / Lock), each tap-to-change from your existing
 * wallpaper picker/grid, then a single "Set Both" action at the bottom.
 *
 * IMPORTANT: setHomeAndLockWallpapers is all-or-nothing. If either image
 * fails to download, NEITHER screen changes — so there's no risk of ending
 * up with home updated but lock still showing the old/default image (or
 * vice-versa). The UI below reflects that: a single pending/error/success
 * state for the pair, not two independent ones.
 * ---------------------------------------------------------------------------
 */

import React, { useState } from 'react';
import { registerPlugin } from '@capacitor/core';
import type { WallpaperPluginPlugin } from '../src/definitions';

const WallpaperPlugin = registerPlugin<WallpaperPluginPlugin>('WallpaperPlugin');

interface DualWallpaperScreenProps {
  /** Initial home-screen image URL (e.g. the wallpaper the user tapped from). */
  initialHomeUrl: string;
  /** Initial lock-screen image URL — defaults to the same as home until changed. */
  initialLockUrl?: string;
  /** Called when the user wants to pick a different image for a slot ('home' | 'lock'). */
  onPickImage: (slot: 'home' | 'lock') => Promise<string>; // resolves to a new CDN url
}

type Status =
  | { kind: 'idle' }
  | { kind: 'setting' }
  | { kind: 'success' }
  | { kind: 'error'; message: string };

export const DualWallpaperScreen: React.FC<DualWallpaperScreenProps> = ({
  initialHomeUrl,
  initialLockUrl,
  onPickImage,
}) => {
  const [homeUrl, setHomeUrl] = useState(initialHomeUrl);
  const [lockUrl, setLockUrl] = useState(initialLockUrl ?? initialHomeUrl);
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  const handlePick = async (slot: 'home' | 'lock') => {
    const newUrl = await onPickImage(slot);
    if (slot === 'home') setHomeUrl(newUrl);
    else setLockUrl(newUrl);
    setStatus({ kind: 'idle' }); // clear any prior error once the user changes a pick
  };

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
        // Shouldn't normally happen — native either resolves success or
        // rejects — but handle defensively.
        setStatus({ kind: 'error', message: 'Could not set both wallpapers.' });
      }
    } catch (e: any) {
      // Covers the all-or-nothing rejection cases: download failure for
      // either image, or pre-Android-7.0 devices where only home applied.
      setStatus({ kind: 'error', message: e?.message ?? 'Failed to set wallpapers.' });
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.slots}>
        <div style={styles.slot}>
          <img src={homeUrl} alt="Home screen wallpaper" style={styles.thumb} />
          <span style={styles.slotLabel}>Home Screen</span>
          <button style={styles.changeButton} onClick={() => handlePick('home')}>
            Change
          </button>
        </div>

        <div style={styles.slot}>
          <img src={lockUrl} alt="Lock screen wallpaper" style={styles.thumb} />
          <span style={styles.slotLabel}>Lock Screen</span>
          <button style={styles.changeButton} onClick={() => handlePick('lock')}>
            Change
          </button>
        </div>
      </div>

      {status.kind === 'error' && <p style={styles.error}>{status.message}</p>}
      {status.kind === 'success' && (
        <p style={styles.success}>Home and lock screen wallpapers set.</p>
      )}

      <button
        style={styles.setButton}
        onClick={handleSetBoth}
        disabled={status.kind === 'setting'}
      >
        {status.kind === 'setting' ? 'Setting…' : 'Set Both Wallpapers'}
      </button>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: { display: 'flex', flexDirection: 'column', gap: 16, padding: 16 },
  slots: { display: 'flex', gap: 16 },
  slot: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 8,
  },
  thumb: {
    width: '100%',
    aspectRatio: '9/16',
    objectFit: 'cover',
    borderRadius: 12,
    background: '#000',
  },
  slotLabel: { fontSize: 13, color: '#aaa' },
  changeButton: {
    padding: '6px 14px',
    borderRadius: 8,
    border: '1px solid #444',
    background: 'transparent',
    color: '#fff',
    fontSize: 13,
  },
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
  success: { color: '#4caf50', fontSize: 13 },
};
