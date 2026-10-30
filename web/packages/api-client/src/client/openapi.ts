import type { HttpClient, HttpMethod, PudansRequestConfig } from "./types";

export interface OpenApiOperation<
  TQuery extends Record<string, unknown> = Record<string, unknown>,
  TBody = unknown,
> {
  /** OpenAPI path template, for example "/apis/console.api.pudans.tolim.cn/v1alpha1/users/{name}". */
  path: string;
  method: HttpMethod;
  pathParams?: Record<string, string | number>;
  query?: TQuery;
  body?: TBody;
}

export interface OpenApiClient {
  request: <
    TResponse,
    TQuery extends Record<string, unknown> = Record<string, unknown>,
    TBody = unknown,
  >(
    operation: OpenApiOperation<TQuery, TBody>,
    config?: PudansRequestConfig<TBody>,
  ) => Promise<TResponse>;
}

function resolvePath(
  path: string,
  pathParams: Record<string, string | number> = {},
): string {
  return path.replace(/\{(\w+)\}/g, (_placeholder, key: string) => {
    const value = pathParams[key];
    if (value === undefined) {
      throw new Error(`Missing OpenAPI path parameter "${key}" for ${path}`);
    }
    return encodeURIComponent(String(value));
  });
}

/**
 * Generic OpenAPI operation executor. Generated clients can describe each
 * operation with path/method/pathParams/query/body while sharing the same
 * Axios interceptors, retry policy and normalized errors.
 */
export function createOpenApiClient(http: HttpClient): OpenApiClient {
  return {
    request: async <
      TResponse,
      TQuery extends Record<string, unknown> = Record<string, unknown>,
      TBody = unknown,
    >(
      operation: OpenApiOperation<TQuery, TBody>,
      config?: PudansRequestConfig<TBody>,
    ): Promise<TResponse> =>
      http.request<TResponse, TBody>({
        ...config,
        url: resolvePath(operation.path, operation.pathParams),
        method: operation.method.toUpperCase(),
        params: operation.query,
        data: operation.body,
      }),
  };
}
