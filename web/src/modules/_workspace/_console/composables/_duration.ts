export const DURATION_BUCKET_ORDER = ["under50", "50to199", "200to499", "500to999", "1to3s", "over3s"];

const DURATION_BUCKET_LABELS: Record<string, string> = {
    "1to3s": "1–3 s",
    "200to499": "200–499 ms",
    "50to199": "50–199 ms",
    "500to999": "0.5–1 s",
    over3s: "> 3 s",
    under50: "< 50 ms",
};

const DURATION_BUCKET_TICK_LABELS: Record<string, string> = {
    "1to3s": "1-3s",
    "200to499": "200-499",
    "50to199": "50-199",
    "500to999": "0.5-1s",
    over3s: ">3s",
    under50: "<50",
};

export function durationBucketLabel(bucket?: string): string {
    return bucket ? (DURATION_BUCKET_LABELS[bucket] ?? bucket) : "未知区间";
}

export function durationBucketTickLabel(bucket?: string): string {
    return bucket ? (DURATION_BUCKET_TICK_LABELS[bucket] ?? bucket) : "";
}
