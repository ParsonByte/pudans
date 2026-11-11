import { useCallback, useEffect, useRef, useState, type DragEvent, type ReactNode } from "react";
import { Backdrop, Box, Typography } from "@mui/material";
import { Icon } from "../icon";

export interface FileDropZoneProps {
  onFiles: (files: File[]) => void;
  accept?: string;
  multiple?: boolean;
  disabled?: boolean;
  /** Renders the legacy full-viewport MUI Backdrop instead of an inline area. */
  overlay?: boolean;
  title?: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  className?: string;
}

function matchesAccept(file: File, accept: string | undefined): boolean {
  if (!accept) {
    return true;
  }
  return accept.split(",").some((token) => {
    const rule = token.trim().toLowerCase();
    if (!rule) {
      return false;
    }
    if (rule.startsWith(".")) {
      return file.name.toLowerCase().endsWith(rule);
    }
    if (rule.endsWith("/*")) {
      return file.type.startsWith(rule.slice(0, -1));
    }
    return file.type === rule;
  });
}

/**
 * Drag & drop surface rebuilt from the legacy Uploader/DropFile:
 * - overlay mode = MUI Backdrop (zIndex drawer + 1, white content, 80px icon,
 *   h4 label) shown while dragging files over the window;
 * - inline mode = dashed token-styled drop target.
 */
export function FileDropZone({
  onFiles,
  accept,
  multiple = true,
  disabled = false,
  overlay = false,
  title = "Drop files here",
  description,
  children,
  className,
}: FileDropZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const depthRef = useRef(0);
  const [dragging, setDragging] = useState(false);

  const deliver = useCallback(
    (fileList: FileList | null) => {
      if (!fileList || disabled) {
        return;
      }
      const files = Array.from(fileList).filter((file) => matchesAccept(file, accept));
      if (files.length > 0) {
        onFiles(multiple ? files : files.slice(0, 1));
      }
    },
    [accept, disabled, multiple, onFiles],
  );

  useEffect(() => {
    if (!overlay || disabled) {
      return;
    }
    const handleDragEnter = (event: globalThis.DragEvent): void => {
      event.preventDefault();
      depthRef.current += 1;
      setDragging(true);
    };
    const handleDragOver = (event: globalThis.DragEvent): void => {
      event.preventDefault();
    };
    const handleDragLeave = (event: globalThis.DragEvent): void => {
      event.preventDefault();
      depthRef.current = Math.max(0, depthRef.current - 1);
      if (depthRef.current === 0) {
        setDragging(false);
      }
    };
    const handleDrop = (event: globalThis.DragEvent): void => {
      event.preventDefault();
      depthRef.current = 0;
      setDragging(false);
      deliver(event.dataTransfer?.files ?? null);
    };
    window.addEventListener("dragenter", handleDragEnter);
    window.addEventListener("dragover", handleDragOver);
    window.addEventListener("dragleave", handleDragLeave);
    window.addEventListener("drop", handleDrop);
    return () => {
      window.removeEventListener("dragenter", handleDragEnter);
      window.removeEventListener("dragover", handleDragOver);
      window.removeEventListener("dragleave", handleDragLeave);
      window.removeEventListener("drop", handleDrop);
    };
  }, [deliver, disabled, overlay]);

  const handleDragEnter = (event: DragEvent<HTMLDivElement>): void => {
    event.preventDefault();
    if (!disabled && !overlay) {
      setDragging(true);
    }
  };

  const handleDragOver = (event: DragEvent<HTMLDivElement>): void => {
    event.preventDefault();
  };

  const handleDragLeave = (event: DragEvent<HTMLDivElement>): void => {
    event.preventDefault();
    if (!overlay) {
      setDragging(false);
    }
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>): void => {
    event.preventDefault();
    if (!overlay) {
      setDragging(false);
    }
    deliver(event.dataTransfer?.files ?? null);
  };

  const openPicker = (): void => {
    if (!disabled) {
      inputRef.current?.click();
    }
  };

  if (overlay) {
    return (
      <Backdrop
        open={dragging}
        className={className}
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
          color: (theme) => theme.palette.common.white,
          flexDirection: "column",
          gap: 1,
          textAlign: "center",
        }}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
      >
        <Box sx={{ fontSize: (theme) => theme.spacing(10), lineHeight: 0 }}>
          <Icon name="cr:CloudArrowIUp" />
        </Box>
        <Typography variant="h4" color="inherit">
          {title}
        </Typography>
        {description ? (
          <Typography variant="body1" color="inherit">
            {description}
          </Typography>
        ) : null}
      </Backdrop>
    );
  }

  return (
    <Box
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled || undefined}
      className={className}
      onClick={openPicker}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openPicker();
        }
      }}
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 1,
        minHeight: (theme) => theme.spacing(20),
        padding: (theme) => theme.spacing(3),
        border: (theme) => `${theme.spacing(0.25)} dashed ${theme.palette.divider}`,
        borderRadius: (theme) => theme.shape.borderRadius,
        backgroundColor: dragging ? "action.selected" : "action.hover",
        color: dragging ? "primary.main" : "text.secondary",
        textAlign: "center",
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.5 : 1,
        transition: (theme) =>
          theme.transitions.create(["border-color", "background-color", "color"]),
        "&:hover": {
          borderColor: "primary.main",
          color: "primary.main",
        },
        "&:focus-visible": {
          outline: (theme) => `${theme.spacing(0.25)} solid ${theme.palette.primary.main}`,
          outlineOffset: (theme) => theme.spacing(0.25),
        },
        "& .MuiBox-root": { pointerEvents: "none" },
      }}
    >
      <Box sx={{ fontSize: (theme) => theme.spacing(3.5), lineHeight: 0 }}>
        <Icon name="cr:CloudArrowIUp" />
      </Box>
      <Typography variant="subtitle1" color="inherit">
        {title}
      </Typography>
      {description ? (
        <Typography variant="body2" color="inherit">
          {description}
        </Typography>
      ) : null}
      {children}
      <input
        ref={inputRef}
        type="file"
        hidden
        accept={accept}
        multiple={multiple}
        disabled={disabled}
        onChange={(event) => {
          deliver(event.target.files);
          event.target.value = "";
        }}
      />
    </Box>
  );
}
