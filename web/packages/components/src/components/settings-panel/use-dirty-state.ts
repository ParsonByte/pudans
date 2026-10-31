import { useEffect, useMemo, useRef } from "react";

export function isEqualValue(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) {
    return true;
  }
  if (
    typeof left !== "object" ||
    typeof right !== "object" ||
    left === null ||
    right === null
  ) {
    return false;
  }
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) {
      return false;
    }
    return left.every((item, index) => isEqualValue(item, right[index]));
  }
  const leftRecord = left as Record<string, unknown>;
  const rightRecord = right as Record<string, unknown>;
  const leftKeys = Object.keys(leftRecord);
  const rightKeys = Object.keys(rightRecord);
  if (leftKeys.length !== rightKeys.length) {
    return false;
  }
  return leftKeys.every(
    (key) =>
      Object.prototype.hasOwnProperty.call(rightRecord, key) &&
      isEqualValue(leftRecord[key], rightRecord[key]),
  );
}

export interface UseDirtyStateOptions {
  /** Controlled dirty flag. Omit to compare initialValues with values. */
  dirty?: boolean;
  /** Notified whenever the resolved dirty flag changes. */
  onDirtyChange?: (dirty: boolean) => void;
}

/**
 * App-agnostic dirty-state primitive. Compares two immutable snapshots and
 * notifies the host when the resolved state changes. Use it directly in any
 * form that needs save/reset affordances without adopting SettingsPanel.
 */
export function useDirtyState<T>(
  initialValues: T,
  values: T,
  options: UseDirtyStateOptions = {},
): boolean {
  const compared = useMemo(
    () => !isEqualValue(initialValues, values),
    [initialValues, values],
  );
  const dirty = options.dirty ?? compared;
  const callbackRef = useRef(options.onDirtyChange);
  callbackRef.current = options.onDirtyChange;

  useEffect(() => {
    callbackRef.current?.(dirty);
  }, [dirty]);

  return dirty;
}
