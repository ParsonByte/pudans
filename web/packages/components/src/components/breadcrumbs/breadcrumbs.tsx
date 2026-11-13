import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Box,
  Button,
  ClickAwayListener,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  TextField,
  Tooltip,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { cn } from "@pudans/shared";
import { Icon } from "../icon";

export interface BreadcrumbEntry {
  key?: string;
  label: ReactNode;
  icon?: ReactNode | string;
  href?: string;
  onClick?: () => void;
  /** Marks the filesystem root segment (legacy is_root): no max-width. */
  isRoot?: boolean;
}

export interface BreadcrumbsProps {
  items: readonly BreadcrumbEntry[];
  /** Collapse the middle into an overflow menu above this length. */
  maxItems?: number;
  separator?: ReactNode;
  ariaLabel?: string;
  className?: string;
  /** Enables the legacy click-to-edit path field (legacy Breadcrumb PathTextField). */
  editable?: boolean;
  /** Initial value of the editable path. Defaults to the joined string labels. */
  path?: string;
  /** Called with the edited path on Enter / click-away. */
  onPathSubmit?: (path: string) => void;
  /** Legacy alias: renders the chain without the editable path field. */
  displayOnly?: boolean;
}

function itemKey(item: BreadcrumbEntry, index: number): string {
  return item.key ?? (typeof item.label === "string" ? item.label : "crumb-" + index);
}

function renderIcon(icon: ReactNode | string | undefined, size: number) {
  if (!icon) {
    return null;
  }
  return typeof icon === "string" ? <Icon name={icon} size={size} /> : icon;
}

/**
 * Path trail rebuilt from the legacy FileManager TopBar Breadcrumb trio:
 * MUI small text buttons (32px min height, 13px/500), 15px ChevronRight
 * separators at text.disabled, per-segment max widths (root auto, middle
 * 100px, current 300px), MoreHorizontal overflow Menu and the monospace
 * click-to-edit path TextField.
 */
