"use client";

import { Breadcrumb } from "tdesign-react";

const { BreadcrumbItem } = Breadcrumb;

type PageBreadcrumbProps = {
    current: string;
    parents?: string[];
};

export function PageBreadcrumb({ current, parents = [] }: PageBreadcrumbProps) {
    return (
        <Breadcrumb>
            {parents.map((parent) => (
                <BreadcrumbItem key={parent}>{parent}</BreadcrumbItem>
            ))}
            <BreadcrumbItem>{current}</BreadcrumbItem>
        </Breadcrumb>
    );
}
