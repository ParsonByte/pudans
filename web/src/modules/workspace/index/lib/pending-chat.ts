import type { ChatAttachmentRef, ChatGenerationKind } from "./chat-api";

type PendingChatGeneration = {
  kind: ChatGenerationKind;
  format?: "MARKDOWN" | "HTML";
};

type PendingChat = {
  conversationId: number;
  prompt: string;
  mode: string | undefined;
  attachments: ChatAttachmentRef[];
  model?: string;
  routeGroup?: string;
  kbCodes?: string[];
  command?: string;
  commandInput?: string;
  generation?: PendingChatGeneration;
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