export function Breadcrumbs({
  items,
  maxItems,
  separator,
  ariaLabel = "Breadcrumb",
  className,
  editable = false,
  path,
  onPathSubmit,
  displayOnly = false,
}: BreadcrumbsProps) {
  const theme = useTheme();
  const chainRef = useRef<HTMLDivElement>(null);
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const canEdit = editable && !displayOnly;
  const [editing, setEditing] = useState(false);
  const [editedPath, setEditedPath] = useState("");
  const [collapsed, setCollapsed] = useState(false);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const fallbackPath = items
    .map((item) => (typeof item.label === "string" ? item.label : ""))
    .filter(Boolean)
    .join("/");
  const currentPath = path ?? fallbackPath;

  useEffect(() => {
    setCollapsed(false);
  }, [items.length]);

  useEffect(() => {
    if (isMobile || maxItems !== undefined || items.length < 2) {
      return;
    }
    const node = chainRef.current;
    if (!node || typeof ResizeObserver === "undefined") {
      return;
    }
    const measure = (): void => {
      if (!collapsed && node.scrollWidth > node.clientWidth + 1) {
        setCollapsed(true);
      }
    };
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    measure();
    return () => observer.disconnect();
  }, [collapsed, isMobile, items.length, maxItems]);

  const hideCount =
    maxItems !== undefined && items.length > maxItems && maxItems >= 2
      ? items.length - 1
      : collapsed
        ? items.length - 1
        : 0;
  const hidden = items.slice(0, hideCount);
  const visible = items.slice(hideCount);
  const hiddenOpen = Boolean(anchorEl);

  const startEditing = (): void => {
    if (!canEdit) {
      return;
    }
    setEditedPath(currentPath);
    setEditing(true);
  };

  const submitPath = (): void => {
    setEditing(false);
    if (editedPath && editedPath !== currentPath) {
      onPathSubmit?.(editedPath);
    }
  };

  const closeHidden = (): void => {
    setAnchorEl(null);
  };

  const segmentSx = (globalIndex: number) => {
    const latest = globalIndex === items.length - 1;
    const isRoot = globalIndex === 0 || items[globalIndex]?.isRoot;
    return {
      minHeight: theme.spacing(4),
      maxWidth: latest ? theme.spacing(37.5) : isRoot ? "none" : theme.spacing(12.5),
      color: latest ? theme.palette.text.primary : theme.palette.text.secondary,
      transition: theme.transitions.create(["max-width", "color", "background-color"], {
        duration: theme.transitions.duration.standard,
        easing: theme.transitions.easing.easeInOut,
      }),
    };
  };

  const chevron = separator ?? (
    <Icon
      name="pd:ChevronRight"
      sx={{
        fontSize: theme.spacing(1.875),
        mx: theme.spacing(0.5),
        color: theme.palette.text.disabled,
        verticalAlign: "middle",
      }}
    />
  );

  return (
    <Box
      component="nav"
      aria-label={ariaLabel}
      className={cn(className)}
      sx={{
        display: "flex",
        alignItems: "center",
        flexGrow: 1,
        minWidth: 0,
        whiteSpace: "nowrap",
        overflow: "auto",
        cursor: canEdit ? "text" : "default",
      }}
      onClick={startEditing}
    >
      {editing ? (
        <ClickAwayListener mouseEvent="onMouseDown" onClickAway={submitPath}>
          <TextField
            autoFocus
            fullWidth
            size="small"
            value={editedPath}
            onClick={(event) => event.stopPropagation()}
            onChange={(event) => setEditedPath(event.target.value)}
            onFocus={(event) => event.target.select()}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                submitPath();
              }
            }}
            slotProps={{ htmlInput: { "aria-label": "Path" } }}
            sx={{
              "& .MuiOutlinedInput-notchedOutline": { border: "none" },
              "& .MuiOutlinedInput-input": {
                padding: theme.spacing(0.75, 0.5),
                fontSize: theme.spacing(1.625),
                lineHeight: 1.5,
                fontFamily: "var(--pd-font-mono)",
              },
              height: theme.spacing(4),
              overflow: "hidden",
              verticalAlign: "middle",
            }}
          />
        </ClickAwayListener>
      ) : (
        <>
          {hidden.length > 0 ? (
            <>
              <Button
                size="small"
                color="inherit"
                onClick={(event) => {
                  event.stopPropagation();
                  setAnchorEl(event.currentTarget);
                }}
                sx={{ minHeight: theme.spacing(4) }}
              >
                <Icon name="pd:MoreHorizontal" />
              </Button>
              {chevron}
            </>
          ) : null}
          {visible.map((item, index) => {
            const globalIndex = hideCount + index;
            const latest = globalIndex === items.length - 1;
            return (
              <Box
                key={itemKey(item, globalIndex)}
                component="span"
                sx={{ display: "inline-flex", alignItems: "center", minWidth: 0 }}
              >
                <Button
                  size="small"
                  color="inherit"
                  href={item.href}
                  onClick={
                    item.href
                      ? undefined
                      : (event) => {
                          event.stopPropagation();
                          if (!latest) {
                            item.onClick?.();
                          }
                        }
                  }
                  startIcon={renderIcon(item.icon, 24) ?? undefined}
                  endIcon={
                    latest && items.length > 1 ? (
                      <Icon name="pd:CaretDown" sx={{ fontSize: theme.spacing(1.5) }} />
                    ) : undefined
                  }
                  sx={segmentSx(globalIndex)}
                >
                  <Tooltip
                    title={typeof item.label === "string" ? item.label : ""}
                    placement="bottom"
                  >
                    <Box
                      component="span"
                      sx={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                    >
                      {item.label}
                    </Box>
                  </Tooltip>
                </Button>
                {!latest ? chevron : null}
              </Box>
            );
          })}
        </>
      )}
      <Menu
        anchorEl={anchorEl}
        open={hiddenOpen}
        onClose={closeHidden}
        slotProps={{ list: { dense: true } }}
      >
        {hidden.map((item, index) => (
          <MenuItem
            key={itemKey(item, index)}
            onClick={() => {
              closeHidden();
              item.onClick?.();
            }}
          >
            <ListItemIcon>{renderIcon(item.icon, 20)}</ListItemIcon>
            <ListItemText>{item.label}</ListItemText>
          </MenuItem>
        ))}
      </Menu>
    </Box>
  );
}
