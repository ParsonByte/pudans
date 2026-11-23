"use client";

import type { AuditDurationBucket } from "@pudans-dev/api-client";

import { DURATION_BUCKET_ORDER, durationBucketLabel, durationBucketTickLabel } from "../composables/_duration";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";
import { DistributionBarChart, type DistributionBarDatum } from "./distribution-bar-chart";

type DurationBucketChartProps = {
    buckets?: AuditDurationBucket[];
    emptyText: string;
    title: string;
    valueLabel: string;
};

export function DurationBucketChart({ buckets, emptyText, title, valueLabel }: DurationBucketChartProps) {
    const { tone } = useDashboardTheme();
    const counts = new Map((buckets ?? []).map((bucket) => [bucket.bucket ?? "", bucket.records ?? 0]));
    const colors = [
        tone("green").base,
        tone("cyan").base,
        tone("blue").base,
        tone("gold").base,
        tone("orange").base,
        tone("red").base,
    ];
    const data: DistributionBarDatum[] = DURATION_BUCKET_ORDER.map((bucket, index) => ({
        color: colors[index] ?? tone("blue").base,
        key: bucket,
        label: durationBucketLabel(bucket),
        tick: durationBucketTickLabel(bucket),
        value: counts.get(bucket) ?? 0,
    }));

    return <DistributionBarChart data={data} emptyText={emptyText} title={title} valueLabel={valueLabel} />;
}
