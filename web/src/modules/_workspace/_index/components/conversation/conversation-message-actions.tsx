"use client";

import dynamic from "next/dynamic";

const ChatActionBar = dynamic(() => import("@tdesign-react/chat").then((module) => module.ChatActionBar), {
    ssr: false,
});

type ConversationMessageActionsProps = {
    canReplay: boolean;
    onReplay: () => void;
    text: string;
};

export function ConversationMessageActions({ canReplay, onReplay, text }: ConversationMessageActionsProps) {
    return (
        <ChatActionBar
            actionBar={canReplay ? ["copy", "replay"] : ["copy"]}
            copyText={text}
            handleAction={(name) => {
                if (name === "replay") {
                    onReplay();
                }
            }}
        />
    );
}
