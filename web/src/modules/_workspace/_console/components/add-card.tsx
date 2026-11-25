"use client";

import type { ReactNode } from "react";

import { AddIcon } from "tdesign-icons-react";
import { Button, Card } from "tdesign-react";

type ConsoleAddCardProps = {
    className?: string;
    icon?: ReactNode;
    onClick: () => void;
    title: ReactNode;
};

export function ConsoleAddCard({ className, icon = <AddIcon />, onClick, title }: ConsoleAddCardProps) {
    return (
        <Button
            block
            className={`!h-full !rounded-[var(--td-radius-extraLarge)] !p-0 text-left ${className ?? ""}`}
            onClick={onClick}
            theme="default"
            type="button"
            variant="text"
        >
            <Card bordered={false} className="h-full min-h-56">
                <span className="flex h-full min-h-48 flex-col items-center justify-center gap-3 text-center">
                    <span className="flex size-12 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-xl text-[var(--td-brand-color)]">
                        {icon}
                    </span>
                    <span className="block text-sm font-semibold">{title}</span>
                </span>
            </Card>
        </Button>
    );
}
