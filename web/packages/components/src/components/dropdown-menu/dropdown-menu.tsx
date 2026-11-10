import { useEffect, useId, type KeyboardEvent, type ReactNode } from "react";
import { Popover, type PopoverProps } from "@mui/material";
import { cn } from "../../utils/cn";
import {
  bindContextMenu,
  bindPopover,
  bindTrigger,
  usePopupState,
} from "material-ui-popup-state/hooks";
import { Icon } from "../icon";
import styles from "./dropdown-menu.module.css";

export interface DropdownMenuItem {
  key: string;
  label?: ReactNode;
  icon?: ReactNode;
  shortcut?: ReactNode;
  disabled?: boolean;
  danger?: boolean;
  divider?: boolean;
  onSelect?: () => void;
}

export interface DropdownMenuProps {
  trigger: ReactNode;
  items: readonly DropdownMenuItem[];
  onOpenChange?: (open: boolean) => void;
  onSelect?: (item: DropdownMenuItem) => void;
  closeOnSelect?: boolean;
  contextMenu?: boolean;
  showChevron?: boolean;
  triggerLabel?: string;
  triggerClassName?: string;
  menuClassName?: string;
  className?: string;
  anchorOrigin?: PopoverProps["anchorOrigin"];
  transformOrigin?: PopoverProps["transformOrigin"];
}

/**
 * Popover-backed menu powered by material-ui-popup-state. Click-outside closing
 * is delegated to Popover; contextMenu mode opens at the pointer coordinates.
 */
export function DropdownMenu({
  trigger,
  items,
  onOpenChange,
  onSelect,
  closeOnSelect = true,
  contextMenu = false,
  showChevron = true,
  triggerLabel = "Open menu",
  triggerClassName,
  menuClassName,
  className,
  anchorOrigin = { vertical: "bottom", horizontal: "right" },
  transformOrigin = { vertical: "top", horizontal: "right" },
}: DropdownMenuProps) {
  const popupState = usePopupState({
    variant: "popover",
    popupId: `pd-dropdown-${useId().replace(/:/g, "")}`,
  });
  const triggerProps = contextMenu ? bindContextMenu(popupState) : bindTrigger(popupState);
  const popoverProps = bindPopover(popupState);

  useEffect(() => {
    onOpenChange?.(popupState.isOpen);
  }, [onOpenChange, popupState.isOpen]);

  const handleKeyDown = (event: KeyboardEvent<HTMLSpanElement>): void => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      popupState.open(event.currentTarget);
    }
  };

  const handleSelect = (item: DropdownMenuItem): void => {
    if (item.disabled) {
      return;
    }
    item.onSelect?.();
    onSelect?.(item);
    if (closeOnSelect) {
      popupState.close();
    }
  };

  return (
    <span className={cn(styles.root, className)}>
      <span
        {...triggerProps}
        className={cn(styles.trigger, triggerClassName)}
        role="button"
        tabIndex={0}
        aria-label={triggerLabel}
        aria-expanded={popupState.isOpen}
        onKeyDown={handleKeyDown}
      >
        {trigger}
        {!contextMenu && showChevron ? (
          <Icon name="mdi:menu-down-outline" size={14} className={styles.chevron} />
        ) : null}
      </span>

      <Popover
        {...popoverProps}
        anchorOrigin={anchorOrigin}
        transformOrigin={transformOrigin}
        slotProps={{
          paper: {
            className: cn(styles.paper, menuClassName),
          },
        }}
      >
        <div className={styles.menu} role="menu">
          {items.map((item) =>
            item.divider ? (
              <div key={item.key} className={styles.divider} role="separator" />
            ) : (
              <button
                key={item.key}
                type="button"
                role="menuitem"
                className={cn(
                  styles.item,
                  item.danger && styles.danger,
                  item.disabled && styles.disabled,
                )}
                disabled={item.disabled}
                onClick={() => handleSelect(item)}
              >
                {item.icon ? (
                  <span className={styles.itemIcon} aria-hidden="true">
                    {item.icon}
                  </span>
                ) : null}
                <span className={styles.itemLabel}>{item.label}</span>
                {item.shortcut ? <span className={styles.shortcut}>{item.shortcut}</span> : null}
              </button>
            ),
          )}
        </div>
      </Popover>
    </span>
  );
}
