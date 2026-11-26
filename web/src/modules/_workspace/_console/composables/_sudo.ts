import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

const SUDO_ROOT = "/sudo";
const SUDO_REQUIRED_TYPE = "https://pudans.tolim.cn/probs/sudo-required";

export type SudoMethod = {
    canSendCode: boolean;
    maskedTarget?: string | null;
    name: string;
};

export type SudoStatus = {
    active: boolean;
    expiresAt?: string | null;
    methods: SudoMethod[];
};

export const SUDO_METHOD_LABELS: Record<string, { hint: string; label: string; placeholder: string }> = {
    totp: {
        hint: "输入身份验证器中的 6 位动态口令。",
        label: "动态口令",
        placeholder: "6 位动态口令",
    },
    password: {
        hint: "输入当前账户的登录密码。",
        label: "当前密码",
        placeholder: "当前密码",
    },
};

// 用户取消安全确认时向上抛出，调用方据此跳过错误提示。
export class SudoCancelledError extends Error {
    constructor() {
        super("已取消安全确认。");
        this.name = "SudoCancelledError";
    }
}

export function sudoMethodLabel(method: string): string {
    return SUDO_METHOD_LABELS[method]?.label ?? method;
}

export async function getSudoStatus(): Promise<SudoStatus> {
    return (await axiosInstance.get<SudoStatus>(SUDO_ROOT)).data;
}

export async function sendSudoCode(method: string): Promise<void> {
    await axiosInstance.post(`${SUDO_ROOT}/code`, new URLSearchParams({ method }).toString(), {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
}

export async function confirmSudo(method: string, code: string): Promise<void> {
    await axiosInstance.post(`${SUDO_ROOT}/confirm`, new URLSearchParams({ method, code }).toString(), {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
}

// 从 403 问题详情中提取服务端要求的安全确认方式。
function sudoRequiredMethods(error: unknown): string[] | undefined {
    if (!isAxiosError(error) || error.response?.status !== 403) {
        return undefined;
    }

    const data = error.response.data as { methods?: unknown; type?: string } | undefined;
    if (data?.type !== SUDO_REQUIRED_TYPE) {
        return undefined;
    }

    return Array.isArray(data.methods)
        ? data.methods.filter((method): method is string => typeof method === "string")
        : [];
}

// 遇到安全确认要求时先完成确认，再重试原操作。
export async function withSudoRetry<T>(
    action: () => Promise<T>,
    confirm: (methods: string[]) => Promise<boolean>,
): Promise<T> {
    try {
        return await action();
    } catch (error) {
        const methods = sudoRequiredMethods(error);
        if (!methods) {
            throw error;
        }

        const confirmed = await confirm(methods);
        if (!confirmed) {
            throw new SudoCancelledError();
        }

        return action();
    }
}
