"use client";

import { Typography } from "tdesign-react";

type EmptyValueProps = {
    text?: string;
};

export function EmptyValue({ text = "未填写" }: EmptyValueProps) {
    return <Typography.Text theme="secondary">{text}</Typography.Text>;
}
