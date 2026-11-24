"use client";

import { useSearchParams } from "next/navigation";

import { Empty } from "tdesign-react";

import { INDEX_SESSION_PARAM } from "@/routes/(_workspace)/(_index)/paths";

export function ConversationView() {
    const searchParams = useSearchParams();
    const sessionIds = searchParams.getAll(INDEX_SESSION_PARAM);

    return (
        <div className="flex min-h-[calc(100dvh-112px)] min-w-0 flex-col max-md:min-h-[calc(100dvh-96px)]">
            {sessionIds.length > 0 ? (
                <div
                    aria-label="已打开的会话"
                    className="flex min-h-12 items-center gap-2 overflow-x-auto border-b border-[var(--td-component-stroke)] px-4"
                    role="tablist"
                >
                    {sessionIds.map((sessionId, index) => (
                        <span
                            aria-selected={index === 0}
                            className={`shrink-0 rounded-[var(--td-radius-default)] px-3 py-1.5 text-sm ${
                                index === 0
                                    ? "bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]"
                                    : "bg-[var(--td-bg-color-secondarycontainer)] text-[var(--td-text-color-secondary)]"
                            }`}
                            key={`${sessionId}-${index}`}
                            role="tab"
                        >
                            会话 {index + 1}
                        </span>
                    ))}
                </div>
            ) : null}
            <div className="flex min-h-0 flex-1 items-center justify-center">
                <Empty description={sessionIds.length > 0 ? "会话内容建设中" : "暂无可打开的会话"} />
            </div>
        </div>
    );
}
