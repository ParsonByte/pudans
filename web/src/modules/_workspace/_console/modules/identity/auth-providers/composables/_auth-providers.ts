"use client";

import type { AuthProvider, ListedAuthProvider } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { useListedAuthProvidersQuery } from "../../composables/_auth-provider-directory";

export { useListedAuthProvidersQuery };

export const AUTH_PROVIDER_PERMISSIONS = {
    manage: "auth-providers:manage",
    view: "auth-providers:view",
} as const;

export const SECRET_MASK = "********";

const AUTHENTICATION_GROUP = "authentication";
const FEISHU_NAME = "feishu";
const FEISHU_DISPLAY_NAME = "飞书";
const LOCAL_PROVIDER_NAME = "local";
const AUTH_PROVIDER_RECORDS_QUERY_KEY = ["console", "auth-providers", "records"] as const;
const AUTHENTICATION_SETTINGS_QUERY_KEY = ["console", "parameter-settings", AUTHENTICATION_GROUP] as const;

export type IdentityProviderKind = "feishu" | "oauth2" | "oidc";

type IdentityProviderSource = "auth-provider" | "feishu" | "oauth2-settings" | "oidc-settings";

export type OAuth2ProviderSettings = {
    authorizationUri?: string;
    clientId?: string;
    clientSecret?: string;
    displayName?: string;
    enabled?: boolean;
    logo?: string;
    registrationId?: string;
    scopes?: string;
    tokenUri?: string;
    userInfoUri?: string;
    userNameAttribute?: string;
};

export type OidcProviderSettings = OAuth2ProviderSettings & {
    issuerUri?: string;
};

export type FeishuProviderSettings = {
    alertChatId?: string;
    appId?: string;
    appSecret?: string;
    authorizationUri?: string;
    enabled?: boolean;
    redirectUri?: string;
    scope?: string;
    syncIntervalSeconds?: number;
};

export type AuthenticationSettings = {
    feishu?: FeishuProviderSettings;
    oauth2?: {
        providers?: OAuth2ProviderSettings[];
    };
    oidc?: {
        providers?: OidcProviderSettings[];
    };
};

export type IdentityProviderCard = {
    authProvider?: AuthProvider;
    configured: boolean;
    description?: string;
    displayName: string;
    enabled: boolean;
    endpoint?: string;
    environmentManaged: boolean;
    key: string;
    kind: IdentityProviderKind;
    logo?: string;
    name: string;
    source: IdentityProviderSource;
    settingsProvider?: FeishuProviderSettings | OAuth2ProviderSettings | OidcProviderSettings;
};

export type ProviderEditorValues = {
    appId?: string;
    appSecret?: string;
    authorizationUri?: string;
    clientId?: string;
    clientSecret?: string;
    displayName?: string;
    enabled?: boolean;
    issuerUri?: string;
    name?: string;
    redirectUri?: string;
    scopes?: string;
    tokenUri?: string;
    userInfoUri?: string;
    userNameAttribute?: string;
};

function asAuthenticationSettings(value: unknown): AuthenticationSettings {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
        return {};
    }

    return value as AuthenticationSettings;
}

function normalizedName(value?: string): string {
    return value?.trim().toLowerCase() ?? "";
}

function textValue(value?: string): string | undefined {
    const normalized = value?.trim();

    return normalized ? normalized : undefined;
}

function optionalTextValue(value?: string): string {
    return value?.trim() ?? "";
}

function configuredOAuth2Provider(provider: OAuth2ProviderSettings): boolean {
    return Boolean(
        textValue(provider.registrationId) &&
        textValue(provider.clientId) &&
        textValue(provider.authorizationUri) &&
        textValue(provider.tokenUri) &&
        textValue(provider.userInfoUri),
    );
}

function configuredOidcProvider(provider: OidcProviderSettings): boolean {
    return configuredOAuth2Provider(provider);
}

function hasStoredFeishuSettings(settings?: FeishuProviderSettings): boolean {
    return Boolean(
        settings &&
        (textValue(settings.appId) ||
            textValue(settings.appSecret) ||
            textValue(settings.redirectUri) ||
            textValue(settings.authorizationUri) ||
            textValue(settings.scope) ||
            textValue(settings.alertChatId) ||
            settings.syncIntervalSeconds !== undefined ||
            settings.enabled !== undefined),
    );
}

