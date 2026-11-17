import axios, { type AxiosInstance } from "axios";
import QueryString from "qs";

import {
  AnnotationSettingV1alpha1Api,
  AuditLogV1alpha1Api,
  AuthProviderV1alpha1Api,
  AuthTokenV1alpha1Api,
  AuthenticationV1alpha1Api,
  AvatarV1alpha1Api,
  CaptchaV1alpha1Api,
  ConfigMapV1alpha1Api,
  DashboardV1alpha1Api,
  DeviceV1alpha1Api,
  LoginV1alpha1Api,
  ParameterSettingV1alpha1Api,
  PasskeyV1alpha1Api,
  PasswordRecoveryV1alpha1Api,
  PersonalAccessTokenV1alpha1Api,
  RememberMeTokenV1alpha1Api,
  RiskRuleV1alpha1Api,
  RoleBindingV1alpha1Api,
  RoleV1alpha1Api,
  SecretV1alpha1Api,
  SettingV1alpha1Api,
  SystemSetupV1alpha1Api,
  TempRoleBindingV1alpha1Api,
  TwoFactorAuthV1alpha1Api,
  UserConnectionV1alpha1Api,
  UserGroupBindingV1alpha1Api,
  UserGroupV1alpha1Api,
  UserPreferenceV1alpha1Api,
  UserV1alpha1Api,
  WebhookV1alpha1Api,
} from "../src";

const defaultAxiosInstance = axios.create({
  baseURL: "",
  withCredentials: true,
  paramsSerializer: (params) => QueryString.stringify(params, { arrayFormat: "repeat" }),
});

defaultAxiosInstance.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";

export function createApiClient(axiosInstance: AxiosInstance = defaultAxiosInstance) {
  const baseURL = axiosInstance.defaults.baseURL ?? "";

  return {
    annotationSetting: new AnnotationSettingV1alpha1Api(undefined, baseURL, axiosInstance),
    auditLog: new AuditLogV1alpha1Api(undefined, baseURL, axiosInstance),
    authProvider: new AuthProviderV1alpha1Api(undefined, baseURL, axiosInstance),
    authToken: new AuthTokenV1alpha1Api(undefined, baseURL, axiosInstance),
    authentication: new AuthenticationV1alpha1Api(undefined, baseURL, axiosInstance),
    avatar: new AvatarV1alpha1Api(undefined, baseURL, axiosInstance),
    captcha: new CaptchaV1alpha1Api(undefined, baseURL, axiosInstance),
    configMap: new ConfigMapV1alpha1Api(undefined, baseURL, axiosInstance),
    dashboard: new DashboardV1alpha1Api(undefined, baseURL, axiosInstance),
    device: new DeviceV1alpha1Api(undefined, baseURL, axiosInstance),
    login: new LoginV1alpha1Api(undefined, baseURL, axiosInstance),
    parameterSetting: new ParameterSettingV1alpha1Api(undefined, baseURL, axiosInstance),
    passkey: new PasskeyV1alpha1Api(undefined, baseURL, axiosInstance),
    passwordRecovery: new PasswordRecoveryV1alpha1Api(undefined, baseURL, axiosInstance),
    personalAccessToken: new PersonalAccessTokenV1alpha1Api(undefined, baseURL, axiosInstance),
    rememberMeToken: new RememberMeTokenV1alpha1Api(undefined, baseURL, axiosInstance),
    riskRule: new RiskRuleV1alpha1Api(undefined, baseURL, axiosInstance),
    roleBinding: new RoleBindingV1alpha1Api(undefined, baseURL, axiosInstance),
    role: new RoleV1alpha1Api(undefined, baseURL, axiosInstance),
    secret: new SecretV1alpha1Api(undefined, baseURL, axiosInstance),
    setting: new SettingV1alpha1Api(undefined, baseURL, axiosInstance),
    systemSetup: new SystemSetupV1alpha1Api(undefined, baseURL, axiosInstance),
    tempRoleBinding: new TempRoleBindingV1alpha1Api(undefined, baseURL, axiosInstance),
    twoFactorAuth: new TwoFactorAuthV1alpha1Api(undefined, baseURL, axiosInstance),
    userConnection: new UserConnectionV1alpha1Api(undefined, baseURL, axiosInstance),
    userGroupBinding: new UserGroupBindingV1alpha1Api(undefined, baseURL, axiosInstance),
    userGroup: new UserGroupV1alpha1Api(undefined, baseURL, axiosInstance),
    userPreference: new UserPreferenceV1alpha1Api(undefined, baseURL, axiosInstance),
    user: new UserV1alpha1Api(undefined, baseURL, axiosInstance),
    webhook: new WebhookV1alpha1Api(undefined, baseURL, axiosInstance),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;

export const apiClient = createApiClient();

export { defaultAxiosInstance as axiosInstance };
