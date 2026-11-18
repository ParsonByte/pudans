import { ChatConversationList } from "./chat-conversation-list";
import { ChatSidebarNav } from "./chat-sidebar-nav";

export function ChatLayout() {
  return (
    <>
      <ChatSidebarNav />
      <ChatConversationList />
    </>
  );
}
