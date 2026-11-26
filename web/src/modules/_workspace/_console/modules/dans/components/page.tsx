"use client";

import type { ReactNode } from "react";

import { Alert } from "tdesign-react";

import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { type ConsoleOverviewMetric, ConsolePageOverview } from "../../../components/page-overview";

type DansPageProps = {
    actions?: ReactNode;
    children: ReactNode;
    description?: string;
    icon: ReactNode;
    metrics?: ConsoleOverviewMetric[];
    scopeNote?: string;
    section: string;
    showOverview?: boolean;
    title: string;
};

export function DansPage({
    actions,
    children,
    description,
    icon,
    metrics = [],
    scopeNote,
    section,
    showOverview = true,
    title,
}: DansPageProps) {
    return (
        <div className="flex min-w-0 flex-col gap-4 overflow-x-hidden">
            <PageBreadcrumb current={title} parents={["DANS 管控", section]} />
            {showOverview ? (
                <ConsolePageOverview actions={actions} icon={icon} metrics={metrics} title={title} />
            ) : null}
            {description ? <p className="m-0 text-sm text-[var(--td-text-color-secondary)]">{description}</p> : null}
            {scopeNote ? <Alert message={scopeNote} theme="warning" /> : null}
            {children}
        </div>
    );
}
