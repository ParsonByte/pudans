"use client";

import { Search, X } from "lucide-react";

import { setDreamQuery } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function MemorySearch() {
    const query = useDreamStore((state) => state.filters.query);

    return (
        <div className="relative">
            <Search
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-white/28"
                size={12}
            />
            <input
                aria-label="搜索记忆"
                className={`${styles.searchField} pr-7 pl-7`}
                onChange={(event) => setDreamQuery(event.target.value)}
                placeholder="搜索标题、摘要或标签"
                type="search"
                value={query}
            />
            {query ? (
                <button
                    aria-label="清除搜索"
                    className="absolute top-1/2 right-1.5 -translate-y-1/2 rounded p-1 text-white/30 hover:text-white"
                    onClick={() => setDreamQuery("")}
                    type="button"
                >
                    <X aria-hidden size={11} />
                </button>
            ) : null}
        </div>
    );
}
