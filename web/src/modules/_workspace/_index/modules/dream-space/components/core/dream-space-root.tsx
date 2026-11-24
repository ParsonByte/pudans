"use client";

import type { ReactNode } from "react";

import { A11yMemoryList } from "../a11y/a11y-memory-list";
import { HighContrastMode } from "../a11y/high-contrast-mode";
import { KeyboardFocusRing } from "../a11y/keyboard-focus-ring";
import { ReducedMotionProvider } from "../a11y/reduced-motion-provider";
import { ScreenReaderAnnouncer } from "../a11y/screen-reader-announcer";
import { DreamSeedProvider } from "./dream-seed-provider";
import { WorldClock } from "./world-clock";
import { WorldEventBus, WorldEventLogger } from "./world-event-bus";
import { WorldSaveSnapshot } from "./world-save-snapshot";
import { WorldSessionProvider } from "./world-session-provider";
import { WorldStateMachine } from "./world-state-machine";

export function DreamSpaceRoot({ children }: { children: ReactNode }) {
    return (
        <WorldSessionProvider>
            <DreamSeedProvider>
                <WorldEventBus>
                    <ReducedMotionProvider>
                        <KeyboardFocusRing />
                        <HighContrastMode />
                        <WorldStateMachine />
                        <WorldClock />
                        <WorldEventLogger />
                        <WorldSaveSnapshot />
                        <A11yMemoryList />
                        <ScreenReaderAnnouncer />
                        {children}
                    </ReducedMotionProvider>
                </WorldEventBus>
            </DreamSeedProvider>
        </WorldSessionProvider>
    );
}
