import { forwardRef, useCallback, useRef, type KeyboardEvent } from "react";
import {
  Box,
  CircularProgress,
  IconButton,
  InputAdornment,
  TextField,
  alpha,
  styled,
  useTheme,
} from "@mui/material";
import { useHotkeys } from "react-hotkeys-hook";
import { Icon } from "../icon";

const KeyIndicator = styled("code")(({ theme }) => ({
  backgroundColor: theme.palette.grey[100],
  ...theme.applyStyles("dark", { backgroundColor: theme.palette.grey[900] }),
  border: "var(--pd-border-width-thin) solid " + theme.palette.divider,
  boxShadow:
    "0 " +
    theme.spacing(0.125) +
    " " +
    theme.spacing(0.125) +
    " " +
    alpha(theme.palette.common.black, 0.2) +
    ", 0 " +
    theme.spacing(0.25) +
    " 0 0 " +
    (theme.palette.mode === "light"
      ? alpha(theme.palette.common.white, 0.7)
      : theme.palette.grey[800]) +
    " inset",
  padding: theme.spacing(0, 0.5),
  borderRadius: theme.spacing(0.5),
  fontFamily: theme.typography.fontFamily,
  fontSize: theme.typography.body2.fontSize,
  whiteSpace: "nowrap",
}));

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
 * Search field rebuilt from the legacy Frame/NavBar SearchBar trigger:
 * outlined MUI input with the shape 12px radius and 1px divider border, a
 * primary Search glyph, body2 13/14px copy and the raised KeyIndicator key cap.
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
  const theme = useTheme();
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

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === "Enter") {
      onSearch?.((event.target as HTMLInputElement).value);
    }
    if (event.key === "Escape") {
      (event.target as HTMLInputElement).blur();
    }
  };

  const handleClear = (): void => {
    if (value === undefined && inputRef.current) {
      inputRef.current.value = "";
    }
    onChange?.("");
    inputRef.current?.focus();
  };

  return (
    <TextField
      className={className}
      size="small"
      value={value}
      defaultValue={defaultValue}
      placeholder={placeholder}
      disabled={disabled}
      autoFocus={autoFocus}
      inputRef={setRefs}
      onKeyDown={handleKeyDown}
      onChange={(event) => onChange?.(event.target.value)}
      sx={{
        minWidth: theme.spacing(27.5),
        "& .MuiOutlinedInput-root": {
          height: theme.spacing(5.25),
          paddingLeft: theme.spacing(2),
          paddingRight: theme.spacing(2),
          borderRadius: theme.shape.borderRadius,
          backgroundColor: theme.palette.background.default,
          "& fieldset": { borderColor: theme.palette.divider },
          "&:hover fieldset": { borderColor: theme.palette.primary.main },
          "&.Mui-focused fieldset": { borderColor: theme.palette.primary.main },
        },
        "& .MuiOutlinedInput-input": {
          padding: 0,
          fontSize: theme.typography.body2.fontSize,
          lineHeight: 1.43,
          color: theme.palette.text.primary,
          "&::placeholder": {
            color: theme.palette.text.disabled,
            opacity: 1,
          },
        },
      }}
      slotProps={{
        htmlInput: { type: "search", "aria-label": ariaLabel },
        input: {
          startAdornment: (
            <InputAdornment position="start" sx={{ color: theme.palette.primary.main }}>
              <Icon name="cr:Search" />
            </InputAdornment>
          ),
          endAdornment: (
            <InputAdornment position="end">
              <Box sx={{ display: "flex", alignItems: "center", gap: theme.spacing(1) }}>
                {loading ? <CircularProgress size={16} /> : null}
                {!loading && (value === undefined || value !== "") ? (
                  <IconButton
                    size="small"
                    aria-label="Clear search"
                    onClick={handleClear}
                    sx={{ color: theme.palette.text.secondary }}
                  >
                    <Icon name="cr:Dismiss" size={16} />
                  </IconButton>
                ) : null}
                {shortcut ? (
                  <KeyIndicator aria-hidden="true">{formatShortcut(shortcut)}</KeyIndicator>
                ) : null}
              </Box>
            </InputAdornment>
          ),
        },
      }}
    />
  );
});

SearchInput.displayName = "SearchInput";
