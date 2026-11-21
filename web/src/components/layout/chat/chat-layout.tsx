import type { NavGroup } from "@/components/layout/types";

import { ChatConversationList } from "./chat-conversation-list";
import { ChatSidebarNav } from "./chat-sidebar-nav";

export function ChatLayout({ navGroups }: { navGroups: NavGroup[] }) {
  return (
    <>
      <ChatSidebarNav navGroups={navGroups} />
      <ChatConversationList />
    </>
  );
}
