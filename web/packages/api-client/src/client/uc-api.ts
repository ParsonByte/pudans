import {
  DeviceV1alpha1UcApi,
  PasskeyV1alpha1UcApi,
  TwoFactorAuthV1alpha1UcApi,
  UserConnectionV1alpha1Api,
  UserV1alpha1UcApi,
} from "../api";
import type { Configuration } from "../configuration";
import type { HttpClient } from "./types";

/** User-centre domain facade. Pages import this object, never the Axios instance. */
export class UcApiClient {
  readonly user: UserV1alpha1UcApi;
  readonly device: DeviceV1alpha1UcApi;
  readonly passkey: PasskeyV1alpha1UcApi;
  readonly twoFactor: TwoFactorAuthV1alpha1UcApi;
  readonly connection: UserConnectionV1alpha1Api;

  constructor(http: HttpClient, configuration: Configuration) {
    this.user = new UserV1alpha1UcApi(configuration, "", http.instance);
    this.device = new DeviceV1alpha1UcApi(configuration, "", http.instance);
    this.passkey = new PasskeyV1alpha1UcApi(configuration, "", http.instance);
    this.twoFactor = new TwoFactorAuthV1alpha1UcApi(configuration, "", http.instance);
    this.connection = new UserConnectionV1alpha1Api(configuration, "", http.instance);
  }
}
