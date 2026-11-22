"use client";

import { useMemo } from "react";

import { useEventCallback } from "@/hooks/utils/use-event-callback";
import { useEventListener } from "@/hooks/utils/use-event-listener";

export type HotkeyHandler = (event: KeyboardEvent) => void;
export type HotkeyMap = Record<string, HotkeyHandler>;

const MODIFIER_ORDER = ["mod", "alt", "shift"] as const;
const MODIFIER_SET = new Set<string>(MODIFIER_ORDER);

function normalizeHotkey(hotkey: string) {
    const parts = hotkey
        .toLowerCase()
        .split("+")
        .map((part) => part.trim())
        .filter(Boolean)
        .map((part) => {
            if (part === "cmd" || part === "command" || part === "meta" || part === "ctrl" || part === "control") {
                return "mod";
            }
            if (part === "option") {
                return "alt";
            }
            return part;
        });

    const modifiers = MODIFIER_ORDER.filter((modifier) => parts.includes(modifier));
    const keys = parts.filter((part) => !MODIFIER_SET.has(part));
    return [...modifiers, ...keys].join("+");
}

function eventHotkey(event: KeyboardEvent) {
    const key = event.key.toLowerCase();
    if (key === "control" || key === "meta" || key === "alt" || key === "shift") {
        return null;
    }

    const parts: string[] = [];
    if (event.metaKey || event.ctrlKey) {
        parts.push("mod");
    }
    if (event.altKey) {
        parts.push("alt");
    }
    if (event.shiftKey) {
        parts.push("shift");
    }
    parts.push(key);
    return parts.join("+");
}

export function useHotkeys(hotkeys: HotkeyMap, enabled = true) {
    const normalizedHotkeys = useMemo(() => {
        const entries = Object.entries(hotkeys).map(([hotkey, handler]) => [normalizeHotkey(hotkey), handler] as const);
        return new Map(entries);
    }, [hotkeys]);

    const handleKeyDown = useEventCallback((event: KeyboardEvent) => {
        if (event.defaultPrevented) {
            return;
        }

        const hotkey = eventHotkey(event);
        if (!hotkey) {
            return;
        }

        const handler = normalizedHotkeys.get(hotkey);
        if (!handler) {
            return;
        }

        event.preventDefault();
        handler(event);
    });

    useEventListener("keydown", handleKeyDown, enabled ? undefined : null);
}
