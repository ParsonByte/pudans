/**
 * Compatibility shim mirroring Halo's entry/patch/thumbnail export. Pudans
 * attachment thumbnail sizes use the same short names as the legacy contract.
 */
export const ThumbnailSpecSize = {
  S: "S",
  M: "M",
  L: "L",
  XL: "XL",
} as const;

export type ThumbnailSpecSize = (typeof ThumbnailSpecSize)[keyof typeof ThumbnailSpecSize];

export type ThumbnailSpec = {
  size?: ThumbnailSpecSize;
};

export function isThumbnailSize(value: string): value is ThumbnailSpecSize {
  return Object.values(ThumbnailSpecSize).includes(value as ThumbnailSpecSize);
}
