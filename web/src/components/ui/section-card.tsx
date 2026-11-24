"use client";

import type { ReactNode } from "react";

import { Card } from "tdesign-react";

type SectionCardProps = {
    bodyClassName?: string;
    children: ReactNode;
    className?: string;
    extra?: ReactNode;
    title: ReactNode;
};

export function SectionCard({ bodyClassName, children, className, extra, title }: SectionCardProps) {
    return (
        <Card
            actions={extra}
            bordered={false}
            bodyClassName={bodyClassName}
            className={`h-full ${className ?? ""}`}
            title={title}
        >
            {children}
        </Card>
    );
}
