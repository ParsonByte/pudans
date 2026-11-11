import type { HTMLAttributes } from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./file-type-icon.module.css";

export type FileIconTone = "brand" | "success" | "warning" | "danger" | "info" | "neutral";

export interface FileTypeIconMapping {
  icon: string;
  tone: FileIconTone;
}

const EXTENSION_MAP: Record<string, FileTypeIconMapping> = {
  jpg: { icon: "cr:Image", tone: "success" },
  jpeg: { icon: "cr:Image", tone: "success" },
  png: { icon: "cr:Image", tone: "success" },
  gif: { icon: "cr:Image", tone: "success" },
  webp: { icon: "cr:Image", tone: "success" },
  heic: { icon: "cr:Image", tone: "success" },
  svg: { icon: "cr:Image", tone: "success" },
  avif: { icon: "cr:Image", tone: "success" },
  mp4: { icon: "cr:Video", tone: "danger" },
  mov: { icon: "cr:Video", tone: "danger" },
  mkv: { icon: "cr:Video", tone: "danger" },
  webm: { icon: "cr:Video", tone: "danger" },
  avi: { icon: "cr:Video", tone: "danger" },
  mp3: { icon: "cr:MusicNote1", tone: "warning" },
  flac: { icon: "cr:MusicNote1", tone: "warning" },
  wav: { icon: "cr:MusicNote1", tone: "warning" },
  aac: { icon: "cr:MusicNote1", tone: "warning" },
  pdf: { icon: "cr:DocumentPDF", tone: "danger" },
  doc: { icon: "cr:FileWordBox", tone: "info" },
  docx: { icon: "cr:FileWordBox", tone: "info" },
  xls: { icon: "cr:FileExclBox", tone: "success" },
  xlsx: { icon: "cr:FileExclBox", tone: "success" },
  csv: { icon: "cr:FileExclBox", tone: "success" },
  ppt: { icon: "cr:FilePowerPointBox", tone: "warning" },
  pptx: { icon: "cr:FilePowerPointBox", tone: "warning" },
  zip: { icon: "cr:FolderZip", tone: "neutral" },
  rar: { icon: "cr:FolderZip", tone: "neutral" },
  "7z": { icon: "cr:FolderZip", tone: "neutral" },
  tar: { icon: "cr:FolderZip", tone: "neutral" },
  gz: { icon: "cr:FolderZip", tone: "neutral" },
  ts: { icon: "cr:LanguageJS", tone: "info" },
  tsx: { icon: "cr:LanguageJS", tone: "info" },
  js: { icon: "cr:LanguageJS", tone: "info" },
  jsx: { icon: "cr:LanguageJS", tone: "info" },
  json: { icon: "cr:LanguageJS", tone: "info" },
  py: { icon: "cr:LanguagePython", tone: "info" },
  go: { icon: "cr:LanguageGo", tone: "info" },
  rs: { icon: "cr:LanguageRust", tone: "info" },
  c: { icon: "cr:LanguageC", tone: "info" },
  cpp: { icon: "cr:LanguageCPP", tone: "info" },
  php: { icon: "cr:LanguagePHP", tone: "info" },
  md: { icon: "cr:Markdown", tone: "neutral" },
  txt: { icon: "cr:Notepad", tone: "neutral" },
  log: { icon: "cr:Notepad", tone: "neutral" },
  epub: { icon: "cr:Book", tone: "brand" },
  mobi: { icon: "cr:Book", tone: "brand" },
  torrent: { icon: "cr:MagnetOn", tone: "info" },
  exe: { icon: "cr:WindowApps", tone: "neutral" },
  apk: { icon: "cr:WindowApps", tone: "neutral" },
  sql: { icon: "cr:Server", tone: "info" },
  db: { icon: "cr:Server", tone: "info" },
  ttf: { icon: "cr:TextCaseTitle", tone: "neutral" },
  otf: { icon: "cr:TextCaseTitle", tone: "neutral" },
  woff: { icon: "cr:TextCaseTitle", tone: "neutral" },
  woff2: { icon: "cr:TextCaseTitle", tone: "neutral" },
  excalidraw: { icon: "cr:Whiteboard", tone: "brand" },
  canvas: { icon: "cr:Whiteboard", tone: "brand" },
};

const FOLDER_MAPPING: FileTypeIconMapping = { icon: "cr:Folder", tone: "brand" };
const UNKNOWN_MAPPING: FileTypeIconMapping = { icon: "cr:Document", tone: "neutral" };

export function resolveFileTypeIcon(
  nameOrExtension: string,
  isFolder = false,
): FileTypeIconMapping {
  if (isFolder) {
    return FOLDER_MAPPING;
  }
  const trimmed = nameOrExtension.trim().toLowerCase();
  if (!trimmed) {
    return UNKNOWN_MAPPING;
  }
  const extension = trimmed.includes(".") ? (trimmed.split(".").pop() ?? "") : trimmed;
  return EXTENSION_MAP[extension] ?? UNKNOWN_MAPPING;
}

export interface FileTypeIconProps extends Omit<HTMLAttributes<HTMLSpanElement>, "children"> {
  /** File name or raw extension, e.g. "report.pdf" or "pdf". */
  name: string;
  isFolder?: boolean;
  size?: "sm" | "md" | "lg";
  /** Override the mapped icon, e.g. "cr:Image". */
  icon?: string;
  tone?: FileIconTone;
  label?: string;
}

/**
 * Extension-aware file icon with semantic tone. Migrated from the legacy
 * FileTypeIcon / FileIcon / FileSmallIcon trio; no store or i18n coupling.
 */
export function FileTypeIcon({
  name,
  isFolder = false,
  size = "md",
  icon,
  tone,
  label,
  className,
  ...props
}: FileTypeIconProps) {
  const mapping = resolveFileTypeIcon(name, isFolder);
  return (
    <span
      className={cn(styles.root, styles[size], styles[tone ?? mapping.tone], className)}
      aria-label={label ?? name}
      role="img"
      {...props}
    >
      <Icon name={icon ?? mapping.icon} size={size === "sm" ? 16 : size === "lg" ? 28 : 20} />
    </span>
  );
}
