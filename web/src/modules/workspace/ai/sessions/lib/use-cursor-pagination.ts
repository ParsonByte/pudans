import { useState } from "react";

type CursorPagination = {
  page: number;
  pageSize: number;
  cursor: number | undefined;
  setPageSize: (size: number) => void;
  goNext: (lastId: number | undefined) => void;
  goPrevious: () => void;
};

export function useCursorPagination(initialPageSize: number): CursorPagination {
  const [pageSize, setPageSizeState] = useState(initialPageSize);
  const [cursors, setCursors] = useState<Array<number | undefined>>([undefined]);
  const [page, setPage] = useState(0);
  const cursor = cursors[page];

  const setPageSize = (size: number) => {
    setPageSizeState(size);
    setCursors([undefined]);
    setPage(0);
  };

  const goNext = (lastId: number | undefined) => {
    if (lastId == null) {
      return;
    }
    setCursors((current) => [...current.slice(0, page + 1), lastId]);
    setPage(page + 1);
  };

  const goPrevious = () => {
    setPage((current) => Math.max(0, current - 1));
  };

  return { page, pageSize, cursor, setPageSize, goNext, goPrevious };
}
