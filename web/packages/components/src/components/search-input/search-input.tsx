import { forwardRef, useCallback, useRef, type KeyboardEvent } from "react";
import { useHotkeys } from "react-hotkeys-hook";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./search-input.module.css";

export interface SearchInputProps {
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  onSearch?: (value: string) => void;
  placeholder?: string;
  /** Hotkey syntax understood by react-hotkeys-hook, e.g. "mod+k". */
  shortcut?: string;
  loading?: boolean;
  disabled?: boolean;
  autoFocus?: boolean;
  className?: string;
  ariaLabel?: string;
}

function isMac(): boolean {
  if (typeof navigator === "undefined") {
    return false;
  }
  return /Mac|iPhone|iPad/.test(navigator.platform);
}

function formatShortcut(shortcut: string): string {
  return shortcut
    .split("+")
    .map((part) => {
      const lower = part.toLowerCase();
      if (lower === "mod") return isMac() ? "⌘" : "Ctrl";
      if (lower === "shift") return "⇧";
      if (lower === "alt") return isMac() ? "⌥" : "Alt";
      return part.toUpperCase();
    })
    .join(isMac() ? "" : "+");
}

/**
 * Compact search field migrated from the legacy NavBar SearchBar. Controlled or
 * uncontrolled, exposes an optional global hotkey and a clear affordance.
 */
export const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(function SearchInput(
  {
    value,
    defaultValue,
    onChange,
    onSearch,
    placeholder = "Search",
    shortcut,
    loading = false,
    disabled = false,
    autoFocus = false,
    className,
    ariaLabel = "Search",
  },
  ref,
) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const setRefs = useCallback(
    (node: HTMLInputElement | null) => {
      inputRef.current = node;
      if (typeof ref === "function") {
        ref(node);
      } else if (ref) {
        ref.current = node;
      }
    },
    [ref],
  );

  useHotkeys(
    shortcut ?? "",
    (event) => {
      event.preventDefault();
      inputRef.current?.focus();
      inputRef.current?.select();
    },
    { enabled: Boolean(shortcut) && !disabled, enableOnFormTags: true },
    [shortcut, disabled],
  );

  const currentValue = value ?? undefined;

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === "Enter") {
      onSearch?.((event.target as HTMLInputElement).value);
    }
    if (event.key === "Escape") {
      (event.target as HTMLInputElement).blur();
    }
  };

  const handleClear = (): void => {
    if (value !== undefined) {
      onChange?.("");
    } else if (inputRef.current) {
      inputRef.current.value = "";
      onChange?.("");
    }
    inputRef.current?.focus();
  };

  return (
    <div className={cn(styles.root, disabled && styles.disabled, className)}>
      <Icon name="cr:Search" size={16} className={styles.icon} />
      <input
        ref={setRefs}
        type="search"
        className={styles.input}
        value={currentValue}
        defaultValue={defaultValue}
        placeholder={placeholder}
        aria-label={ariaLabel}
        autoFocus={autoFocus}
        disabled={disabled}
        onChange={(event) => onChange?.(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      {loading ? <span className={styles.spinner} aria-hidden="true" /> : null}
      {!loading && (value === undefined || value !== "") ? (
        <button
          type="button"
          className={styles.clear}
          onClick={handleClear}
          aria-label="Clear search"
        >
          <Icon name="cr:Dismiss" size={14} />
        </button>
      ) : null}
      {shortcut ? (
        <kbd className={styles.shortcut} aria-hidden="true">
          {formatShortcut(shortcut)}
        </kbd>
      ) : null}
    </div>
  );
});

SearchInput.displayName = "SearchInput";
