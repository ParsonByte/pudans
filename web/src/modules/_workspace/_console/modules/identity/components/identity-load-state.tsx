"use client";

import type { ReactElement, ReactNode } from "react";

import { Card, Empty } from "tdesign-react";

type IdentityLoadStateProps = {
    action?: ReactElement;
    description?: ReactNode;
    title: ReactNode;
    type?: "empty" | "fail" | "network-error";
};

export function IdentityLoadState({ action, description, title, type = "fail" }: IdentityLoadStateProps) {
    return (
        <Card>
            <div className="flex min-h-[420px] items-center justify-center">
                <Empty action={action} description={description} title={title} type={type} />
            </div>
        </Card>
    );
}
