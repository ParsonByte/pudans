"use client";

import { type ReactNode, Suspense } from "react";

import { PageContainer } from "@ant-design/pro-components";
import { Skeleton } from "antd";

import { useMobile } from "@/hooks/ui/use-mobile";

type AppContainerProps = {
    children: ReactNode;
    title?: string;
};

export function AppContainer({ children, title }: AppContainerProps) {
    const isMobile = useMobile();

    return (
        <PageContainer
            className="app-page-container"
            title={title}
            token={{
                paddingBlockPageContainerContent: isMobile ? 20 : 32,
                paddingInlinePageContainerContent: isMobile ? 16 : 40,
            }}
        >
            <Suspense fallback={<Skeleton active />}>{children}</Suspense>
        </PageContainer>
    );
}
