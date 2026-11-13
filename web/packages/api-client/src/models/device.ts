import type { Device, DeviceList, DeviceSpec, DeviceStatus, UserDevice } from "./generated";

export type { Device, DeviceList, DeviceSpec, DeviceStatus, UserDevice };

export interface ListDevicesParams {
  page?: number;
  size?: number;
  labelSelector?: string[];
  fieldSelector?: string[];
  sort?: string[];
}
