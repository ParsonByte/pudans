import { http } from "../../entry/api-client";
import type { DeviceList, ListDevicesParams } from "../models/device";

const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

/** GET /devices */
export function listDevices(params: ListDevicesParams = {}): Promise<DeviceList> {
  return http
    .get<DeviceList>(`${AUTH_API_BASE}/devices`, { params })
    .then((response) => response.data);
}

/** DELETE /devices/{deviceId} */
export function revokeDevice(deviceId: string): Promise<void> {
  return http
    .delete<void>(`${AUTH_API_BASE}/devices/${encodeURIComponent(deviceId)}`)
    .then(() => undefined);
}
