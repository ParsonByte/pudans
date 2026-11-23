"use client";

import { Alert, Typography } from "antd";

type FeishuSyncNoticeProps = {
    description?: string;
};

export function FeishuSyncNotice({ description = "姓名、邮箱、手机号和头像由飞书提供。" }: FeishuSyncNoticeProps) {
    return (
        <Alert
            className="!rounded-xl"
            message={
                <span>
                    <Typography.Text strong>资料由飞书同步</Typography.Text>
                    <Typography.Text type="secondary"> · {description}</Typography.Text>
                </span>
            }
            showIcon
            type="info"
        />
    );
}
