import { type SetupRequest, apiClient } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

type SetupErrorResult = {
    message: string;
    setupCompleted: boolean;
};

type SetupProblemDetail = {
    errors?: string[];
    detail?: string;
};

export async function submitSetup(input: SetupRequest): Promise<void> {
    await apiClient.systemSetup.setupSystem({ setupRequest: input }, { timeout: 60_000 });
}

export function setupErrorMessage(error: unknown): SetupErrorResult {
    if (isAxiosError<SetupProblemDetail>(error)) {
        if (error.response?.status === 409) {
            return { message: "系统已完成初始化，请直接登录。", setupCompleted: true };
        }

        if (error.response?.status === 400) {
            const firstError = error.response.data?.errors?.find((item) => item.trim());

            return {
                message: firstError ?? error.response.data?.detail ?? "请检查填写内容后重试。",
                setupCompleted: false,
            };
        }

        if (error.code === "ECONNABORTED") {
            return { message: "初始化超时，请稍后重试。", setupCompleted: false };
        }

        if (!error.response) {
            return { message: "无法连接服务器，请稍后重试。", setupCompleted: false };
        }

        if (error.response.status >= 500) {
            return { message: "服务器暂时不可用，请稍后重试。", setupCompleted: false };
        }
    }

    return { message: "初始化失败，请稍后重试。", setupCompleted: false };
}
