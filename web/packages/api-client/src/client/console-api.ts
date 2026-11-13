import {
  AuditLogV1alpha1Api,
  RoleV1alpha1Api,
  SettingV1alpha1Api,
  SystemConfigV1alpha1ConsoleApi,
  UserV1alpha1Api,
} from "../api";
import type { Configuration } from "../configuration";
import type { HttpClient } from "./types";

/** Console domain facade. Pages import this object, never the Axios instance. */
export class ConsoleApiClient {
  readonly user: UserV1alpha1Api;
  readonly role: RoleV1alpha1Api;
  readonly audit: AuditLogV1alpha1Api;
  readonly setting: SettingV1alpha1Api;
  readonly systemConfig: SystemConfigV1alpha1ConsoleApi;

  constructor(http: HttpClient, configuration: Configuration) {
    this.user = new UserV1alpha1Api(configuration, "", http.instance);
    this.role = new RoleV1alpha1Api(configuration, "", http.instance);
    this.audit = new AuditLogV1alpha1Api(configuration, "", http.instance);
    this.setting = new SettingV1alpha1Api(configuration, "", http.instance);
    this.systemConfig = new SystemConfigV1alpha1ConsoleApi(configuration, "", http.instance);
  }
}
