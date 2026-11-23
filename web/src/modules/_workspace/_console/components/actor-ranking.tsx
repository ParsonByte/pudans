"use client";

import type { AuditActorStat } from "@pudans-dev/api-client";

import { formatCount, formatDuration } from "../composables/_format";
import { RankedBarList } from "./ranked-bar-list";

type ActorRankingProps = {
    actors?: AuditActorStat[];
    emptyText?: string;
    valueLabel?: string;
};

export function ActorRanking({ actors, emptyText = "暂无活跃用户", valueLabel = "条" }: ActorRankingProps) {
    return (
        <RankedBarList
            emptyText={emptyText}
            items={(actors ?? []).map((actor) => {
                const uid = actor.uid ?? "未知用户";

                return {
                    key: uid,
                    label: <span className="font-mono">{uid}</span>,
                    labelTitle: uid,
                    meta: (
                        <>
                            <span>操作 {formatCount(actor.operations)} 种</span>
                            <span>失败 {formatCount(actor.failures)}</span>
                            <span>平均 {formatDuration(actor.averageDurationMs)}</span>
                        </>
                    ),
                    value: actor.records ?? 0,
                    valueLabel,
                };
            })}
        />
    );
}
