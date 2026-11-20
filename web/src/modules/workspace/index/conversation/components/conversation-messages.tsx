import { useState } from "react";

import { Link } from "@tanstack/react-router";
import { Copy, FileText, Loader2, PencilLine, RotateCcw } from "lucide-react";
import { toast } from "sonner";

import {
  Attachment,
  AttachmentContent,
  AttachmentDescription,
  AttachmentMedia,
  AttachmentTitle,
} from "@/components/ui/attachment";
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
import { formatBeijingClock } from "@/lib/date";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import {
  type ChatArtifact,
  type ChatAttachmentRef,
  type ChatMessage,
  type ChatMessageAttachment,
  type ChatStreamDraft,
  type ChatStreamStage,
  type ChatToolCall,
  type ChatToolResult,
  chatAttachmentUrl,
} from "../../lib/chat-api";
import type { ChatGenerationProgress } from "../../lib/chat-generation";
import { ArtifactBlock } from "./artifact-block";
import { EditBranchDialog } from "./edit-branch-dialog";
import { GenerationProgress } from "./generation-progress";
import { MessageMarkdown } from "./message-markdown";
import { ThinkingBlock } from "./thinking-block";
import { ToolCallsBlock } from "./tool-call-block";

type ConversationMessagesProps = {
  messages: ChatMessage[];
  loading: boolean;
  error: string | null;
  optimisticMessage: { content: string; attachments: ChatAttachmentRef[] } | null;
  draft: ChatStreamDraft;
  sending: boolean;
  generation: ChatGenerationProgress | null;
  onRetry: (message: ChatMessage) => void;
  onEditBranch: (message: ChatMessage, content: string) => Promise<boolean>;
};

export function ConversationMessages({
  messages,
  loading,
  error,
  optimisticMessage,
  draft,
  sending,
  generation,
  onRetry,
  onEditBranch,
}: ConversationMessagesProps) {
  const [editing, setEditing] = useState<ChatMessage | null>(null);
  const routeGroupsPath = useMenuPath("ai-route-groups");
  const visibleMessages = messages.filter(
    (message) =>
      Boolean(message.content) ||
      Boolean(message.metadata?.artifact) ||
      Boolean(message.metadata?.toolCalls?.length),
  );
  const lastAssistantId = [...visibleMessages]
    .reverse()
    .find((message) => message.role === "assistant")?.id;
  const draftVisible =
    Boolean(draft.content) ||
    Boolean(draft.reasoning) ||
    draft.toolCalls.length > 0 ||
    draft.artifacts.length > 0 ||
    Boolean(draft.stage);
  const draftPending = !draftVisible;
  const showDraft = draftVisible || (sending && !generation);
  const empty = visibleMessages.length === 0 && !optimisticMessage && !showDraft && !generation;
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
                        message={message}
                        retryable={!sending && message.id === lastAssistantId}
                        onRetry={onRetry}
                        onEdit={setEditing}
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
                  {showDraft ? (
                    <MessageScrollerItem>
                      <AssistantMessage
                        content={draft.content}
                        reasoning={draft.reasoning}
                        toolCalls={draft.toolCalls}
                        toolResults={draft.toolResults}
                        artifacts={draft.artifacts}
                        stage={draft.stage}
                        streaming
                        pending={draftPending}
                      />
                    </MessageScrollerItem>
                  ) : null}
                  {generation ? (
                    <MessageScrollerItem>
                      <GenerationProgress progress={generation} />
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
      <EditBranchDialog
        target={editing}
        onOpenChange={(open) => {
          if (!open) {
            setEditing(null);
          }
        }}
        onConfirm={onEditBranch}
      />
    </MessageScrollerProvider>
  );
}

function MessageRow({
  message,
  retryable,
  onRetry,
  onEdit,
}: {
  message: ChatMessage;
  retryable: boolean;
  onRetry: (message: ChatMessage) => void;
  onEdit: (message: ChatMessage) => void;
}) {
  if (message.role === "user") {
    return (
      <UserMessage
        content={message.content ?? ""}
        attachments={message.metadata?.attachments}
        createdAt={message.createdAt}
        onEdit={() => onEdit(message)}
      />
    );
  }
  return (
    <AssistantMessage
      content={message.content ?? ""}
      reasoning={message.metadata?.reasoning}
      toolCalls={message.metadata?.toolCalls}
      toolResults={message.metadata?.toolResults}
      artifact={message.metadata?.artifact}
      modelCode={message.modelCode}
      tokenCount={message.tokenCount}
      createdAt={message.createdAt}
      onRetry={retryable ? () => onRetry(message) : undefined}
    />
  );
}

function UserMessage({
  content,
  attachments,
  createdAt,
  onEdit,
}: {
  content: string;
  attachments?: ChatMessageAttachment[];
  createdAt?: string;
  onEdit?: () => void;
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
                className="w-fit max-w-72"
              >
                <Attachment size="sm" className="max-w-full transition-colors hover:bg-muted/50">
                  <AttachmentMedia>
                    <FileText />
                  </AttachmentMedia>
                  <AttachmentContent>
                    <AttachmentTitle>{attachment.name || "附件"}</AttachmentTitle>
                    {attachment.size != null ? (
                      <AttachmentDescription>
                        {formatFileSize(attachment.size)}
                      </AttachmentDescription>
                    ) : null}
                  </AttachmentContent>
                </Attachment>
              </a>
            ))}
          </div>
        ) : null}
        <Bubble align="end" variant="muted">
          <BubbleContent className="whitespace-pre-wrap">{content}</BubbleContent>
        </Bubble>
        <MessageFooter>
          <CopyMessageButton content={content} />
          {onEdit ? (
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              aria-label="编辑并分支"
              onClick={onEdit}
            >
              <PencilLine />
            </Button>
          ) : null}
          {createdAt ? (
            <span className="text-muted-foreground">{formatBeijingClock(createdAt)}</span>
          ) : null}
        </MessageFooter>
      </MessageContent>
    </Message>
  );
}

