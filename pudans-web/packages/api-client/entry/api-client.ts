import axios, { type AxiosInstance } from "axios";
import QueryString from "qs";
import {
  AuthorizationActionApi,
  AuthorizationApi,
  AuthorizationPolicyApi,
  AuthorizationPolicyBindingApi,
  AuthorizationResourceTypeApi,
  AuthorizationRoleApi,
  AuthorizationRoleBindingApi,
  IamApplicationApi,
  IamAuditRecordApi,
  IamAuthenticationApi,
  IamIdentitySourceEventApi,
  IamMfaApi,
  IamPortalApi,
  IamPublicKeyApi,
  IamSessionApi,
  IamSsoApi,
  JobDefinitionApi,
  JobExecutionApi,
  JobExecutorApi,
  MessageCenterApi,
  NotificationProviderApi,
  NotificationSendApi,
  NotificationTemplateApi,
  RepositoryFileApi,
  SearchDocumentApi,
  SearchIndexApi,
  SearchQueryApi,
  StorageFileApi,
  TotpApi,
  WebAuthnApi,
} from "../src";

const defaultAxiosInstance = axios.create({
  baseURL: "",
  withCredentials: true,
  paramsSerializer: (params) => QueryString.stringify(params, { arrayFormat: "repeat" }),
});

function createApiClient(axiosInstance: AxiosInstance) {
  const baseURL = axiosInstance.defaults.baseURL ?? "";

  return {
    iam: {
      application: new IamApplicationApi(undefined, baseURL, axiosInstance),
      auditRecord: new IamAuditRecordApi(undefined, baseURL, axiosInstance),
      authentication: new IamAuthenticationApi(undefined, baseURL, axiosInstance),
      identitySourceEvent: new IamIdentitySourceEventApi(undefined, baseURL, axiosInstance),
      mfa: new IamMfaApi(undefined, baseURL, axiosInstance),
      portal: new IamPortalApi(undefined, baseURL, axiosInstance),
      publicKey: new IamPublicKeyApi(undefined, baseURL, axiosInstance),
      session: new IamSessionApi(undefined, baseURL, axiosInstance),
      sso: new IamSsoApi(undefined, baseURL, axiosInstance),
      storageFile: new StorageFileApi(undefined, baseURL, axiosInstance),
      totp: new TotpApi(undefined, baseURL, axiosInstance),
      webauthn: new WebAuthnApi(undefined, baseURL, axiosInstance),
      authorization: new AuthorizationApi(undefined, baseURL, axiosInstance),
      authorizationAction: new AuthorizationActionApi(undefined, baseURL, axiosInstance),
      authorizationPolicy: new AuthorizationPolicyApi(undefined, baseURL, axiosInstance),
      authorizationPolicyBinding: new AuthorizationPolicyBindingApi(undefined, baseURL, axiosInstance),
      authorizationResourceType: new AuthorizationResourceTypeApi(undefined, baseURL, axiosInstance),
      authorizationRole: new AuthorizationRoleApi(undefined, baseURL, axiosInstance),
      authorizationRoleBinding: new AuthorizationRoleBindingApi(undefined, baseURL, axiosInstance),
    },
    repository: {
      file: new RepositoryFileApi(undefined, baseURL, axiosInstance),
    },
    notification: {
      send: new NotificationSendApi(undefined, baseURL, axiosInstance),
      provider: new NotificationProviderApi(undefined, baseURL, axiosInstance),
      template: new NotificationTemplateApi(undefined, baseURL, axiosInstance),
    },
    mq: {
      messageCenter: new MessageCenterApi(undefined, baseURL, axiosInstance),
    },
    job: {
      definition: new JobDefinitionApi(undefined, baseURL, axiosInstance),
      execution: new JobExecutionApi(undefined, baseURL, axiosInstance),
      executor: new JobExecutorApi(undefined, baseURL, axiosInstance),
    },
    search: {
      index: new SearchIndexApi(undefined, baseURL, axiosInstance),
      document: new SearchDocumentApi(undefined, baseURL, axiosInstance),
      query: new SearchQueryApi(undefined, baseURL, axiosInstance),
    },
  };
}

type ApiClient = ReturnType<typeof createApiClient>;

const apiClient = createApiClient(defaultAxiosInstance);

export { apiClient, createApiClient, defaultAxiosInstance as axiosInstance };
export type { ApiClient };
