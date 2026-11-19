import { Link } from "@tanstack/react-router";
import { Copy, FileText, Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Button } from "@/components/ui/button";
import { Message, MessageContent, MessageFooter, MessageHeader } from "@/components/ui/message";
import {
  MessageScroller,
  MessageScrollerButton,
  MessageScrollerContent,
  MessageScrollerItem,
  MessageScrollerProvider,
  MessageScrollerViewport,
} from "@/components/ui/message-scroller";
import { Skeleton } from "@/components/ui/skeleton";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { formatClock } from "../../../ai/routing/lib/model";
import {
  type ChatAttachmentRef,
  type ChatMessage,
  type ChatMessageAttachment,
  chatAttachmentUrl,
} from "../../lib/chat-api";
import { MessageMarkdown } from "./message-markdown";
import { ThinkingBlock } from "./thinking-block";

type ConversationMessagesProps = {
  messages: ChatMessage[];
  loading: boolean;
  error: string | null;
  optimisticMessage: { content: string; attachments: ChatAttachmentRef[] } | null;
  assistantDraft: string;
  reasoningDraft: string;
  sending: boolean;
};

export function ConversationMessages({
  messages,
  loading,
  error,
  optimisticMessage,
  assistantDraft,
  reasoningDraft,
  sending,
}: ConversationMessagesProps) {
  const routeGroupsPath = useMenuPath("ai-route-groups");
  const visibleMessages = messages.filter((message) => Boolean(message.content));
  const empty =
    visibleMessages.length === 0 && !optimisticMessage && !assistantDraft && !reasoningDraft;
  const routeHint =
    Boolean(error) &&
    Boolean(error?.includes("路由组")) &&
    !error?.includes("熔断") &&
    !error?.includes("健康度过低");

  return (
    <MessageScrollerProvider>
      <div className="min-h-0 flex-1">
        <MessageScroller>
          <MessageScrollerViewport>
            <MessageScrollerContent className="mx-auto w-full max-w-3xl px-6 py-6">
              {loading ? (
                <div className="space-y-4">
                  <Skeleton className="ms-auto h-16 w-3/5 rounded-xl" />
                  <Skeleton className="h-24 w-4/5 rounded-xl" />
                </div>
              ) : empty ? (
                <div className="flex min-h-48 items-center justify-center text-center text-sm text-muted-foreground">
                  开始新的对话吧。
                </div>
              ) : (
                <>
                  {visibleMessages.map((message) => (
                    <MessageScrollerItem key={message.id}>
                      <MessageRow
                        role={message.role}
                        content={message.content ?? ""}
                        attachments={message.metadata?.attachments}
                        reasoning={message.metadata?.reasoning}
                        modelCode={message.modelCode}
                        tokenCount={message.tokenCount}
                        createdAt={message.createdAt}
                      />
                    </MessageScrollerItem>
                  ))}
                  {optimisticMessage ? (
                    <MessageScrollerItem>
                      <UserMessage
                        content={optimisticMessage.content}
                        attachments={optimisticMessage.attachments.map((attachment) => ({
                          fileId: attachment.fileId,
                          type: attachment.type,
                          name: attachment.name,
                          contentType: attachment.contentType,
                          size: attachment.size,
                        }))}
                      />
                    </MessageScrollerItem>
                  ) : null}
                  {assistantDraft || sending || reasoningDraft ? (
                    <MessageScrollerItem>
                      <AssistantMessage
                        content={assistantDraft}
                        reasoning={reasoningDraft}
                        streaming
                        pending={!assistantDraft && !reasoningDraft}
                      />
                    </MessageScrollerItem>
                  ) : null}
                </>
              )}

              {error ? (
                <div className="mt-4 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-xs text-destructive">
                  {error}
                  {routeHint && routeGroupsPath ? (
                    <Link
                      to={routeGroupsPath}
                      className="ms-2 inline-block font-medium underline underline-offset-2"
                    >
                      去配置路由组
                    </Link>
                  ) : null}
                </div>
              ) : null}
            </MessageScrollerContent>
          </MessageScrollerViewport>
          <MessageScrollerButton />
        </MessageScroller>
      </div>
    </MessageScrollerProvider>
  );
}

function MessageRow({
  role,
  content,
  attachments,
  reasoning,
  modelCode,
  tokenCount,
  createdAt,
}: {
  role: string;
  content: string;
  attachments?: ChatMessageAttachment[];
  reasoning?: string;
  modelCode?: string;
  tokenCount?: number;
  createdAt: string;
}) {
  if (role === "user") {
    return <UserMessage content={content} attachments={attachments} createdAt={createdAt} />;
  }
  return (
    <AssistantMessage
      content={content}
      reasoning={reasoning}
      modelCode={modelCode}
      tokenCount={tokenCount}
      createdAt={createdAt}
    />
  );
}

