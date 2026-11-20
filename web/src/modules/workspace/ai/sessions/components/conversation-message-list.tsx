import { useMemo } from "react";

import { useInfiniteQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";

import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Button } from "@/components/ui/button";
import { Message, MessageContent, MessageFooter, MessageHeader } from "@/components/ui/message";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";
import { MessageMarkdown } from "@/modules/workspace/index/conversation/components/message-markdown";

import { type ChatMessage, fetchConversationMessages, resolveAiSessionApiError } from "../lib/api";
import { CONVERSATION_MESSAGE_PAGE_SIZE, messageRoleLabel } from "../lib/model";

type ConversationMessageListProps = {
  conversationId: number | null;
};

export function ConversationMessageList({ conversationId }: ConversationMessageListProps) {
  const messagesQuery = useInfiniteQuery({
    queryKey: ["ai-sessions", "conversation-messages", conversationId ?? "none"],
    queryFn: ({ pageParam }) =>
      fetchConversationMessages(conversationId ?? 0, {
        afterId: pageParam,
        limit: CONVERSATION_MESSAGE_PAGE_SIZE,
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.length === CONVERSATION_MESSAGE_PAGE_SIZE ? lastPage.at(-1)?.id : undefined,
    enabled: conversationId != null,
    retry: false,
  });

  const messages = useMemo(() => messagesQuery.data?.pages.flat() ?? [], [messagesQuery.data]);
  const refreshing = messagesQuery.isFetching && !messagesQuery.isFetchingNextPage;

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="space-y-0.5">
          <h3 className="text-sm font-semibold">消息</h3>
          <p className="text-xs text-muted-foreground">
            {messagesQuery.isPending ? "正在加载消息…" : `已加载 ${messages.length} 条消息`}
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => void messagesQuery.refetch()}
          disabled={messagesQuery.isFetching || conversationId == null}
        >
          {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          刷新
        </Button>
      </div>

      {messagesQuery.isPending ? (
        <div className="space-y-3 rounded-lg border bg-muted/20 p-4">
          <Skeleton className="ms-auto h-14 w-3/5 rounded-xl" />
          <Skeleton className="h-20 w-4/5 rounded-xl" />
          <Skeleton className="h-16 w-2/3 rounded-xl" />
        </div>
      ) : messagesQuery.isError ? (
        <div className="flex min-h-40 flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-6 text-center">
          <p className="text-sm font-medium">无法加载消息</p>
          <p className="max-w-md text-xs text-muted-foreground">
            {resolveAiSessionApiError(messagesQuery.error)}
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void messagesQuery.refetch()}
          >
            重试
          </Button>
        </div>
      ) : messages.length === 0 ? (
        <div className="flex min-h-40 items-center justify-center rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
          该会话还没有消息。
        </div>
      ) : (
        <ScrollArea className="h-[46vh] rounded-lg border bg-muted/20">
          <div className="space-y-5 p-4">
            {messages.map((message) => (
              <SessionMessage key={message.id} message={message} />
            ))}
            {messagesQuery.hasNextPage ? (
              <div className="flex justify-center pt-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => void messagesQuery.fetchNextPage()}
                  disabled={messagesQuery.isFetchingNextPage}
                >
                  {messagesQuery.isFetchingNextPage ? <Loader2 className="animate-spin" /> : null}
                  加载更多
                </Button>
              </div>
            ) : (
              <p className="pt-1 text-center text-xs text-muted-foreground">已到会话末尾</p>
            )}
          </div>
        </ScrollArea>
      )}
    </div>
  );
}

function SessionMessage({ message }: { message: ChatMessage }) {
  const isUser = message.role === "user";
  const content = message.content ?? "";
  const hasContent = content.trim().length > 0;

  return (
    <Message align={isUser ? "end" : "start"}>
      <MessageContent>
        <MessageHeader>
          {messageRoleLabel(message.role)}
          {message.modelCode ? (
            <span className="ms-2 font-mono text-[10px]">{message.modelCode}</span>
          ) : null}
        </MessageHeader>
        {hasContent ? (
          isUser ? (
            <Bubble align="end" variant="muted">
              <BubbleContent className="whitespace-pre-wrap">{content}</BubbleContent>
            </Bubble>
          ) : (
            <Bubble variant="ghost">
              <BubbleContent className="text-sm leading-7">
                <MessageMarkdown content={content} />
              </BubbleContent>
            </Bubble>
          )
        ) : (
          <p className="px-3 text-xs text-muted-foreground">该消息没有文本内容。</p>
        )}
        <MessageFooter>
          <span>{formatUserDateTime(message.createdAt)}</span>
          {message.tokenCount != null ? (
            <span className="ms-2">{formatTokenCount(message.tokenCount)} token</span>
          ) : null}
        </MessageFooter>
      </MessageContent>
    </Message>
  );
}
