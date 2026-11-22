import { isAxiosError } from "axios";

type ProblemDetail = {
    detail?: string;
    message?: string;
};

export function consoleErrorMessage(error: unknown, fallback = "操作失败，请稍后重试。"): string {
    if (isAxiosError<ProblemDetail>(error)) {
        if (!error.response) {
            return "无法连接服务器，请稍后重试。";
        }

        return error.response.data?.detail ?? error.response.data?.message ?? `请求失败（${error.response.status}）。`;
    }

    return fallback;
}
