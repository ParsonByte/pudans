import {
  AuditLogV1alpha1Api,
  AuthV1alpha1AuthApi,
  LoginApi,
  SettingV1alpha1Api,
  UserV1alpha1Api,
} from "../api";
import { Configuration } from "../configuration";
import { resolveApiClientConfig, type ApiClientConfig } from "./config";
import { createHttpClient } from "./http";
import { RecoveryApi } from "./recovery-api";
import { SystemApi } from "./system-api";
import type { HttpClient } from "./types";

/**
 * Facade over the generated OpenAPI API classes. The generated methods keep
 * their AxiosPromise return type; all requests still flow through the shared
 * Axios instance so auth, request ids, retries and normalized errors apply.
 */
export class ApiClient {
  readonly http: HttpClient;

  readonly user: UserV1alpha1Api;

  readonly setting: SettingV1alpha1Api;

  readonly audit: AuditLogV1alpha1Api;

  readonly auth: AuthV1alpha1AuthApi;

  readonly login: LoginApi;

  readonly system: SystemApi;

  readonly recovery: RecoveryApi;

  constructor(config: ApiClientConfig = {}) {
    this.http = createHttpClient(resolveApiClientConfig(config));
    const configuration = new Configuration({ basePath: "" });
    this.user = new UserV1alpha1Api(configuration, "", this.http.instance);
    this.setting = new SettingV1alpha1Api(
      configuration,
      "",
      this.http.instance,
    );
    this.audit = new AuditLogV1alpha1Api(
      configuration,
      "",
      this.http.instance,
    );
    this.auth = new AuthV1alpha1AuthApi(
      configuration,
      "",
      this.http.instance,
    );
    this.login = new LoginApi(configuration, "", this.http.instance);
    this.system = new SystemApi(this.http);
    this.recovery = new RecoveryApi(this.http);
  }
}

export function createApiClient(config: ApiClientConfig = {}): ApiClient {
  return new ApiClient(config);
}

/** Same-origin client used by the setup, auth, console and uc applications. */
export const apiClient = createApiClient();

export * from "./config";
export * from "./errors";
export * from "./http";
export * from "./openapi";
export * from "./recovery-api";
export * from "./system-api";
export * from "./types";