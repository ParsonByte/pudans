"use client";

import type { BatchCreateFailure } from "@pudans-dev/api-client";
import { Alert } from "tdesign-react";

type BatchCreateResultProps = {
    created?: number;
    failed?: number;
    failures?: BatchCreateFailure[];
};

export function BatchCreateResult({ created = 0, failed = 0, failures = [] }: BatchCreateResultProps) {
    const visibleFailures = failures.slice(0, 6);

    return (
        <Alert
            message={
                visibleFailures.length > 0 ? (
                    <div className="grid gap-1 text-xs">
                        {visibleFailures.map((failure) => (
                            <div key={`${failure.index}-${failure.providerUserId ?? "unknown"}`}>
                                第 {failure.index} 条{failure.providerUserId ? `（${failure.providerUserId}）` : ""}：
                                {failure.message ?? "创建失败。"}
                            </div>
                        ))}
                        {failures.length > visibleFailures.length ? (
                            <div>另有 {failures.length - visibleFailures.length} 条失败记录。</div>
                        ) : null}
                    </div>
                ) : undefined
            }
            title={`创建完成：成功 ${created} 个，失败 ${failed} 个。`}
            theme={failed > 0 ? "warning" : "success"}
        />
    );
}
