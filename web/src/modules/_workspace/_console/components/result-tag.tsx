import { Tag } from "antd";

type ResultTagProps = {
    result?: string;
};

export function ResultTag({ result }: ResultTagProps) {
    const color = result === "SUCCESS" ? "green" : result === "FAILURE" ? "red" : "default";

    return <Tag color={color}>{result ?? "—"}</Tag>;
}
