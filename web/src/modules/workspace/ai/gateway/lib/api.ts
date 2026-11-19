import { axiosInstance } from "@pudans-dev/api-client";

import { AI_API_PREFIX } from "../../providers/lib/api";

const GATEWAY_TOKEN_ROLE = "role-template-ai-gateway";

const GATEWAY_TOKEN_LIFETIME_DAYS = 90;

const ACCESS_TOKEN_ANNOTATION = "security.pudans.tolim.cn/access-token";

type PersonalAccessTokenResponse = {
  metadata: {
    name: string;
    annotations?: Record<string, string>;
  };
  spec: {
    expiresAt?: string;
  };
};

export type GatewayToken = {
  token: string;
  name: string;
  expiresAt: string;
};

export async function createGatewayToken(): Promise<GatewayToken> {
  const expiresAt = new Date(
    Date.now() + GATEWAY_TOKEN_LIFETIME_DAYS * 24 * 60 * 60 * 1000,
  ).toISOString();
  const response = await axiosInstance.post<PersonalAccessTokenResponse>(
    `${AI_API_PREFIX}/personalaccesstokens`,
    {
      apiVersion: "security.pudans.tolim.cn/v1alpha1",
      kind: "PersonalAccessToken",
      metadata: {},
      spec: {
        name: "AI 网关密钥",
        description: "由 AI 网关页面创建",
        roles: [GATEWAY_TOKEN_ROLE],
        scopeMode: "RolesOnly",
        expiresAt,
      },
    },
  );
  const token = response.data.metadata.annotations?.[ACCESS_TOKEN_ANNOTATION];
  if (!token) {
    throw new Error("网关密钥已创建，但响应中没有返回密钥内容。");
  }
  return {
    token,
    name: response.data.metadata.name,
    expiresAt: response.data.spec.expiresAt ?? expiresAt,
  };
}
