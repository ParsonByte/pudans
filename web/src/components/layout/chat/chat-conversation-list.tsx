import { useEffect, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowUpToLine,
  ChevronDown,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Trash2,
} from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { toast } from "sonner";

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
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import {
  type ChatConversation as ApiChatConversation,
  deleteChatConversation,
  fetchChatConversations,
  resolveChatError,
} from "@/modules/workspace/index/lib/chat-api";
import {
  type ChatPreferences,
  loadChatPreferences,
  saveChatPreferences,
} from "@/modules/workspace/index/lib/chat-preferences";

const CONVERSATION_LIMIT = 100;

const RECENT_PREVIEW = 20;

type ChatConversation = ApiChatConversation & {
  displayTitle: string;
  pinned: boolean;
  unread: boolean;
};

export function ChatConversationList() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const pathname = usePathname();
  const [preferences, setPreferences] = useState<ChatPreferences>(loadChatPreferences);
  const [pinnedOpen, setPinnedOpen] = useState(true);
  const [recentOpen, setRecentOpen] = useState(true);
  const [showAll, setShowAll] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<ChatConversation | null>(null);

  const conversationsQuery = useQuery({
    queryKey: ["chat-conversations"],
    queryFn: () => fetchChatConversations(CONVERSATION_LIMIT),
    retry: false,
  });

  useEffect(() => {
    saveChatPreferences(preferences);
  }, [preferences]);

  const conversations = (conversationsQuery.data ?? []).map((conversation) => {
    const key = String(conversation.id);
    const seen = preferences.seen[key];
    return {
      ...conversation,
      displayTitle: preferences.aliases[key]?.trim() || conversation.title?.trim() || "新的对话",
      pinned: preferences.pinned.includes(key),
      unread: !seen || conversation.lastActivityAt > seen,
    };
  });
  const pinned = conversations.filter((conversation) => conversation.pinned);
  const recent = conversations.filter((conversation) => !conversation.pinned);
  const visibleRecent = showAll ? recent : recent.slice(0, RECENT_PREVIEW);

  const markSeen = (conversation: ChatConversation) => {
    const key = String(conversation.id);
    setPreferences((current) =>
      current.seen[key] === conversation.lastActivityAt
        ? current
        : {
            ...current,
            seen: { ...current.seen, [key]: conversation.lastActivityAt },
          },
    );
  };

  const togglePin = (conversation: ChatConversation) => {
    const key = String(conversation.id);
    setPreferences((current) => ({
      ...current,
      pinned: current.pinned.includes(key)
        ? current.pinned.filter((id) => id !== key)
        : [...current.pinned, key],
    }));
  };

  const renameConversation = (conversation: ChatConversation, title: string) => {
    const nextTitle = title.trim();
    const key = String(conversation.id);
    setPreferences((current) => {
      const aliases = { ...current.aliases };
      if (nextTitle && nextTitle !== conversation.title) {
        aliases[key] = nextTitle;
      } else {
        delete aliases[key];
      }
      return { ...current, aliases };
    });
    setEditingId(null);
  };

  const deleteMutation = useMutation({
    mutationFn: (conversation: ChatConversation) => deleteChatConversation(conversation.id),
    onSuccess: async (_result, conversation) => {
      const key = String(conversation.id);
      setPreferences((current) => ({
        pinned: current.pinned.filter((id) => id !== key),
        aliases: Object.fromEntries(Object.entries(current.aliases).filter(([id]) => id !== key)),
        seen: Object.fromEntries(Object.entries(current.seen).filter(([id]) => id !== key)),
      }));
      await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
      if (pathname === `/chat/${conversation.id}`) {
        router.push("/");
      }
      setDeleting(null);
      toast.success("对话已删除。");
    },
    onError: (error) => toast.error(resolveChatError(error)),
  });

  const rowActions = {
    currentPath: pathname,
    editingId,
    onSelect: (conversation: ChatConversation) => {
      markSeen(conversation);
      router.push(`/chat/${conversation.id}`);
    },
    onTogglePin: togglePin,
    onStartEdit: (conversation: ChatConversation) => setEditingId(String(conversation.id)),
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
            {recent.length > RECENT_PREVIEW ? (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground"
                onClick={() => setShowAll((value) => !value)}
              >
                {showAll ? "收起" : "查看全部"}
              </Button>
            ) : null}
          </div>
          <CollapsibleContent className="CollapsibleContent">
            {conversationsQuery.isPending ? (
              <div className="space-y-1 px-2 pt-1">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : conversationsQuery.isError ? (
              <p className="px-3 py-2 text-xs text-muted-foreground">无法加载对话。</p>
            ) : visibleRecent.length === 0 && pinned.length === 0 ? (
              <p className="px-3 py-2 text-xs text-muted-foreground">还没有对话。</p>
            ) : (
              <ConversationRows conversations={visibleRecent} {...rowActions} />
            )}
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
        desc={deleting ? `确定删除「${deleting.displayTitle}」？删除后无法恢复。` : ""}
        destructive
        confirmText="删除"
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleting) {
            deleteMutation.mutate(deleting);
          }
        }}
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
  editingId: string | null;
  onSelect: (conversation: ChatConversation) => void;
  onTogglePin: (conversation: ChatConversation) => void;
  onStartEdit: (conversation: ChatConversation) => void;
  onFinishEdit: (conversation: ChatConversation, title: string) => void;
  onCancelEdit: () => void;
  onRequestDelete: (conversation: ChatConversation) => void;
};

function ConversationRows({
  conversations,
  currentPath,
  editingId,
  onSelect,
  onTogglePin,
  onStartEdit,
  onFinishEdit,
  onCancelEdit,
  onRequestDelete,
}: ConversationRowsProps) {
  return (
    <SidebarMenu className="gap-0.5 pt-1">
      {conversations.map((conversation) => {
        const href = `/chat/${conversation.id}`;
        const editing = editingId === String(conversation.id);

        return (
          <SidebarMenuItem key={conversation.id}>
            {editing ? (
              <Input
                autoFocus
                defaultValue={conversation.displayTitle}
                className="h-8 min-w-0"
                onFocus={(event) => event.target.select()}
                onBlur={(event) => onFinishEdit(conversation, event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    onFinishEdit(conversation, event.currentTarget.value);
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
                  tooltip={conversation.displayTitle}
                  className="h-10"
                  onClick={() => onSelect(conversation)}
                >
                  <MessageSquare className="size-4" />
                  <span>{conversation.displayTitle}</span>
                </SidebarMenuButton>
                {conversation.unread ? (
                  <SidebarMenuBadge className="bg-transparent">
                    <span className="size-1.5 bg-primary" />
                  </SidebarMenuBadge>
                ) : null}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <SidebarMenuAction showOnHover aria-label="更多操作">
                      <MoreHorizontal />
                    </SidebarMenuAction>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent side="right" align="start" sideOffset={4} className="w-40">
                    <DropdownMenuItem onClick={() => onStartEdit(conversation)}>
                      <Pencil />
                      编辑标题
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => onTogglePin(conversation)}>
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
      {conversations.length === 0 ? (
        <p className="px-3 py-2 text-xs text-muted-foreground">还没有对话。</p>
      ) : null}
    </SidebarMenu>
  );
}
