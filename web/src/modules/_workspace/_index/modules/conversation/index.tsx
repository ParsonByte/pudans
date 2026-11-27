"use client";

import { ConversationShell } from "../../components/conversation/conversation-shell";
import { useConversationController } from "../../composables/_conversation-controller";

export function ConversationView() {
    const controller = useConversationController();

    return <ConversationShell controller={controller} />;
}
