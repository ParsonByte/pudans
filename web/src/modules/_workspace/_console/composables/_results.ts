import { RESULT_LABELS } from "@/components/ui";

export const RESULT_ORDER = ["SUCCESS", "FAILURE", "DENIED"];

export const RESULT_OPTIONS = RESULT_ORDER.map((result) => ({
    label: RESULT_LABELS[result],
    value: result,
}));

export function resultLabel(result?: string): string {
    return result ? (RESULT_LABELS[result] ?? result) : "未知结果";
}
