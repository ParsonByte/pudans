import { useCallback, useEffect, useRef, useState, type DragEvent, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./file-drop-zone.module.css";

export interface FileDropZoneProps {
  onFiles: (files: File[]) => void;
  accept?: string;
  multiple?: boolean;
  disabled?: boolean;
  /** Renders a full-viewport overlay instead of an inline dashed area. */
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
 * Drag & drop surface migrated from the legacy Uploader DropFile. Supports an
 * inline drop area or a full-screen overlay, keyboard activation and accept
 * filtering; the actual upload protocol stays in the consuming application.
 */
export function FileDropZone({
  onFiles,
  accept,
  multiple = true,
  disabled = false,
  overlay = false,
  title = "Drop files to upload",
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

  const body = (
    <>
      <Icon name="cr:CloudArrowIUp" size={overlay ? 56 : 28} />
      <span className={styles.title}>{title}</span>
      {description ? <span className={styles.description}>{description}</span> : null}
      {!overlay && children}
    </>
  );

  if (overlay) {
    return dragging ? (
      <div
        className={cn(styles.overlay, className)}
        role="presentation"
        onDragOver={handleDragOver}
        onDrop={handleDrop}
      >
        <div className={styles.overlayCard}>
          <span className={styles.overlayBody}>{body}</span>
        </div>
      </div>
    ) : null;
  }

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled || undefined}
      className={cn(
        styles.root,
        dragging && styles.dragging,
        disabled && styles.disabled,
        className,
      )}
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
    >
      {body}
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
    </div>
  );
}
