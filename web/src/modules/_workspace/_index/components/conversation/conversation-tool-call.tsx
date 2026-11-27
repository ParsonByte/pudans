"use client";

import { useState } from "react";

import type { ToolCallContent } from "@tdesign-react/chat";
import { ChevronDown, Wrench } from "lucide-react";
import { Button, Tag } from "tdesign-react";

type ConversationToolCallProps = {
    content: ToolCallContent;
    onApproval?: (approvalId: string, toolCallId: string, decision: "approve" | "reject") => Promise<void>;
};

const STATE_LABELS: Record<string, string> = {
    approved: "已批准",
    complete: "已完成",
    denied: "已拒绝",
    failed: "执行失败",
    pending: "等待审批",
    rejected: "已拒绝",
    started: "执行中",
    succeeded: "已完成",
};

type ToolCallExt = {
    approvalId?: string;
    state?: string;
};

export function ConversationToolCall({ content, onApproval }: ConversationToolCallProps) {
    const [deciding, setDeciding] = useState<"approve" | "reject" | null>(null);
    const ext = (content.ext ?? {}) as ToolCallExt;
    const state = ext.state ?? content.data.result ?? "complete";
    const approvalId = ext.approvalId ?? "";
    const pending = state === "pending" && approvalId && onApproval;
    const tagTheme =
        state === "denied" || state === "failed" || state === "rejected"
            ? "danger"
            : state === "pending"
              ? "warning"
              : "success";

    const decide = async (decision: "approve" | "reject") => {
        if (!pending) {
            return;
        }

        setDeciding(decision);
        try {
            await onApproval(approvalId, content.data.toolCallId, decision);
        } finally {
            setDeciding(null);
        }
    };

    return (
        <details className="group w-full rounded-[var(--td-radius-medium)] border border-[var(--td-component-stroke)] bg-[var(--td-bg-color-secondarycontainer)]">
            <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2.5 text-sm text-[var(--td-text-color-primary)]">
                <Wrench size={15} strokeWidth={1.8} />
                <span className="min-w-0 flex-1 truncate font-medium">{content.data.toolCallName || "工具调用"}</span>
                <Tag size="small" theme={tagTheme} variant="light">
                    {STATE_LABELS[state] ?? state}
                </Tag>
                {pending ? (
                    <span className="flex items-center gap-1">
                        <Button
                            loading={deciding === "approve"}
                            onClick={(event) => {
                                event.preventDefault();
                                event.stopPropagation();
                                void decide("approve");
                            }}
                            size="small"
                            theme="primary"
                        >
                            批准
                        </Button>
                        <Button
                            loading={deciding === "reject"}
                            onClick={(event) => {
                                event.preventDefault();
                                event.stopPropagation();
                                void decide("reject");
                            }}
                            size="small"
                            theme="danger"
                            variant="outline"
                        >
                            拒绝
                        </Button>
                    </span>
                ) : null}
                <ChevronDown className="transition-transform group-open:rotate-180" size={15} strokeWidth={1.8} />
            </summary>
            <div className="border-t border-[var(--td-component-stroke)] px-3 py-2 text-xs text-[var(--td-text-color-secondary)]">
                <div className="font-mono">{content.data.toolCallId}</div>
                {content.data.args ? (
                    <pre className="mt-2 max-h-48 overflow-auto rounded-[var(--td-radius-default)] bg-[var(--td-bg-color-container)] p-2 font-mono break-all whitespace-pre-wrap">
                        {content.data.args}
                    </pre>
                ) : null}
                {content.data.result ? (
                    <div className="mt-2">
                        结果：
                        <span className="ml-1 font-medium text-[var(--td-text-color-primary)]">
                            {content.data.result}
                        </span>
                    </div>
                ) : null}
            </div>
        </details>
    );
}
