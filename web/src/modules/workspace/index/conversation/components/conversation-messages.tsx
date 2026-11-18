import { Copy, Pencil, RotateCcw, Share2, ThumbsDown, ThumbsUp, Volume2 } from "lucide-react";
import { toast } from "sonner";

import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Button } from "@/components/ui/button";
import {
  CodeBlock,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockTitle,
} from "@/components/ui/code-block";
import { Message, MessageContent, MessageFooter } from "@/components/ui/message";
import {
  MessageScroller,
  MessageScrollerButton,
  MessageScrollerContent,
  MessageScrollerItem,
  MessageScrollerProvider,
  MessageScrollerViewport,
} from "@/components/ui/message-scroller";

const WEBHOOK_PAYLOAD = `{
  "action": "opened",
  "pull_request": {
    "title": "docs: update identity sync guide",
    "merged": false
  }
}`;

const QUESTION_TEXT = "帮我整理一下飞书机器人接入 GitHub PR 通知的配置。";

const REPLY_TEXT = `飞书机器人接入 GitHub PR 通知

先配置 GitHub Webhook，再把事件转发到飞书机器人。每个 PR 只发一条通知，建议在卡片前面加分支节点。

JSON：
${WEBHOOK_PAYLOAD}

两个注意点
1. merged 字段在 payload 里是布尔值，条件分支按字符串匹配可能失效。
2. 真正的批准人在 Pull request review 事件里，这个触发器收不到。

如果平台变量格式不同，以界面上实际显示的变量为准。`;

export function ConversationMessages() {
  const copyQuestion = () => {
    void navigator.clipboard
      .writeText(QUESTION_TEXT)
      .then(() => toast.success("提问已复制。"))
      .catch(() => toast.error("复制失败，请稍后重试。"));
  };

  const copyReply = () => {
    void navigator.clipboard
      .writeText(REPLY_TEXT)
      .then(() => toast.success("回复内容已复制。"))
      .catch(() => toast.error("复制失败，请稍后重试。"));
  };

  return (
    <MessageScrollerProvider>
      <div className="min-h-0 flex-1">
        <MessageScroller>
          <MessageScrollerViewport>
            <MessageScrollerContent className="mx-auto w-full max-w-3xl px-6 py-6">
              <MessageScrollerItem>
                <Message align="end">
                  <MessageContent>
                    <Bubble align="end" variant="muted">
                      <BubbleContent>{QUESTION_TEXT}</BubbleContent>
                    </Bubble>
                    <MessageFooter>
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        aria-label="复制"
                        onClick={copyQuestion}
                      >
                        <Copy />
                      </Button>
                      <Button variant="ghost" size="icon-xs" aria-label="编辑">
                        <Pencil />
                      </Button>
                    </MessageFooter>
                  </MessageContent>
                </Message>
              </MessageScrollerItem>
              <MessageScrollerItem>
                <Message>
                  <MessageContent>
                    <div className="space-y-4 text-sm leading-7">
                      <h2 className="text-lg font-semibold">飞书机器人接入 GitHub PR 通知</h2>
                      <p>
                        先配置 GitHub Webhook，再把事件转发到飞书机器人。每个 PR
                        只发一条通知，建议在卡片前面加分支节点。
                      </p>
                      <CodeBlock size="sm">
                        <CodeBlockHeader>
                          <CodeBlockTitle>JSON</CodeBlockTitle>
                          <CodeBlockCopyButton value={WEBHOOK_PAYLOAD} />
                        </CodeBlockHeader>
                        <CodeBlockContent>{WEBHOOK_PAYLOAD}</CodeBlockContent>
                      </CodeBlock>
                      <h3 className="pt-2 text-base font-semibold">两个注意点</h3>
                      <ol className="list-decimal space-y-1 ps-5">
                        <li>
                          <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">
                            merged
                          </code>{" "}
                          字段在 payload 里是布尔值，条件分支按字符串匹配可能失效。
                        </li>
                        <li>真正的批准人在 Pull request review 事件里，这个触发器收不到。</li>
                      </ol>
                      <p className="text-muted-foreground">
                        如果平台变量格式不同，以界面上实际显示的变量为准。
                      </p>
                    </div>
                    <MessageFooter>
                      <Button variant="ghost" size="icon-xs" aria-label="复制" onClick={copyReply}>
                        <Copy />
                      </Button>
                      <Button variant="ghost" size="icon-xs" aria-label="朗读">
                        <Volume2 />
                      </Button>
                      <Button variant="ghost" size="icon-xs" aria-label="重新生成">
                        <RotateCcw />
                      </Button>
                      <Button variant="ghost" size="icon-xs" aria-label="分享">
                        <Share2 />
                      </Button>
                      <Button variant="ghost" size="icon-xs" aria-label="有帮助">
                        <ThumbsUp />
                      </Button>
                      <Button variant="ghost" size="icon-xs" aria-label="没有帮助">
                        <ThumbsDown />
                      </Button>
                    </MessageFooter>
                  </MessageContent>
                </Message>
              </MessageScrollerItem>
            </MessageScrollerContent>
          </MessageScrollerViewport>
          <MessageScrollerButton />
        </MessageScroller>
      </div>
    </MessageScrollerProvider>
  );
}
