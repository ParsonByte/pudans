import { useState } from "react";

import { useLocation, useNavigate } from "@tanstack/react-router";
import {
  ArrowUpToLine,
  ChevronDown,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Trash2,
} from "lucide-react";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";
import {
  CHAT_CONVERSATIONS,
  type ChatConversation,
} from "@/modules/workspace/index/lib/chat-conversations";

import type { NavUrl } from "../types";

export function ChatConversationList() {
  const navigate = useNavigate();
  const pathname = useLocation({ select: (location) => location.pathname });
  const [conversations, setConversations] = useState<ChatConversation[]>(CHAT_CONVERSATIONS);
  const [pinnedOpen, setPinnedOpen] = useState(true);
  const [recentOpen, setRecentOpen] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<ChatConversation | null>(null);

  const pinned = conversations.filter((conversation) => conversation.pinned);
  const recent = conversations.filter((conversation) => !conversation.pinned);

  const togglePin = (id: string) => {
    setConversations((items) =>
      items.map((item) => (item.id === id ? { ...item, pinned: !item.pinned } : item)),
    );
  };

  const renameConversation = (id: string, title: string) => {
    const nextTitle = title.trim();
    if (nextTitle) {
      setConversations((items) =>
        items.map((item) => (item.id === id ? { ...item, title: nextTitle } : item)),
      );
    }
    setEditingId(null);
  };

  const deleteConversation = () => {
    if (!deleting) {
      return;
    }
    setConversations((items) => items.filter((item) => item.id !== deleting.id));
    setDeleting(null);
  };

  const rowActions = {
    currentPath: pathname,
    onSelect: (href: NavUrl) => void navigate({ to: href }),
    onTogglePin: togglePin,
    editingId,
    onStartEdit: setEditingId,
    onFinishEdit: renameConversation,
    onCancelEdit: () => setEditingId(null),
    onRequestDelete: setDeleting,
  };

  return (
    <>
      {pinned.length > 0 ? (
        <SidebarGroup>
          <Collapsible open={pinnedOpen} onOpenChange={setPinnedOpen}>
            <ConversationHeader label="已置顶" open={pinnedOpen} />
            <CollapsibleContent className="CollapsibleContent">
              <ConversationRows conversations={pinned} {...rowActions} />
            </CollapsibleContent>
          </Collapsible>
        </SidebarGroup>
      ) : null}
      <SidebarGroup>
        <Collapsible open={recentOpen} onOpenChange={setRecentOpen}>
          <div className="flex h-8 items-center justify-between px-2 group-data-[collapsible=icon]:hidden">
            <CollapsibleTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                className="h-7 gap-1 px-2 text-xs font-medium text-sidebar-foreground/70 hover:text-sidebar-foreground"
              >
                对话
                <ChevronDown
                  className={cn("size-3.5 transition-transform", !recentOpen && "-rotate-90")}
                />
              </Button>
            </CollapsibleTrigger>
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground"
            >
              查看全部
            </Button>
          </div>
          <CollapsibleContent className="CollapsibleContent">
            <ConversationRows conversations={recent} {...rowActions} />
          </CollapsibleContent>
        </Collapsible>
      </SidebarGroup>
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleting(null);
          }
        }}
        title="删除对话"
        desc={deleting ? `确定删除「${deleting.title}」？删除后无法恢复。` : ""}
        destructive
        confirmText="删除"
        handleConfirm={deleteConversation}
      />
    </>
  );
}

function ConversationHeader({ label, open }: { label: string; open: boolean }) {
  return (
    <div className="flex h-8 items-center justify-between px-2 group-data-[collapsible=icon]:hidden">
      <CollapsibleTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="h-7 gap-1 px-2 text-xs font-medium text-sidebar-foreground/70 hover:text-sidebar-foreground"
        >
          {label}
          <ChevronDown className={cn("size-3.5 transition-transform", !open && "-rotate-90")} />
        </Button>
      </CollapsibleTrigger>
    </div>
  );
}

type ConversationRowsProps = {
  conversations: ChatConversation[];
  currentPath: string;
  onSelect: (href: NavUrl) => void;
  editingId: string | null;
  onTogglePin: (id: string) => void;
  onStartEdit: (id: string) => void;
  onFinishEdit: (id: string, title: string) => void;
  onCancelEdit: () => void;
  onRequestDelete: (conversation: ChatConversation) => void;
};

function ConversationRows({
  conversations,
  currentPath,
  onSelect,
  editingId,
  onTogglePin,
  onStartEdit,
  onFinishEdit,
  onCancelEdit,
  onRequestDelete,
}: ConversationRowsProps) {
  return (
    <SidebarMenu className="gap-0.5 pt-1">
      {conversations.map((conversation) => {
        const href: NavUrl = `/chat/${conversation.id}`;
        const editing = editingId === conversation.id;

        return (
          <SidebarMenuItem key={conversation.id}>
            {editing ? (
              <Input
                autoFocus
                defaultValue={conversation.title}
                className="h-8 min-w-0"
                onFocus={(event) => event.target.select()}
                onBlur={(event) => onFinishEdit(conversation.id, event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    onFinishEdit(conversation.id, event.currentTarget.value);
                  } else if (event.key === "Escape") {
                    event.preventDefault();
                    onCancelEdit();
                  }
                }}
              />
            ) : (
              <>
                <SidebarMenuButton
                  isActive={currentPath === href}
                  tooltip={conversation.title}
                  className="h-10"
                  onClick={() => onSelect(href)}
                >
                  <MessageSquare className="size-4" />
                  <span>{conversation.title}</span>
                </SidebarMenuButton>
                {conversation.unread ? (
                  <SidebarMenuBadge className="bg-transparent">
                    <span className="size-1.5 rounded-full bg-blue-500" />
                  </SidebarMenuBadge>
                ) : null}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <SidebarMenuAction showOnHover aria-label="更多操作">
                      <MoreHorizontal />
                    </SidebarMenuAction>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent side="right" align="start" sideOffset={4} className="w-40">
                    <DropdownMenuItem onClick={() => onStartEdit(conversation.id)}>
                      <Pencil />
                      编辑标题
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => onTogglePin(conversation.id)}>
                      <ArrowUpToLine />
                      {conversation.pinned ? "取消置顶" : "置顶"}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      variant="destructive"
                      onClick={() => onRequestDelete(conversation)}
                    >
                      <Trash2 />
                      删除
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </>
            )}
          </SidebarMenuItem>
        );
      })}
    </SidebarMenu>
  );
}
