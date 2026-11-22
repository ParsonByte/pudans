"use client";

import { useCallback, useState } from "react";

import { useLocalStorage } from "@/hooks/utils/use-local-storage";

const RECENT_STORAGE_KEY = "pudans:command-menu-recent";
const RECENT_LIMIT = 5;
const EMPTY_RECENT: string[] = [];

export function useCommandMenu() {
    const [open, setOpenState] = useState(false);
    const [search, setSearch] = useState("");
    const [recent, setRecent] = useLocalStorage<string[]>(RECENT_STORAGE_KEY, EMPTY_RECENT);

    const setOpen = useCallback((nextOpen: boolean) => {
        setSearch("");
        setOpenState(nextOpen);
    }, []);

    const openMenu = useCallback(() => setOpen(true), [setOpen]);
    const closeMenu = useCallback(() => setOpen(false), [setOpen]);
    const toggleMenu = useCallback(() => {
        setSearch("");
        setOpenState((previous) => !previous);
    }, []);

    const trackRecent = useCallback(
        (id: string) => {
            setRecent((previous) => [id, ...previous.filter((item) => item !== id)].slice(0, RECENT_LIMIT));
        },
        [setRecent],
    );

    const clearRecent = useCallback(() => {
        setRecent(EMPTY_RECENT);
    }, [setRecent]);

    return {
        open,
        setOpen,
        search,
        setSearch,
        recent,
        trackRecent,
        clearRecent,
        openMenu,
        closeMenu,
        toggleMenu,
    };
}
