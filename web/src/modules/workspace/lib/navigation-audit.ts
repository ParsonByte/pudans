import { axiosInstance } from "@pudans-dev/api-client";

import { setNavigationAuditContext } from "@/lib/api-interceptor";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

type NavigationResult = {
  auditId?: string;
  traceFlowId?: string;
};

export async function recordNavigation(path: string): Promise<void> {
  try {
    const response = await axiosInstance.post<NavigationResult>(`${API_PREFIX}/usernavigations`, {
      path,
    });
    const { auditId, traceFlowId } = response.data;
    if (auditId && traceFlowId) {
      setNavigationAuditContext({ auditId, traceFlowId });
    }
  } catch {
    // 页面访问审计失败不应阻断前端导航。
  }
}
