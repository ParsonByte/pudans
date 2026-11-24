import { SearchIcon } from "tdesign-icons-react";
import { Input } from "tdesign-react";

import styles from "../styles/assets-view.module.css";

type LibraryToolbarProps<T extends string> = {
    filterLabel: string;
    filterOptions: readonly T[];
    filterValue: T;
    keyword: string;
    onFilterChange: (value: T) => void;
    onKeywordChange: (value: string) => void;
    searchPlaceholder: string;
};

export function LibraryToolbar<T extends string>({
    filterLabel,
    filterOptions,
    filterValue,
    keyword,
    onFilterChange,
    onKeywordChange,
    searchPlaceholder,
}: LibraryToolbarProps<T>) {
    return (
        <div className={styles.toolbar}>
            <div aria-label={filterLabel} className={styles.filters} role="group">
                {filterOptions.map((option) => {
                    const active = option === filterValue;

                    return (
                        <button
                            aria-pressed={active}
                            className={`${styles.filter} ${active ? styles.filterActive : ""}`}
                            key={option}
                            onClick={() => onFilterChange(option)}
                            type="button"
                        >
                            {option}
                        </button>
                    );
                })}
            </div>
            <div className={styles.searchBox}>
                <Input
                    clearable
                    onChange={(value) => onKeywordChange(String(value))}
                    placeholder={searchPlaceholder}
                    prefixIcon={<SearchIcon />}
                    value={keyword}
                />
            </div>
        </div>
    );
}
