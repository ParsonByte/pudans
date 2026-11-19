import type { ChatAttachmentRef } from "./chat-api";

type PendingChat = {
  conversationId: number;
  prompt: string;
  mode: string | undefined;
  attachments: ChatAttachmentRef[];
};

let pending: PendingChat | null = null;

export function setPendingChat(chat: PendingChat): void {
  pending = chat;
}

export function takePendingChat(conversationId: number): PendingChat | null {
  if (pending?.conversationId !== conversationId) {
    return null;
  }
  const chat = pending;
  pending = null;
  return chat;
}