function UserMessage({
  content,
  attachments,
  createdAt,
}: {
  content: string;
  attachments?: ChatMessageAttachment[];
  createdAt?: string;
}) {
  const items = (attachments ?? []).filter((attachment) => attachment.fileId);
  const images = items.filter(
    (attachment) => attachment.type === "IMAGE" || attachment.contentType?.startsWith("image/"),
  );
  const videos = items.filter(
    (attachment) => attachment.type === "VIDEO" || attachment.contentType?.startsWith("video/"),
  );
  const audios = items.filter(
    (attachment) => attachment.type === "AUDIO" || attachment.contentType?.startsWith("audio/"),
  );
  const files = items.filter(
    (attachment) =>
      !images.includes(attachment) && !videos.includes(attachment) && !audios.includes(attachment),
  );
  return (
    <Message align="end">
      <MessageContent>
        {items.length > 0 ? (
          <div className="flex flex-wrap justify-end gap-2">
            {images.map((attachment) => (
              <img
                key={attachment.fileId}
                src={chatAttachmentUrl(attachment.fileId)}
                alt={attachment.name || "图片"}
                className="max-h-64 max-w-64 rounded-xl border object-cover"
              />
            ))}
            {videos.map((attachment) => (
              <video
                key={attachment.fileId}
                src={chatAttachmentUrl(attachment.fileId)}
                controls
                className="max-h-64 max-w-64 rounded-xl border"
              />
            ))}
            {audios.map((attachment) => (
              <audio
                key={attachment.fileId}
                src={chatAttachmentUrl(attachment.fileId)}
                controls
                className="w-64"
              />
            ))}
            {files.map((attachment) => (
              <a
                key={attachment.fileId}
                href={chatAttachmentUrl(attachment.fileId)}
                download
                className="inline-flex max-w-72 items-center gap-2 rounded-lg border bg-background/60 px-3 py-2 text-xs transition-colors hover:bg-muted"
              >
                <FileText className="size-4 shrink-0 text-muted-foreground" />
                <span className="truncate">{attachment.name || "附件"}</span>
                {attachment.size != null ? (
                  <span className="shrink-0 text-muted-foreground">
                    {formatFileSize(attachment.size)}
                  </span>
                ) : null}
              </a>
            ))}
          </div>
        ) : null}
        <Bubble align="end" variant="muted">
          <BubbleContent className="whitespace-pre-wrap">{content}</BubbleContent>
        </Bubble>
        <MessageFooter>
          <CopyMessageButton content={content} />
          {createdAt ? (
            <span className="text-muted-foreground">{formatClock(createdAt)}</span>
          ) : null}
        </MessageFooter>
      </MessageContent>
    </Message>
  );
}

function AssistantMessage({
  content,
  reasoning,
  modelCode,
  tokenCount,
  createdAt,
  streaming = false,
  pending = false,
}: {
  content: string;
  reasoning?: string;
  modelCode?: string;
  tokenCount?: number;
  createdAt?: string;
  streaming?: boolean;
  pending?: boolean;
}) {
  return (
    <Message>
      <MessageContent>
        <MessageHeader>
          PUDANS
          {modelCode ? <span className="ms-2 font-mono text-[10px]">{modelCode}</span> : null}
          {streaming ? (
            <span className="ms-2 inline-flex items-center gap-1 text-muted-foreground">
              <Loader2 className="size-3 animate-spin" />
              生成中
            </span>
          ) : null}
        </MessageHeader>
        {reasoning || (streaming && pending) ? (
          <ThinkingBlock reasoning={reasoning ?? ""} streaming={streaming} />
        ) : null}
        {!pending ? (
          <Bubble variant="ghost">
            <BubbleContent className="text-sm leading-7">
              <MessageMarkdown content={content} />
            </BubbleContent>
          </Bubble>
        ) : null}
        {!streaming ? (
          <MessageFooter>
            <CopyMessageButton content={content} />
            {createdAt ? (
              <span className="text-muted-foreground">{formatClock(createdAt)}</span>
            ) : null}
            {tokenCount != null ? (
              <span className="ms-2 text-muted-foreground">{tokenCount} token</span>
            ) : null}
          </MessageFooter>
        ) : null}
      </MessageContent>
    </Message>
  );
}

function CopyMessageButton({ content }: { content: string }) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-xs"
      aria-label="复制"
      onClick={() => {
        void navigator.clipboard
          .writeText(content)
          .then(() => toast.success("内容已复制。"))
          .catch(() => toast.error("复制失败，请手动选择文本。"));
      }}
    >
      <Copy />
    </Button>
  );
}
