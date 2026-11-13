import type {
  Setting,
  SettingForm,
  SettingList,
  SettingRef,
  SettingSpec,
} from "./generated";

export type { Setting, SettingForm, SettingList, SettingRef, SettingSpec };

export interface ListSettingsParams {
  page?: number;
  size?: number;
  labelSelector?: string[];
  fieldSelector?: string[];
  sort?: string[];
}
