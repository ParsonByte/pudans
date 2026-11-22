import type { ReactNode } from "react";

import { WorkspaceLayout } from "@/modules/_workspace/layout";

type WorkspaceRouteLayoutProps = {
    children: ReactNode;
    params: Promise<{ workspace: string }>;
};

export default async function WorkspaceRouteLayout({ children, params }: WorkspaceRouteLayoutProps) {
    const { workspace } = await params;

    return <WorkspaceLayout workspace={workspace}>{children}</WorkspaceLayout>;
}
