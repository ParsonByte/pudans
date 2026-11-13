import {
  AuditLogV1alpha1Api,
  AuthV1alpha1AuthApi,
  LoginApi,
  SettingV1alpha1Api,
  UserV1alpha1Api,
} from "../api";
import { Configuration } from "../configuration";
import { AuthApiClient } from "./auth-api";
import { ConsoleApiClient } from "./console-api";
import { resolveApiClientConfig, type ApiClientConfig } from "./config";
import { createHttpClient } from "./http";
import { redirectToForbidden, redirectToLogin } from "./navigation";
import { RecoveryApi } from "./recovery-api";
import { SystemApi } from "./system-api";
import { UcApiClient } from "./uc-api";
import type { HttpClient } from "./types";

/**
 * Facade over the generated OpenAPI API classes and the auth/console/uc domain
 * clients. Every request goes through the one Axios instance created here.
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

  readonly authApi: AuthApiClient;

  readonly consoleApi: ConsoleApiClient;

  readonly ucApi: UcApiClient;

  constructor(config: ApiClientConfig = {}) {
    this.http = createHttpClient(
      resolveApiClientConfig({
        onUnauthorized: redirectToLogin,
        onForbidden: redirectToForbidden,
        ...config,
      }),
    );
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
    this.authApi = new AuthApiClient(this.http);
    this.consoleApi = new ConsoleApiClient(this.http, configuration);
    this.ucApi = new UcApiClient(this.http, configuration);
  }
}

export function createApiClient(config: ApiClientConfig = {}): ApiClient {
  return new ApiClient(config);
}

/** Same-origin client used by the setup, auth, console and uc applications. */
export const apiClient = createApiClient();

/** Domain clients consumed by pages and layouts. */
export const authApiClient = apiClient.authApi;
export const consoleApiClient = apiClient.consoleApi;
export const ucApiClient = apiClient.ucApi;

export * from "./auth-api";
export * from "./config";
export * from "./console-api";
export * from "./errors";
export * from "./http";
export * from "./navigation";
export * from "./openapi";
export * from "./recovery-api";
export * from "./system-api";
export * from "./types";
export * from "./uc-api";
