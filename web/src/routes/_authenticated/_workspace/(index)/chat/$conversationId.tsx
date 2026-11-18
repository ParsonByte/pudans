import { createFileRoute } from "@tanstack/react-router";

import { ChatConversationView } from "@/modules/workspace/index/conversation";

export const Route = createFileRoute("/_authenticated/_workspace/(index)/chat/$conversationId")({
  component: ChatConversationRoute,
});

function ChatConversationRoute() {
  const { conversationId } = Route.useParams();

  return <ChatConversationView conversationId={conversationId} />;
}
