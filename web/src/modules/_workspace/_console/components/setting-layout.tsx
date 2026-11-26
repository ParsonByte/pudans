"use client";

import type { ReactNode } from "react";

import { SectionCard } from "@/components/ui";

type SettingLayoutProps = {
    children: ReactNode;
    footer?: ReactNode;
    title: string;
};

export function SettingLayout({ children, footer, title }: SettingLayoutProps) {
    return (
        <SectionCard bodyClassName="!flex flex-1 flex-col" className="flex min-h-[600px] w-full flex-col">
            <div className="flex flex-1 flex-col pb-6">
                <h1 className="m-0 mb-6 text-[18px] leading-[1.8] font-semibold text-[var(--td-text-color-primary)]">
                    {title}
                </h1>
                {children}
            </div>
            {footer ? (
                <div className="flex justify-end border-t border-[var(--td-component-stroke)] pt-4">{footer}</div>
            ) : null}
        </SectionCard>
    );
}
