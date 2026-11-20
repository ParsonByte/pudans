import { FileText, Image as ImageIcon, Loader2, Video } from "lucide-react";

import { Message, MessageContent, MessageHeader } from "@/components/ui/message";
import { Progress } from "@/components/ui/progress";

import type { ChatGenerationProgress } from "../../lib/chat-generation";

type GenerationProgressProps = {
  progress: ChatGenerationProgress;
};

const KIND_LABELS = {
  IMAGE: "图片",
  VIDEO: "视频",
  DOCUMENT: "文档",
} as const;

export function GenerationProgress({ progress }: GenerationProgressProps) {
  const Icon = progress.kind === "IMAGE" ? ImageIcon : progress.kind === "VIDEO" ? Video : FileText;
  return (
    <Message>
      <MessageContent>
        <MessageHeader>
          PUDANS
          <span className="ms-2 inline-flex items-center gap-1 text-muted-foreground">
            <Loader2 className="size-3 animate-spin" />
            生成中
          </span>
        </MessageHeader>
        <div className="w-full space-y-2 rounded-xl border bg-muted/30 px-3 py-2.5 text-xs">
          <div className="flex items-center gap-2">
            <Icon className="size-3.5 text-muted-foreground" />
            <span className="font-medium">正在生成{KIND_LABELS[progress.kind]}</span>
            <span className="ms-auto text-muted-foreground">{progress.progress}%</span>
          </div>
          <p className="line-clamp-2 text-muted-foreground">{progress.prompt}</p>
          <Progress value={progress.progress} />
          <p className="text-muted-foreground">{progress.stage}</p>
        </div>
      </MessageContent>
    </Message>
  );
}
