import type { Metadata } from "next";

import { ExternalPortalPage } from "@/modules/_workspace/_index/modules/dream-space/pages/external-portal-page";

export const metadata: Metadata = {
    title: "外部门户 - 幻境空间",
};

type ExternalPortalRouteProps = {
    params: Promise<{ portalId: string }>;
};

export default async function ExternalPortalRoute({ params }: ExternalPortalRouteProps) {
    const { portalId } = await params;

    return <ExternalPortalPage portalId={decodeURIComponent(portalId)} />;
}
