import type {
  OidcAuthorizeUrlVo,
  OidcBindRequest,
  OidcProviderVo,
  UserConnection,
  UserConnectionList,
  UserConnectionSpec,
} from "./generated";

export type {
  OidcAuthorizeUrlVo,
  OidcBindRequest,
  OidcProviderVo,
  UserConnection,
  UserConnectionList,
  UserConnectionSpec,
};

export interface ListConnectionsParams {
  page?: number;
  size?: number;
  labelSelector?: string[];
  fieldSelector?: string[];
  sort?: string[];
}
