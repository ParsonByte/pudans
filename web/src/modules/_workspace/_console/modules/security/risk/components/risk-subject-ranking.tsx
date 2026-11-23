"use client";

import type { RiskSubjectStat } from "@pudans-dev/api-client";

import { RankedBarList } from "../../../../components/ranked-bar-list";
import { formatCount } from "../../../../composables/_format";

type RiskSubjectRankingProps = {
    subjects?: RiskSubjectStat[];
};

function subjectLabel(subject?: string): string {
    return !subject || subject === "unknown" ? "未知主体" : subject;
}

export function RiskSubjectRanking({ subjects }: RiskSubjectRankingProps) {
    const data = subjects ?? [];

    return (
        <RankedBarList
            emptyText="暂无受影响主体"
            items={data.map((subject) => ({
                key: subject.subject ?? "unknown",
                label: <span className="font-mono">{subjectLabel(subject.subject)}</span>,
                labelTitle: subjectLabel(subject.subject),
                meta: (
                    <>
                        <span>场景 {formatCount(subject.scenes)}</span>
                        <span>阻断 {formatCount(subject.blocked)}</span>
                        <span>告警 {formatCount(subject.alerts)}</span>
                    </>
                ),
                value: subject.events ?? 0,
                valueLabel: "次",
            }))}
        />
    );
}
