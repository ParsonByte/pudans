import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { List, ListItemButton, ListItemIcon, ListItemText } from "@mui/material";
import { cn } from "@pudans/shared";
import { Dialog } from "../dialog";
import styles from "./select-dialog.module.css";

export interface SelectDialogOption<T = string> {
  value: T;
  label: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  disabled?: boolean;
}

export interface SelectDialogProps<T = string> {
  open: boolean;
  title?: ReactNode;
  description?: ReactNode;
  options: readonly SelectDialogOption<T>[];
  onSelect: (value: T, option: SelectDialogOption<T>) => void;
  onCancel: () => void;
}

/**
 * Legacy SelectOption dialog: DraggableDialog + MUI List of ListItemButton rows
 * whose primary text is body2/bold and secondary text is body2.
 */
export function SelectDialog<T = string>({
  open,
  title = "Select an option",
  description,
  options,
  onSelect,
  onCancel,
}: SelectDialogProps<T>) {
  return (
    <Dialog open={open} onClose={onCancel} size="sm" title={title} description={description}>
      <List component="nav" disablePadding>
          {options.map((option, index) => (
            <ListItemButton
              key={index}
              disabled={option.disabled}
              onClick={() => onSelect(option.value, option)}
            >
              {option.icon ? <ListItemIcon>{option.icon}</ListItemIcon> : null}
              <ListItemText
                primary={option.label}
                secondary={option.description}
                slotProps={{
                  primary: { variant: "body2", sx: { fontWeight: "bold" } },
                  secondary: { variant: "body2" },
                }}
              />
            </ListItemButton>
          ))}
      </List>
    </Dialog>
  );
}

export interface SelectConfig {
  title?: ReactNode;
  description?: ReactNode;
}

type SelectFn = <T>(
  options: readonly SelectDialogOption<T>[],
  config?: SelectConfig,
) => Promise<T | null>;

interface SelectRequest {
  id: number;
  options: readonly SelectDialogOption<unknown>[];
  config: SelectConfig;
  resolve: (value: unknown | null) => void;
}

interface SelectContextValue {
  select: SelectFn;
}

const SelectContext = createContext<SelectContextValue | null>(null);

export interface SelectProviderProps {
  children: ReactNode;
}

/**
 * Promise-based option picker migrated from the legacy global SelectOption
 * dialog. Returns null when cancelled.
 */
export function SelectProvider({ children }: SelectProviderProps) {
  const [queue, setQueue] = useState<SelectRequest[]>([]);
  const idRef = useRef(0);
  const current = queue[0];

  const select = useCallback<SelectFn>((options, config) => {
    return new Promise((resolve) => {
      idRef.current += 1;
      setQueue((previous) => [
        ...previous,
        {
          id: idRef.current,
          options: options as readonly SelectDialogOption<unknown>[],
          config: config ?? {},
          resolve: resolve as (value: unknown | null) => void,
        },
      ]);
    });
  }, []);

  const settle = useCallback((value: unknown | null) => {
    setQueue((previous) => {
      const [first, ...rest] = previous;
      first?.resolve(value);
      return rest;
    });
  }, []);

  const value = useMemo<SelectContextValue>(() => ({ select }), [select]);

  return (
    <SelectContext.Provider value={value}>
      {children}
      <SelectDialog
        open={Boolean(current)}
        title={current?.config.title}
        description={current?.config.description}
        options={current?.options ?? []}
        onSelect={(selected) => settle(selected)}
        onCancel={() => settle(null)}
      />
    </SelectContext.Provider>
  );
}

export function useSelectOption(): SelectFn {
  const context = useContext(SelectContext);
  if (!context) {
    throw new Error("useSelectOption must be used inside <SelectProvider>.");
  }
  return context.select;
}

/** Compatibility class for legacy StyledDialogContentText-like bodies. */
export const selectDialogBodyClassName = cn(styles.body);