"use client";

import { externalPortalHref } from "../../world/external-link-service";
import { EXTERNAL_PORTALS_LIST } from "../../world/portal-registry";

export function A11yPortalList() {
    return (
        <section aria-label="外部门户列表">
            <h3>外部门户</h3>
            <ul>
                {EXTERNAL_PORTALS_LIST.map((portal) => (
                    <li key={portal.id}>
                        <a href={externalPortalHref(portal.id)}>
                            {portal.label}，{portal.host}
                        </a>
                    </li>
                ))}
            </ul>
        </section>
    );
}