function hasStoredFeishuCredentials(settings?: FeishuProviderSettings): boolean {
    return Boolean(textValue(settings?.appId) && textValue(settings?.appSecret));
}

export function buildIdentityProviderCards(
    authProviders: AuthProvider[],
    listedProviders: ListedAuthProvider[],
    authentication: AuthenticationSettings,
): IdentityProviderCard[] {
    const rawProviders = new Map(
        authProviders
            .filter((provider) => !provider.metadata.deletionTimestamp)
            .map((provider) => [normalizedName(provider.metadata.name), provider]),
    );
    const listedProvidersByName = new Map(listedProviders.map((provider) => [normalizedName(provider.name), provider]));
    const cards: IdentityProviderCard[] = [];
    const settingsNames = new Set<string>();

    for (const provider of authentication.oauth2?.providers ?? []) {
        const name = textValue(provider.registrationId);
        if (!name || normalizedName(name) === LOCAL_PROVIDER_NAME) {
            continue;
        }

        const normalized = normalizedName(name);
        const rawProvider = rawProviders.get(normalized);
        const configured = configuredOAuth2Provider(provider);
        settingsNames.add(normalized);
        cards.push({
            authProvider: rawProvider,
            configured,
            description: textValue(rawProvider?.spec.description) ?? "标准 OAuth2 授权码登录",
            displayName: textValue(provider.displayName) ?? textValue(rawProvider?.spec.displayName) ?? name,
            enabled: configured && provider.enabled !== false,
            endpoint: textValue(provider.authorizationUri),
            environmentManaged: false,
            key: `oauth2:${name}`,
            kind: "oauth2",
            logo: textValue(provider.logo) ?? textValue(rawProvider?.spec.logo),
            name,
            settingsProvider: provider,
            source: "oauth2-settings",
        });
    }

    for (const provider of authentication.oidc?.providers ?? []) {
        const name = textValue(provider.registrationId);
        if (!name || normalizedName(name) === LOCAL_PROVIDER_NAME) {
            continue;
        }

        const normalized = normalizedName(name);
        const rawProvider = rawProviders.get(normalized);
        const configured = configuredOidcProvider(provider);
        settingsNames.add(normalized);
        cards.push({
            authProvider: rawProvider,
            configured,
            description: textValue(rawProvider?.spec.description) ?? "OpenID Connect 身份认证",
            displayName: textValue(provider.displayName) ?? textValue(rawProvider?.spec.displayName) ?? name,
            enabled: configured && provider.enabled !== false,
            endpoint: textValue(provider.authorizationUri),
            environmentManaged: false,
            key: `oidc:${name}`,
            kind: "oidc",
            logo: textValue(provider.logo) ?? textValue(rawProvider?.spec.logo),
            name,
            settingsProvider: provider,
            source: "oidc-settings",
        });
    }

    const rawFeishu = rawProviders.get(FEISHU_NAME);
    const listedFeishu = listedProvidersByName.get(FEISHU_NAME);
    const storedFeishu = hasStoredFeishuSettings(authentication.feishu);
    const storedFeishuCredentials = hasStoredFeishuCredentials(authentication.feishu);
    const environmentManaged = !storedFeishuCredentials && listedFeishu?.enabled === true;
    const configured = storedFeishuCredentials || environmentManaged;

    if (storedFeishu || environmentManaged) {
        settingsNames.add(FEISHU_NAME);
        cards.push({
            authProvider: rawFeishu,
            configured,
            description: environmentManaged ? "由运行环境配置的飞书企业应用登录" : "使用飞书企业应用完成第三方登录",
            displayName: FEISHU_DISPLAY_NAME,
            enabled: configured && (authentication.feishu?.enabled ?? listedFeishu?.enabled === true),
            endpoint: textValue(authentication.feishu?.redirectUri),
            environmentManaged,
            key: `feishu:${FEISHU_NAME}`,
            kind: "feishu",
            logo: textValue(rawFeishu?.spec.logo),
            name: FEISHU_NAME,
            settingsProvider: authentication.feishu,
            source: "feishu",
        });
    }

    for (const provider of rawProviders.values()) {
        const name = textValue(provider.metadata.name);
        if (!name || normalizedName(name) === LOCAL_PROVIDER_NAME || provider.spec.authType === "FORM") {
            continue;
        }

        const normalized = normalizedName(name);
        if (normalized === FEISHU_NAME || settingsNames.has(normalized)) {
            continue;
        }

        const configured = Boolean(textValue(provider.spec.authenticationUrl));
        cards.push({
            authProvider: provider,
            configured,
            description: textValue(provider.spec.description) ?? "自定义第三方登录入口",
            displayName: textValue(provider.spec.displayName) ?? name,
            enabled: configured && listedProvidersByName.get(normalized)?.enabled === true,
            endpoint: textValue(provider.spec.authenticationUrl),
            environmentManaged: false,
            key: `auth-provider:${name}`,
            kind: "oauth2",
            logo: textValue(provider.spec.logo),
            name,
            source: "auth-provider",
        });
    }

    return cards.sort((left, right) => {
        const kindOrder = identityProviderKindOrder(left.kind) - identityProviderKindOrder(right.kind);
        if (kindOrder !== 0) {
            return kindOrder;
        }

        return (
            left.displayName.localeCompare(right.displayName, "zh-Hans-CN", { sensitivity: "base" }) ||
            left.name.localeCompare(right.name)
        );
    });
}

