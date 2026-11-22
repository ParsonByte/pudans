import type { ReactNode } from "react";

import { Flex, Typography } from "antd";

type AuthFormCardProps = {
    eyebrow: string;
    title: string;
    description: ReactNode;
    children: ReactNode;
};

export function AuthFormCard({ eyebrow, title, description, children }: AuthFormCardProps) {
    return (
        <Flex vertical gap={20}>
            <div>
                <Typography.Text className="text-xs font-semibold tracking-[0.2em] uppercase" type="secondary">
                    {eyebrow}
                </Typography.Text>
                <Typography.Title className="!mt-2 !mb-1" level={2}>
                    {title}
                </Typography.Title>
                <Typography.Paragraph className="!mb-0" type="secondary">
                    {description}
                </Typography.Paragraph>
            </div>
            {children}
        </Flex>
    );
}
