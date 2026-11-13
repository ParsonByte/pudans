import { http } from "../../entry/api-client";
import type {
  ListConnectionsParams,
  OidcProviderVo,
  UserConnection,
  UserConnectionList,
} from "../models/connection";

const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

/** GET /userconnections */
export function listMyConnections(params: ListConnectionsParams = {}): Promise<UserConnectionList> {
  return http
    .get<UserConnectionList>(`${AUTH_API_BASE}/userconnections`, { params })
    .then((response) => response.data);
}

/** DELETE /userconnections/{name} */
export function unbindConnection(name: string): Promise<void> {
  return http
    .delete<void>(`${AUTH_API_BASE}/userconnections/${encodeURIComponent(name)}`)
    .then(() => undefined);
}

/** GET /userconnections/oidc/providers */
export function listOidcProviders(): Promise<OidcProviderVo[]> {
  return http
    .get<OidcProviderVo[]>(`${AUTH_API_BASE}/userconnections/oidc/providers`)
    .then((response) => response.data);
}

/** GET /userconnections/oidc/{registrationId}/authorize-url */
export function getOidcAuthorizeUrl(registrationId: string, redirectUri: string): Promise<UserConnection> {
  return http
    .get<UserConnection>(
      `${AUTH_API_BASE}/userconnections/oidc/${encodeURIComponent(registrationId)}/authorize-url`,
      { params: { redirectUri } },
    )
    .then((response) => response.data);
}
