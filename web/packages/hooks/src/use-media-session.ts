import { useEffect } from "react";

export interface MediaSessionMetadata {
  title: string;
  artist?: string;
  album?: string;
  artwork?: MediaImage[];
}

export interface MediaSessionHandlers {
  play?: () => void;
  pause?: () => void;
  previousTrack?: () => void;
  nextTrack?: () => void;
  seekBackward?: (offsetSeconds: number) => void;
  seekForward?: (offsetSeconds: number) => void;
  seekTo?: (timeSeconds: number) => void;
}

export interface UseMediaSessionOptions {
  metadata: MediaSessionMetadata;
  playing: boolean;
  duration?: number;
  position?: number;
  handlers?: MediaSessionHandlers;
}

function hasMediaSession(): boolean {
  return typeof navigator !== "undefined" && "mediaSession" in navigator;
}

/**
 * Syncs playback metadata/handlers with the browser Media Session API.
 * Migrated from the legacy Common/hooks/useMediaSession (generic version,
 * no file-model dependency).
 */
export function useMediaSession({
  metadata,
  playing,
  duration,
  position,
  handlers,
}: UseMediaSessionOptions): void {
  useEffect(() => {
    if (!hasMediaSession()) {
      return;
    }
    navigator.mediaSession.metadata = new MediaMetadata({
      title: metadata.title,
      artist: metadata.artist ?? "",
      album: metadata.album ?? "",
      artwork: metadata.artwork ?? [],
    });
  }, [metadata]);

  useEffect(() => {
    if (!hasMediaSession()) {
      return;
    }
    navigator.mediaSession.playbackState = playing ? "playing" : "paused";
  }, [playing]);

  useEffect(() => {
    if (!hasMediaSession() || !duration || duration <= 0 || position === undefined) {
      return;
    }
    try {
      navigator.mediaSession.setPositionState({
        duration,
        playbackRate: 1,
        position: Math.max(0, Math.min(position, duration)),
      });
    } catch {
      // Some browsers reject position state; safe to ignore.
    }
  }, [duration, position]);

  useEffect(() => {
    if (!hasMediaSession() || !handlers) {
      return;
    }
    const session = navigator.mediaSession;
    const bind = (action: MediaSessionAction, handler: MediaSessionActionHandler | null): void => {
      try {
        session.setActionHandler(action, handler);
      } catch {
        // Unsupported action; ignore.
      }
    };
    bind("play", handlers.play ? () => handlers.play?.() : null);
    bind("pause", handlers.pause ? () => handlers.pause?.() : null);
    bind("previoustrack", handlers.previousTrack ? () => handlers.previousTrack?.() : null);
    bind("nexttrack", handlers.nextTrack ? () => handlers.nextTrack?.() : null);
    bind(
      "seekbackward",
      handlers.seekBackward ? (details) => handlers.seekBackward?.(details.seekOffset ?? 10) : null,
    );
    bind(
      "seekforward",
      handlers.seekForward ? (details) => handlers.seekForward?.(details.seekOffset ?? 10) : null,
    );
    bind(
      "seekto",
      handlers.seekTo
        ? (details) => {
            if (details.seekTime !== undefined) {
              handlers.seekTo?.(details.seekTime);
            }
          }
        : null,
    );
    return () => {
      for (const action of [
        "play",
        "pause",
        "previoustrack",
        "nexttrack",
        "seekbackward",
        "seekforward",
        "seekto",
      ] as const) {
        bind(action, null);
      }
    };
  }, [handlers]);
}
