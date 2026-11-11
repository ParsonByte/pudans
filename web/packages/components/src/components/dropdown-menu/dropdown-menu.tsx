import { useEffect, useId, type KeyboardEvent, type ReactNode } from "react";
import {
  Box,
  Divider,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Typography,
  type MenuProps,
} from "@mui/material";
import {
  bindContextMenu,
  bindMenu,
  bindTrigger,
  usePopupState,
} from "material-ui-popup-state/hooks";
import { Icon } from "../icon";

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
  anchorOrigin?: MenuProps["anchorOrigin"];
  transformOrigin?: MenuProps["transformOrigin"];
}

/**
 * Legacy SquareMenu/MenuItem rebuilt on MUI Menu: 8px paper radius,
 * elevation 3, list padding 4px 0 and MenuItem radius 8 / margin 0 4px are
 * inherited from the shared MUI theme. Items are dense (32px) like the legacy
 * FileManager context menu; icons are 20px ListItemIcons whose colour turns
 * primary on hover (error.light for danger items).
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
  const menuProps = bindMenu(popupState);

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
    <>
      <Box
        component="span"
        {...triggerProps}
        className={triggerClassName}
        role="button"
        tabIndex={0}
        aria-label={triggerLabel}
        aria-expanded={popupState.isOpen}
        onKeyDown={handleKeyDown}
        sx={{ display: "inline-flex", alignItems: "center", gap: 0.5, cursor: "pointer" }}
      >
        {trigger}
        {!contextMenu && showChevron ? <Icon name="cr:CaretDown" size={15} /> : null}
      </Box>

      <Menu
        {...menuProps}
        anchorOrigin={anchorOrigin}
        transformOrigin={transformOrigin}
        className={className}
        slotProps={{ paper: { className: menuClassName, sx: { minWidth: (theme) => theme.spacing(25) } } }}
      >
        {items.map((item) =>
          item.divider ? (
            <Divider key={item.key} sx={{ my: 0.5 }} />
          ) : (
            <MenuItem
              key={item.key}
              dense
              disabled={item.disabled}
              onClick={() => handleSelect(item)}
              sx={{
                "&:hover .MuiListItemIcon-root": {
                  color: item.danger ? "error.light" : "primary.main",
                },
              }}
            >
              {item.icon ? <ListItemIcon>{item.icon}</ListItemIcon> : null}
              <ListItemText>{item.label}</ListItemText>
              {item.shortcut ? (
                <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                  {item.shortcut}
                </Typography>
              ) : null}
            </MenuItem>
          ),
        )}
      </Menu>
    </>
  );
}