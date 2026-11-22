import { Tag } from "antd";

type ResultTagProps = {
    result?: string;
};

const RESULT_LABELS: Record<string, string> = {
    DENIED: "拒绝",
    FAILED: "失败",
    FAILURE: "失败",
    SUCCESS: "成功",
};

export function ResultTag({ result }: ResultTagProps) {
    const color =
        result === "SUCCESS"
            ? "green"
            : result === "FAILURE" || result === "FAILED"
              ? "red"
              : result === "DENIED"
                ? "gold"
                : "default";

    return <Tag color={color}>{result ? (RESULT_LABELS[result] ?? result) : "—"}</Tag>;
}
