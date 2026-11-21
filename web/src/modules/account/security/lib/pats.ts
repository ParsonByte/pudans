import {
  type Metadata,
  type PatSpec,
  type PersonalAccessToken,
  type PersonalAccessTokenList,
  apiClient,
} from "@pudans-dev/api-client";

const PAT_ACCESS_TOKEN_ANNOTATION = "security.pudans.tolim.cn/access-token";

export const PAT_LIST_QUERY_KEY = ["account-personal-access-tokens"] as const;

export function patDetailQueryKey(name: string) {
  return ["account-personal-access-token", name] as const;
}

export type CreatedPat = {
  pat: PersonalAccessToken;
  token?: string;
};

type CreatePatInput = {
  name: string;
  description?: string;
  expiresAt: string;
};

type UpdatePatInput = {
  name: string;
  description?: string;
  expiresAt: string;
};

export type PatState = "ACTIVE" | "REVOKED" | "EXPIRED";

export function patDisplayName(pat: PersonalAccessToken): string {
  return pat.spec?.name?.trim() || pat.metadata.name;
}

export function patState(pat: PersonalAccessToken, now = Date.now()): PatState {
  if (pat.spec?.revoked) {
    return "REVOKED";
  }
  if (pat.spec?.expiresAt && new Date(pat.spec.expiresAt).getTime() <= now) {
    return "EXPIRED";
  }
  return "ACTIVE";
}

export async function fetchPatList(): Promise<PersonalAccessToken[]> {
  const response = await apiClient.personalAccessToken.obtainPats();
  const value: unknown = response.data;
  if (Array.isArray(value)) {
    return value as PersonalAccessToken[];
  }
  if (
    value &&
    typeof value === "object" &&
    Array.isArray((value as PersonalAccessTokenList).items)
  ) {
    return (value as PersonalAccessTokenList).items;
  }
  return [];
}

export async function fetchPatDetail(name: string): Promise<PersonalAccessToken> {
  const response = await apiClient.personalAccessToken.obtainPat({ name });
  return response.data;
}

export async function createPat(input: CreatePatInput): Promise<CreatedPat> {
  const response = await apiClient.personalAccessToken.generatePat({
    personalAccessToken: {
      apiVersion: "security.pudans.tolim.cn/v1alpha1",
      kind: "PersonalAccessToken",
      metadata: {} as Metadata,
      spec: {
        name: input.name,
        description: input.description,
        expiresAt: input.expiresAt,
      } as PatSpec,
    },
  });
  return {
    pat: response.data,
    token: response.data.metadata.annotations?.[PAT_ACCESS_TOKEN_ANNOTATION],
  };
}

export async function updatePat(
  pat: PersonalAccessToken,
  input: UpdatePatInput,
): Promise<PersonalAccessToken> {
  if (!pat.spec) {
    throw new Error("个人访问令牌详情缺少 spec。");
  }
  const spec: PatSpec = {
    ...pat.spec,
    name: input.name,
    description: input.description,
    expiresAt: input.expiresAt,
  };
  const response = await apiClient.personalAccessToken.updatePersonalAccessToken({
    name: pat.metadata.name,
    personalAccessToken: { ...pat, spec },
  });
  return response.data;
}

export async function revokePat(name: string): Promise<PersonalAccessToken> {
  const response = await apiClient.personalAccessToken.revokePat({ name });
  return response.data;
}

export async function restorePat(name: string): Promise<PersonalAccessToken> {
  const response = await apiClient.personalAccessToken.restorePat({ name });
  return response.data;
}
