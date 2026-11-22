import axios, { isAxiosError } from "axios";

import { encryptLoginPassword } from "./_auth-login-crypto";
import { getPasskeyAssertion, serializeAssertion } from "./_auth-webauthn";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

type LoginStatus = {
    setupRequired?: boolean;
    rsaEncryptionEnabled?: boolean;
};

type LoginSession = {
    authenticated: boolean;
    consoleAccessDisallowed?: boolean;
    redirectUrl?: string;
};

type PublicKeyResponse = {
    base64Format?: string;
};

type PasskeyOptionsResponse = {
    publicKey?: Record<string, unknown>;
};

type PasskeyResult = {
    authenticated?: boolean;
    twoFactorRequired?: boolean;
};

type LoginProvider = {
    name?: string;
    displayName?: string;
    logo?: string;
    authenticationUrl?: string;
    helpPage?: string;
};

type ProviderWithUrl = LoginProvider & { authenticationUrl: string };

class LoginError extends Error {}

function readCookie(name: string): string | undefined {
    if (typeof document === "undefined") {
        return undefined;
    }

    const prefix = `${name}=`;
    const entry = document.cookie.split("; ").find((item) => item.startsWith(prefix));

    return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}

async function getLoginStatus(): Promise<LoginStatus> {
    const { data } = await axios.get<LoginStatus>(`${API_PREFIX}/login/status`, { withCredentials: true });

    return data;
}

async function getLoginSession(): Promise<LoginSession> {
    const { data } = await axios.get<LoginSession>(`${API_PREFIX}/login/session`, { withCredentials: true });

    return data;
}

async function getLoginPublicKey(): Promise<string> {
    const { data } = await axios.get<PublicKeyResponse>(`${API_PREFIX}/login/public-key`, {
        withCredentials: true,
    });

    if (!data.base64Format) {
        throw new LoginError("登录公钥缺失，请稍后重试。");
    }

    return data.base64Format;
}

export async function getLoginProviders(): Promise<LoginProvider[]> {
    const { data } = await axios.get<LoginProvider[]>(`${API_PREFIX}/login/providers`, {
        withCredentials: true,
    });

    return data;
}

export async function signIn(username: string, password: string): Promise<LoginSession> {
    const status = await getLoginStatus();

    if (status.setupRequired) {
        throw new LoginError("系统尚未完成初始化，请先完成设置。");
    }

    const submittedPassword =
        status.rsaEncryptionEnabled === false
            ? password
            : await encryptLoginPassword(password, await getLoginPublicKey());
    const csrfToken = readCookie("XSRF-TOKEN");
    const response = await axios.post<{ twoFactorRequired?: boolean }>(
        "/login",
        new URLSearchParams({ username, password: submittedPassword }),
        {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                ...(csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {}),
            },
            withCredentials: true,
        },
    );

    if (response.data?.twoFactorRequired) {
        throw new LoginError("该账号需要完成两步验证。");
    }

    const session = await getLoginSession();

    if (!session.authenticated) {
        throw new LoginError("用户名或密码错误。");
    }

    return session;
}

async function getPasskeyOptions(username?: string): Promise<PasskeyOptionsResponse> {
    try {
        const { data } = await axios.post<PasskeyOptionsResponse>(
            `${API_PREFIX}/passkeys/assertion/options`,
            username ? { username } : {},
            { withCredentials: true },
        );

        return data;
    } catch (error) {
        if (isAxiosError(error) && error.response?.status === 403) {
            throw new LoginError("通行密钥登录未启用。");
        }

        throw error;
    }
}

export async function signInWithPasskey(username?: string): Promise<LoginSession> {
    const options = await getPasskeyOptions(username);
    const publicKey = options.publicKey;

    if (!publicKey) {
        throw new LoginError("通行密钥选项缺失，请重试。");
    }

    let credential: PublicKeyCredential;

    try {
        credential = await getPasskeyAssertion(publicKey);
    } catch (error) {
        if (error instanceof DOMException && error.name === "NotAllowedError") {
            throw new LoginError("已取消通行密钥登录。");
        }

        throw new LoginError("通行密钥登录失败，请重试。");
    }

    const result = await axios.post<PasskeyResult>(
        `${API_PREFIX}/passkeys/assertion/verify`,
        serializeAssertion(credential),
        {
            headers: { "Content-Type": "application/json" },
            withCredentials: true,
        },
    );

    if (result.data.twoFactorRequired) {
        throw new LoginError("该账号需要完成两步验证。");
    }

    if (!result.data.authenticated) {
        throw new LoginError("通行密钥登录失败，请重试。");
    }

    const session = await getLoginSession();

    if (!session.authenticated) {
        throw new LoginError("登录状态未生效，请重试。");
    }

    return session;
}

export function providerWithUrl(provider: LoginProvider): provider is ProviderWithUrl {
    return Boolean(provider.authenticationUrl);
}

export function signInErrorMessage(error: unknown): string {
    if (error instanceof LoginError) {
        return error.message;
    }

    if (isAxiosError(error)) {
        if (!error.response) {
            return "无法连接登录服务，请稍后重试。";
        }

        if (error.response.status === 401) {
            return "用户名或密码错误。";
        }

        if (error.response.status === 403) {
            return "登录请求被拒绝，请刷新页面后重试。";
        }

        if (error.response.status === 429) {
            return "尝试次数过多，请稍后重试。";
        }
    }

    return "登录失败，请稍后重试。";
}
