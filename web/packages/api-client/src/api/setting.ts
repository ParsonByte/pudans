import { http } from "../../entry/api-client";
import type { ListSettingsParams, Setting, SettingList } from "../models/setting";

const UC_API_BASE = "/apis/uc.api.pudans.tolim.cn/v1alpha1";

/** GET /uc/settings */
export function listSettings(params: ListSettingsParams = {}): Promise<SettingList> {
  return http
    .get<SettingList>(`${UC_API_BASE}/settings`, { params })
    .then((response) => response.data);
}

/** GET /uc/settings/{name} */
export function getSetting(name: string): Promise<Setting> {
  return http
    .get<Setting>(`${UC_API_BASE}/settings/${encodeURIComponent(name)}`)
    .then((response) => response.data);
}

/** PUT /uc/settings/{name} */
export function updateSetting(name: string, setting: Setting): Promise<Setting> {
  return http
    .put<Setting>(`${UC_API_BASE}/settings/${encodeURIComponent(name)}`, setting)
    .then((response) => response.data);
}
