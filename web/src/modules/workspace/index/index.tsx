import { useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";

import { PageHeader } from "@/components/layout/page-header";

import { ChatComposer } from "./components/chat-composer";
import {
  type ChatAttachmentRef,
  createChatConversation,
  fetchChatModes,
  resolveChatError,
} from "./lib/chat-api";
import { setPendingChat } from "./lib/pending-chat";

const TITLE_LIMIT = 80;

export function ChatNewView() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [modeOverride, setModeOverride] = useState<string | undefined>(undefined);
  const [sending, setSending] = useState(false);
  const modesQuery = useQuery({
    queryKey: ["chat-modes"],
    queryFn: fetchChatModes,
    retry: false,
  });
  const modes = modesQuery.data ?? [];
  const mode = modeOverride ?? modes[0]?.name;

  const send = async (prompt: string, attachments: ChatAttachmentRef[]) => {
    if (sending) {
      return;
    }
    setSending(true);
    try {
      const title = prompt.length <= TITLE_LIMIT ? prompt : prompt.slice(0, TITLE_LIMIT);
      const conversation = await createChatConversation({ title, modeCode: mode });
      await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
      setPendingChat({
        conversationId: conversation.id,
        prompt,
        mode,
        attachments,
      });
      await navigate({
        to: "/chat/$conversationId",
        params: { conversationId: String(conversation.id) },
      });
    } catch (error) {
      const message = resolveChatError(error);
      toast.error(message);
      throw error;
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="flex h-svh min-h-0 flex-col">
      <PageHeader />
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-10 overflow-y-auto px-6 py-12">
        <h1 className="text-5xl font-semibold tracking-[0.18em] sm:text-6xl">PUDANS</h1>
        <div className="w-full max-w-3xl space-y-2">
          <ChatComposer
            modes={modes}
            mode={mode}
            onModeChange={setModeOverride}
            onSend={send}
            sending={sending}
            placeholder="问点什么，或直接开始一个新会话"
          />
          <p className="text-center text-xs text-muted-foreground">内容由 AI 生成，请仔细甄别</p>
        </div>
      </div>
    </div>
  );
}
