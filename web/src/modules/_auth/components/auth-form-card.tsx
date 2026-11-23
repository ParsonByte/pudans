import type { ReactNode } from "react";

import { Typography } from "tdesign-react";

type AuthFormCardProps = {
    children: ReactNode;
    description: ReactNode;
    eyebrow: string;
    title: string;
};

export function AuthFormCard({ children, description, eyebrow, title }: AuthFormCardProps) {
    return (
        <div className="flex flex-col gap-5">
            <div>
                <Typography.Text className="text-xs font-semibold tracking-[0.2em] uppercase" theme="secondary">
                    {eyebrow}
                </Typography.Text>
                <Typography.Title className="!mt-2 !mb-1 !text-[30px] !leading-[1.35]" level="h2">
                    {title}
                </Typography.Title>
                <Typography.Paragraph className="!mb-0 !text-sm !text-[var(--td-text-color-secondary)]">
                    {description}
                </Typography.Paragraph>
            </div>
            {children}
        </div>
    );
}
