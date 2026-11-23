"use client";

import { Typography } from "antd";

type EmptyValueProps = {
    text?: string;
};

export function EmptyValue({ text = "未填写" }: EmptyValueProps) {
    return <Typography.Text type="secondary">{text}</Typography.Text>;
}
