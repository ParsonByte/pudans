"use client";

import { useEffect } from "react";

export function KeyboardFocusRing() {
    useEffect(() => {
        const enable = () => {
            document.documentElement.dataset.keyboardFocus = "true";
        };
        const disable = () => {
            document.documentElement.dataset.keyboardFocus = "false";
        };

        window.addEventListener("keydown", enable);
        window.addEventListener("pointerdown", disable);
        enable();

        return () => {
            window.removeEventListener("keydown", enable);
            window.removeEventListener("pointerdown", disable);
            delete document.documentElement.dataset.keyboardFocus;
        };
    }, []);

    return null;
}
