/** POST /system/setup */
export interface SetupPayload {
  username: string;
  password: string;
  email?: string;
  siteTitle: string;
  siteUrl: string;
  language?: string;
}