function AssistantMessage({
  content,
  reasoning,
  toolCalls = [],
  toolResults = [],
  artifact,
  artifacts = [],
  stage,
  modelCode,
  tokenCount,
  createdAt,
  streaming = false,
  pending = false,
  onRetry,
}: {
  content: string;
  reasoning?: string;
  toolCalls?: ChatToolCall[];
  toolResults?: ChatToolResult[];
  artifact?: ChatArtifact;
  artifacts?: ChatArtifact[];
  stage?: ChatStreamStage | null;
  modelCode?: string;
  tokenCount?: number;
  createdAt?: string;
  streaming?: boolean;
  pending?: boolean;
  onRetry?: () => void;
}) {
  const displayedArtifacts = artifact ? [...artifacts, artifact] : artifacts;
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
        {toolCalls.length > 0 || toolResults.length > 0 ? (
          <ToolCallsBlock calls={toolCalls} results={toolResults} streaming={streaming} />
        ) : null}
        {streaming && stage ? (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Loader2 className="size-3 animate-spin" />
            {stage.detail || stage.stage || "处理中"}
          </div>
        ) : null}
        {!pending && content ? (
          <Bubble variant="ghost">
            <BubbleContent className="text-sm leading-7">
              <MessageMarkdown content={content} />
            </BubbleContent>
          </Bubble>
        ) : null}
        {displayedArtifacts.length > 0 ? (
          <div className="w-full space-y-2">
            {displayedArtifacts.map((item, index) => (
              <ArtifactBlock
                key={item.fileId ?? item.url ?? `${item.kind}-${index}`}
                artifact={item}
              />
            ))}
          </div>
        ) : null}
        {!streaming ? (
          <MessageFooter>
            <CopyMessageButton content={content} />
            {onRetry ? (
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                aria-label="重新生成"
                onClick={onRetry}
              >
                <RotateCcw />
              </Button>
            ) : null}
            {createdAt ? (
              <span className="text-muted-foreground">{formatBeijingClock(createdAt)}</span>
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
