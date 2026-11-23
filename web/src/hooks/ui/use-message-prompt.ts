"use client";

import { createElement, useMemo } from "react";
import type { ReactNode } from "react";

import { CloseIcon } from "tdesign-icons-react";
import { Button, MessagePlugin, NotificationPlugin } from "tdesign-react";

type PromptContent = ReactNode | string;

type PromptOptions = {
    content: PromptContent;
    duration?: number;
    footer?: ReactNode;
    title?: ReactNode;
};

type PromptInput = PromptOptions | string;

type MessagePrompt = {
    error: (options: PromptInput) => void;
    info: (options: PromptInput) => void;
    success: (options: PromptInput) => void;
    warning: (options: PromptInput) => void;
};

const NOTIFICATION_CLOSE_BUTTON = createElement(Button, {
    "aria-label": "关闭通知",
    className: "notification-close-button",
    icon: createElement(CloseIcon),
    shape: "square",
    size: "small",
    variant: "text",
});

function resolveDuration(duration: number | undefined, fallback: number): number {
    return duration ?? fallback;
}

function showMessage(theme: "info" | "success", { content, duration }: PromptOptions): void {
    void MessagePlugin[theme]({
        content,
        duration: resolveDuration(duration, 3000),
        placement: "top",
    });
}

function showNotification(theme: "error" | "warning", { content, duration, footer, title }: PromptOptions): void {
    void NotificationPlugin[theme]({
        closeBtn: NOTIFICATION_CLOSE_BUTTON,
        content,
        duration: resolveDuration(duration, 4500),
        footer,
        placement: "top-right",
        title,
    });
}

function normalizePrompt(input: PromptInput): PromptOptions {
    return typeof input === "string" ? { content: input } : input;
}

export function useMessagePrompt(): MessagePrompt {
    return useMemo(
        () => ({
            error: (options) => showNotification("error", normalizePrompt(options)),
            info: (options) => showMessage("info", normalizePrompt(options)),
            success: (options) => showMessage("success", normalizePrompt(options)),
            warning: (options) => showNotification("warning", normalizePrompt(options)),
        }),
        [],
    );
}
