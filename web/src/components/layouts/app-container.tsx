"use client";

import { type ReactNode, Suspense } from "react";

import { PageContainer } from "@ant-design/pro-components";
import { Skeleton } from "antd";

type AppContainerProps = {
    children: ReactNode;
};

export function AppContainer({ children }: AppContainerProps) {
    return (
        <PageContainer>
            <Suspense fallback={<Skeleton active />}>{children}</Suspense>
        </PageContainer>
    );
}
