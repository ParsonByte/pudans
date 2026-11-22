"use client";

import type { ReactNode } from "react";

import { Card, theme } from "antd";

type SectionCardProps = {
    bodyClassName?: string;
    children: ReactNode;
    className?: string;
    extra?: ReactNode;
    subtitle?: ReactNode;
    title: ReactNode;
};

export function SectionCard({ bodyClassName, children, className, extra, subtitle, title }: SectionCardProps) {
    const { token } = theme.useToken();

    return (
        <Card
            className={`h-full !rounded-[20px] sm:!rounded-[28px] ${className ?? ""}`}
            style={{
                background: token.colorBgContainer,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowTertiary,
            }}
            styles={{ body: { padding: 0 } }}
        >
            <div className="flex flex-col gap-3 px-4 pt-4 sm:flex-row sm:items-start sm:justify-between sm:px-5 sm:pt-5">
                <div className="min-w-0">
                    <div className="text-base font-semibold tracking-tight" style={{ color: token.colorText }}>
                        {title}
                    </div>
                    {subtitle ? (
                        <div className="mt-1 text-xs leading-5" style={{ color: token.colorTextSecondary }}>
                            {subtitle}
                        </div>
                    ) : null}
                </div>
                {extra}
            </div>
            <div className={`p-4 sm:p-5 ${bodyClassName ?? ""}`}>{children}</div>
        </Card>
    );
}
