import type { HttpClientOptions } from "./types";

export const DEFAULT_API_BASE_URL = "";

export interface ApiClientConfig extends HttpClientOptions {
  baseURL?: string;
}

export function resolveApiClientConfig(config: ApiClientConfig = {}): HttpClientOptions {
  return {
    baseURL: DEFAULT_API_BASE_URL,
    withCredentials: true,
    ...config,
  };
}
