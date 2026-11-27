"use client";

import type { ActivityContent } from "@tdesign-react/chat";
import { PanelRightOpen } from "lucide-react";
import { Button } from "tdesign-react";

type ConversationActivityInlineProps = {
    content: ActivityContent;
    onOpen: () => void;
};

export function ConversationActivityInline({ content, onOpen }: ConversationActivityInlineProps) {
    return (
        <div className="flex w-full items-center gap-3 rounded-[var(--td-radius-medium)] border border-[var(--td-component-stroke)] bg-[var(--td-bg-color-container)] px-3 py-2.5">
            <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-[var(--td-text-color-primary)]">生成式界面</div>
                <div className="mt-0.5 truncate text-xs text-[var(--td-text-color-secondary)]">
                    {content.data.activityType}
                </div>
            </div>
            <Button icon={<PanelRightOpen size={15} />} onClick={onOpen} size="small" variant="outline">
                侧栏查看
            </Button>
        </div>
    );
}
