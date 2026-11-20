import { useCallback, useState } from "react";

export type UsageCursorPager = {
  cursor: number | undefined;
  page: number;
  hasPrevious: boolean;
  goNext: (nextCursor: number | null) => void;
  goPrevious: () => void;
  reset: () => void;
};

export function useCursorPager(): UsageCursorPager {
  const [cursors, setCursors] = useState<(number | undefined)[]>(() => [undefined]);
  const [index, setIndex] = useState(0);

  const goNext = useCallback(
    (nextCursor: number | null) => {
      if (nextCursor == null) {
        return;
      }
      setCursors((current) => [...current.slice(0, index + 1), nextCursor]);
      setIndex((current) => current + 1);
    },
    [index],
  );

  const goPrevious = useCallback(() => {
    setIndex((current) => Math.max(0, current - 1));
  }, []);

  const reset = useCallback(() => {
    setCursors(() => [undefined]);
    setIndex(0);
  }, []);

  return {
    cursor: cursors[index],
    page: index + 1,
    hasPrevious: index > 0,
    goNext,
    goPrevious,
    reset,
  };
}
