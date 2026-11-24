"use client";

import { ExternalPageRoute } from "../components/external/external-page-route";

export function ExternalPortalPage({ portalId }: { portalId: string }) {
    return <ExternalPageRoute portalId={portalId} />;
}
