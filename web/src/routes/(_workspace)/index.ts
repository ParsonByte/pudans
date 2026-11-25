import type { ComponentType } from "react";

import { AuthProvidersRoute } from "./(_console)/identity/auth-providers/route";
import { PermissionsRoute } from "./(_console)/identity/permissions/route";
import { RolesRoute } from "./(_console)/identity/roles/route";
import { UserGroupsRoute } from "./(_console)/identity/user-groups/route";
import { UsersRoute } from "./(_console)/identity/users/route";
import { LiveRecordingsRoute } from "./(_console)/live/live-recordings/route";
import { LiveSourcesRoute } from "./(_console)/live/live-sources/route";
import { DashboardRoute } from "./(_console)/overview/dashboard/route";
import { AuditRoute } from "./(_console)/security/audit/route";
import { ReplayRoute } from "./(_console)/security/replay/route";
import { RiskRoute } from "./(_console)/security/risk/route";
import { SecretsRoute } from "./(_console)/security/secrets/route";
import { TracesRoute } from "./(_console)/security/traces/route";
import { StorageFilesRoute } from "./(_console)/storage/storage-files/route";
import { StoragePoliciesRoute } from "./(_console)/storage/storage-policies/route";
import { SettingsRoute } from "./(_console)/system/settings/route";
import { WebhooksRoute } from "./(_console)/system/webhooks/route";
import { IdentitiesRoute } from "./(_uc)/account/identities/route";
import { ProfileRoute } from "./(_uc)/account/profile/route";
import { SecurityRoute } from "./(_uc)/account/security/route";

type WorkspaceViews = Record<string, Record<string, ComponentType>>;

export const WORKSPACE_VIEWS: Record<string, WorkspaceViews> = {
    uc: {
        account: {
            profile: ProfileRoute,
            security: SecurityRoute,
            identities: IdentitiesRoute,
        },
    },
    console: {
        overview: {
            dashboard: DashboardRoute,
        },
        identity: {
            users: UsersRoute,
            roles: RolesRoute,
            "user-groups": UserGroupsRoute,
            permissions: PermissionsRoute,
            "auth-providers": AuthProvidersRoute,
        },
        security: {
            risk: RiskRoute,
            audit: AuditRoute,
            traces: TracesRoute,
            replay: ReplayRoute,
            secrets: SecretsRoute,
        },
        storage: {
            "storage-policies": StoragePoliciesRoute,
            "storage-files": StorageFilesRoute,
        },
        live: {
            "live-sources": LiveSourcesRoute,
            "live-recordings": LiveRecordingsRoute,
        },
        system: {
            settings: SettingsRoute,
            webhooks: WebhooksRoute,
        },
    },
};