function identityProviderKindOrder(kind: IdentityProviderKind): number {
    switch (kind) {
        case "feishu":
            return 0;
        case "oauth2":
            return 1;
        case "oidc":
            return 2;
    }
}

export function providerNames(authentication: AuthenticationSettings, currentName?: string): Set<string> {
    return new Set(
        [
            ...(authentication.oauth2?.providers ?? []).map((provider) => provider.registrationId),
            ...(authentication.oidc?.providers ?? []).map((provider) => provider.registrationId),
            FEISHU_NAME,
            LOCAL_PROVIDER_NAME,
        ]
            .map(normalizedName)
            .filter((name) => name && name !== normalizedName(currentName)),
    );
}

function secretValue(value: string | undefined, previous?: string): string | undefined {
    return value ?? previous;
}

export function saveOAuth2Provider(
    authentication: AuthenticationSettings,
    originalName: string | undefined,
    values: ProviderEditorValues,
): AuthenticationSettings {
    const providers = [...(authentication.oauth2?.providers ?? [])];
    const index = providers.findIndex((provider) => provider.registrationId === originalName);
    const previous = index >= 0 ? providers[index] : undefined;
    const provider: OAuth2ProviderSettings = {
        ...previous,
        authorizationUri: textValue(values.authorizationUri),
        clientId: textValue(values.clientId),
        clientSecret: secretValue(values.clientSecret, previous?.clientSecret),
        displayName: textValue(values.displayName),
        enabled: values.enabled !== false,
        registrationId: textValue(values.name),
        scopes: optionalTextValue(values.scopes),
        tokenUri: textValue(values.tokenUri),
        userInfoUri: textValue(values.userInfoUri),
        userNameAttribute: optionalTextValue(values.userNameAttribute),
    };

    if (index >= 0) {
        providers[index] = provider;
    } else {
        providers.push(provider);
    }

    return {
        ...authentication,
        oauth2: {
            ...authentication.oauth2,
            providers,
        },
    };
}

export function saveOidcProvider(
    authentication: AuthenticationSettings,
    originalName: string | undefined,
    values: ProviderEditorValues,
): AuthenticationSettings {
    const providers = [...(authentication.oidc?.providers ?? [])];
    const index = providers.findIndex((provider) => provider.registrationId === originalName);
    const previous = index >= 0 ? providers[index] : undefined;
    const provider: OidcProviderSettings = {
        ...previous,
        authorizationUri: textValue(values.authorizationUri),
        clientId: textValue(values.clientId),
        clientSecret: secretValue(values.clientSecret, previous?.clientSecret),
        displayName: textValue(values.displayName),
        enabled: values.enabled !== false,
        issuerUri: optionalTextValue(values.issuerUri),
        registrationId: textValue(values.name),
        scopes: optionalTextValue(values.scopes),
        tokenUri: textValue(values.tokenUri),
        userInfoUri: textValue(values.userInfoUri),
        userNameAttribute: optionalTextValue(values.userNameAttribute),
    };

    if (index >= 0) {
        providers[index] = provider;
    } else {
        providers.push(provider);
    }

    return {
        ...authentication,
        oidc: {
            ...authentication.oidc,
            providers,
        },
    };
}

