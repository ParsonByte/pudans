import type { AxiosResponse } from "axios";

type ListResponse<TItem> = {
  items: TItem[];
  hasNext?: boolean;
};

/**
 * Query all items from a generated list function.
 *
 * Mirrors the reference entry/utils/paginate helper so infinite pagination is shared
 * by every resource instead of being reimplemented per page.
 */
export async function paginate<TParams extends { page?: number }, TItem>(
  listFn: (params: TParams) => Promise<AxiosResponse<ListResponse<TItem>>>,
  params?: Omit<TParams, "page">,
): Promise<TItem[]> {
  const result: TItem[] = [];
  let page = 1;
  let hasNext = true;

  while (hasNext) {
    const { data } = await listFn({ ...params, page } as TParams);
    result.push(...data.items);
    page += 1;
    hasNext = data.hasNext ?? data.items.length > 0;
  }

  return result;
}
