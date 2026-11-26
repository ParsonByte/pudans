import { axiosInstance } from "@pudans-dev/api-client";

import { csrfHeaders } from "@/modules/_auth/composables/_auth-login";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

export type SiteAssetField = "favicon" | "iconLarge" | "iconMedium" | "logoDark" | "logoLight";

// 站点图片接口尚未生成客户端，直接调用后端上传接口。
export async function uploadSiteAsset(field: SiteAssetField, file: File): Promise<string> {
    const formData = new FormData();
    formData.append("field", field);
    formData.append("file", file);
    const response = await axiosInstance.post<{ url?: string }>(
        `${API_ROOT}/parameter-settings/site/-/assets`,
        formData,
        { headers: csrfHeaders() },
    );
    const url = response.data.url?.trim();
    if (!url) {
        throw new Error("上传响应缺少图片地址。");
    }
    return url;
}
