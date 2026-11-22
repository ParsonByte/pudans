"use client";

import { App } from "antd";

type MessagePrompt = ReturnType<typeof App.useApp>["message"];

export function useMessagePrompt(): MessagePrompt {
    return App.useApp().message;
}