export function saveFeishuProvider(
    authentication: AuthenticationSettings,
    values: ProviderEditorValues,
): AuthenticationSettings {
    const previous = authentication.feishu;

    return {
        ...authentication,
        feishu: {
            ...previous,
            appId: textValue(values.appId),
            appSecret: secretValue(values.appSecret, previous?.appSecret),
            enabled: values.enabled !== false,
            redirectUri: optionalTextValue(values.redirectUri),
            scope: optionalTextValue(values.scopes),
        },
    };
}

export function setProviderEnabled(
    authentication: AuthenticationSettings,
    provider: IdentityProviderCard,
    enabled: boolean,
): AuthenticationSettings {
    if (provider.source === "feishu") {
        return {
            ...authentication,
            feishu: {
                ...authentication.feishu,
                enabled,
            },
        };
    }

    if (provider.source === "oauth2-settings") {
        return {
            ...authentication,
            oauth2: {
                ...authentication.oauth2,
                providers: (authentication.oauth2?.providers ?? []).map((item) =>
                    item.registrationId === provider.name ? { ...item, enabled } : item,
                ),
            },
        };
    }

    if (provider.source === "oidc-settings") {
        return {
            ...authentication,
            oidc: {
                ...authentication.oidc,
                providers: (authentication.oidc?.providers ?? []).map((item) =>
                    item.registrationId === provider.name ? { ...item, enabled } : item,
                ),
            },
        };
    }

    return authentication;
}

export function removeProviderFromSettings(
    authentication: AuthenticationSettings,
    provider: IdentityProviderCard,
): AuthenticationSettings {
    if (provider.source === "oauth2-settings") {
        return {
            ...authentication,
            oauth2: {
                ...authentication.oauth2,
                providers: (authentication.oauth2?.providers ?? []).filter(
                    (item) => item.registrationId !== provider.name,
                ),
            },
        };
    }

    if (provider.source === "oidc-settings") {
        return {
            ...authentication,
            oidc: {
                ...authentication.oidc,
                providers: (authentication.oidc?.providers ?? []).filter(
                    (item) => item.registrationId !== provider.name,
                ),
            },
        };
    }

    return authentication;
}

export function useAuthProviderRecordsQuery() {
    return useQuery({
        queryKey: AUTH_PROVIDER_RECORDS_QUERY_KEY,
        queryFn: async () => (await apiClient.authProvider.listAuthProvider({ page: 1, size: 1000 })).data.items,
    });
}

export function useAuthenticationSettingsQuery() {
    return useQuery({
        queryKey: AUTHENTICATION_SETTINGS_QUERY_KEY,
        queryFn: async () => {
            const response = await apiClient.parameterSetting.getParameterSettingByGroup({
                group: AUTHENTICATION_GROUP,
            });

            return asAuthenticationSettings(response.data);
        },
    });
}

function invalidateAuthProviderData(queryClient: QueryClient) {
    void queryClient.invalidateQueries({ queryKey: ["console", "auth-providers"] });
    void queryClient.invalidateQueries({ queryKey: AUTHENTICATION_SETTINGS_QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: ["login-providers"] });
}

export function useUpdateAuthenticationSettings() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ authentication }: { authentication: AuthenticationSettings; successMessage: string }) => {
            await apiClient.parameterSetting.updateParameterSettingByGroup({
                body: { ...authentication },
                group: AUTHENTICATION_GROUP,
            });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (_data, variables) => {
            prompt.success(variables.successMessage);
            invalidateAuthProviderData(queryClient);
        },
    });
}

export function useUpdateAuthProvider() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (authProvider: AuthProvider) =>
            (
                await apiClient.authProvider.updateAuthProvider({
                    authProvider,
                    name: authProvider.metadata.name,
                })
            ).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("身份提供商已保存。");
            invalidateAuthProviderData(queryClient);
        },
    });
}

export function useToggleAuthProvider() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ enabled, name }: { enabled: boolean; name: string }) => {
            if (enabled) {
                await apiClient.authProvider.enableAuthProvider({ name });
                return;
            }

            await apiClient.authProvider.disableAuthProvider({ name });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (_data, variables) => {
            prompt.success(variables.enabled ? "身份提供商已启用。" : "身份提供商已停用。");
            invalidateAuthProviderData(queryClient);
        },
    });
}

export function useDeleteAuthProvider() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (name: string) => apiClient.authProvider.deleteAuthProvider({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("身份提供商已删除。");
            invalidateAuthProviderData(queryClient);
        },
    });
}
