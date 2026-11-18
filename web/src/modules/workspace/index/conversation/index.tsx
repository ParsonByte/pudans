import { ChatComposer } from "../components/chat-composer";
import { findChatConversation } from "../lib/chat-conversations";
import { ConversationHeader } from "./components/conversation-header";
import { ConversationMessages } from "./components/conversation-messages";

type ChatConversationViewProps = {
  conversationId: string;
};

export function ChatConversationView({ conversationId }: ChatConversationViewProps) {
  const conversation = findChatConversation(conversationId);

  return (
    <div className="flex h-svh min-h-0 flex-col">
      <ConversationHeader title={conversation?.title ?? "新的对话"} />
      <ConversationMessages />
      <div className="shrink-0 px-6 pb-3">
        <div className="mx-auto w-full max-w-3xl space-y-2">
          <ChatComposer placeholder="尽管问，带图也行" showSuggestions={false} />
          <p className="text-center text-xs text-muted-foreground">内容由 AI 生成，请仔细甄别</p>
        </div>
      </div>
    </div>
  );
}
