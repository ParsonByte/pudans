"use client";

import { useParams } from "next/navigation";

import { ChatConversationView } from "@/modules/workspace/index/conversation";

export default function ChatConversationPage() {
  const params = useParams<{ conversationId: string }>();

  return <ChatConversationView conversationId={params.conversationId} />;
}
