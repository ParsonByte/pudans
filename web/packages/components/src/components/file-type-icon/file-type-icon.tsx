import type { HTMLAttributes, ReactNode } from "react";
import { Box, useTheme, type SxProps, type Theme } from "@mui/material";
import { cn } from "@pudans/utils";
import { Icon } from "../icon";

export type FileIconTone = "brand" | "success" | "warning" | "danger" | "info" | "neutral";

export interface FileTypeIconMapping {
  icon: string;
  tone: FileIconTone;
}

/** Legacy builtInIcons map, keyed by the backend category name. */
export const builtInFileIcons: Record<string, string> = {
  audio: "pd:MusicNote1",
  video: "pd:Video",
  image: "pd:Image",
  pdf: "pd:DocumentPDF",
  word: "pd:FileWordBox",
  ppt: "pd:FilePowerPointBox",
  excel: "pd:FileExclBox",
  text: "pd:Notepad",
  torrent: "pd:MagnetOn",
  zip: "pd:FolderZip",
  exe: "pd:WindowApps",
  android: "pd:PhoneLaptop",
  go: "pd:LanguageGo",
  c: "pd:LanguageC",
  cpp: "pd:LanguageCPP",
  js: "pd:LanguageJS",
  python: "pd:LanguagePython",
  book: "pd:Book",
  rust: "pd:LanguageRust",
  raw: "pd:Raw",
  flowchart: "pd:DocumentFlowchart",
  whiteboard: "pd:Whiteboard",
  markdown: "pd:Markdown",
};

const FOLDER_ICON = "pd:Folder";
const FOLDER_OUTLINED_ICON = "pd:FolderOutlined";
const UNKNOWN_ICON = "pd:Document";

const EXTENSION_MAP: Record<string, FileTypeIconMapping> = {
  jpg: { icon: "pd:Image", tone: "neutral" },
  jpeg: { icon: "pd:Image", tone: "neutral" },
  png: { icon: "pd:Image", tone: "neutral" },
  gif: { icon: "pd:Image", tone: "neutral" },
  webp: { icon: "pd:Image", tone: "neutral" },
  heic: { icon: "pd:Image", tone: "neutral" },
  svg: { icon: "pd:Image", tone: "neutral" },
  avif: { icon: "pd:Image", tone: "neutral" },
  mp4: { icon: "pd:Video", tone: "neutral" },
  mov: { icon: "pd:Video", tone: "neutral" },
  mkv: { icon: "pd:Video", tone: "neutral" },
  webm: { icon: "pd:Video", tone: "neutral" },
  avi: { icon: "pd:Video", tone: "neutral" },
  mp3: { icon: "pd:MusicNote1", tone: "neutral" },
  flac: { icon: "pd:MusicNote1", tone: "neutral" },
  wav: { icon: "pd:MusicNote1", tone: "neutral" },
  aac: { icon: "pd:MusicNote1", tone: "neutral" },
  pdf: { icon: "pd:DocumentPDF", tone: "neutral" },
  doc: { icon: "pd:FileWordBox", tone: "neutral" },
  docx: { icon: "pd:FileWordBox", tone: "neutral" },
  xls: { icon: "pd:FileExclBox", tone: "neutral" },
  xlsx: { icon: "pd:FileExclBox", tone: "neutral" },
  csv: { icon: "pd:FileExclBox", tone: "neutral" },
  ppt: { icon: "pd:FilePowerPointBox", tone: "neutral" },
  pptx: { icon: "pd:FilePowerPointBox", tone: "neutral" },
  zip: { icon: "pd:FolderZip", tone: "neutral" },
  rar: { icon: "pd:FolderZip", tone: "neutral" },
  "7z": { icon: "pd:FolderZip", tone: "neutral" },
  tar: { icon: "pd:FolderZip", tone: "neutral" },
  gz: { icon: "pd:FolderZip", tone: "neutral" },
  ts: { icon: "pd:LanguageJS", tone: "neutral" },
  tsx: { icon: "pd:LanguageJS", tone: "neutral" },
  js: { icon: "pd:LanguageJS", tone: "neutral" },
  jsx: { icon: "pd:LanguageJS", tone: "neutral" },
  json: { icon: "pd:LanguageJS", tone: "neutral" },
  py: { icon: "pd:LanguagePython", tone: "neutral" },
  go: { icon: "pd:LanguageGo", tone: "neutral" },
  rs: { icon: "pd:LanguageRust", tone: "neutral" },
  c: { icon: "pd:LanguageC", tone: "neutral" },
  cpp: { icon: "pd:LanguageCPP", tone: "neutral" },
  php: { icon: "pd:LanguagePHP", tone: "neutral" },
  md: { icon: "pd:Markdown", tone: "neutral" },
  txt: { icon: "pd:Notepad", tone: "neutral" },
  log: { icon: "pd:Notepad", tone: "neutral" },
  epub: { icon: "pd:Book", tone: "neutral" },
  mobi: { icon: "pd:Book", tone: "neutral" },
  torrent: { icon: "pd:MagnetOn", tone: "neutral" },
  exe: { icon: "pd:WindowApps", tone: "neutral" },
  apk: { icon: "pd:PhoneLaptop", tone: "neutral" },
  sql: { icon: "pd:Server", tone: "neutral" },
  db: { icon: "pd:Server", tone: "neutral" },
  ttf: { icon: "pd:TextCaseTitle", tone: "neutral" },
  otf: { icon: "pd:TextCaseTitle", tone: "neutral" },
  woff: { icon: "pd:TextCaseTitle", tone: "neutral" },
  woff2: { icon: "pd:TextCaseTitle", tone: "neutral" },
  excalidraw: { icon: "pd:Whiteboard", tone: "neutral" },
  canvas: { icon: "pd:Whiteboard", tone: "neutral" },
};

const FOLDER_MAPPING: FileTypeIconMapping = { icon: FOLDER_ICON, tone: "neutral" };
const UNKNOWN_MAPPING: FileTypeIconMapping = { icon: UNKNOWN_ICON, tone: "neutral" };

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

const SIZE_MAP = { sm: 2.5, md: 3, lg: 4 } as const;

const TONE_COLOR: Record<FileIconTone, string> = {
  brand: "primary.main",
  success: "success.main",
  warning: "warning.main",
  danger: "error.main",
  info: "info.main",
  neutral: "action.active",
};

const PALETTE_COLOR: Record<string, string> = {
  action: "action.active",
  primary: "primary.main",
  secondary: "secondary.main",
  error: "error.main",
  warning: "warning.main",
  info: "info.main",
  success: "success.main",
  disabled: "text.disabled",
  inherit: "inherit",
};

function isImageSource(value: string): boolean {
  return /^(https?:|data:|\/)/.test(value);
}

export interface FileTypeIconProps extends Omit<HTMLAttributes<HTMLSpanElement>, "children" | "color"> {
  /** File name or raw extension, e.g. "report.pdf" or "pdf". */
  name: string;
  isFolder?: boolean;
  size?: "sm" | "md" | "lg";
  /** Override the mapped icon, e.g. "pd:Image". */
  icon?: string;
  tone?: FileIconTone;
  label?: string;
  /** Legacy fileType number: 1 = folder. */
  fileType?: number;
  /** Legacy loading folder falls back to FolderOutlined. */
  notLoaded?: boolean;
  /** Legacy customizedColor (e.g. file metadata icon_color). */
  customizedColor?: string;
  /** Legacy SvgIcon color prop (palette key or CSS colour). */
  color?: string;
  reverseDarkMode?: boolean;
  hideUnknown?: boolean;
  sx?: SxProps<Theme>;
}

/**
 * Legacy FileManager/Explorer/FileTypeIcon: a bare SvgIcon-sized glyph (24px
 * medium / 20px small in legacy call sites) instead of a coloured tile. Colour
 * defaults to palette.action.active; customizedColor wins, then the legacy
 * palette `color` prop, then an explicitly provided semantic tone. Supports the
 * legacy folder / notLoaded / hideUnknown / image (img) / iconify paths, and the
 * same built-in category map used by the legacy backend defaults.
 */
export function FileTypeIcon({
  name,
  isFolder = false,
  size = "md",
  icon,
  tone,
  label,
  fileType,
  notLoaded = false,
  customizedColor,
  color,
  reverseDarkMode = false,
  hideUnknown = false,
  className,
  sx,
  ...props
}: FileTypeIconProps) {
  const theme = useTheme();
  const folder = isFolder || fileType === 1;
  const mapping = resolveFileTypeIcon(name, folder);
  const resolvedIcon = icon ?? (folder ? (notLoaded ? FOLDER_OUTLINED_ICON : FOLDER_ICON) : mapping.icon);
  const isUnknown = !folder && !icon && mapping.icon === UNKNOWN_ICON;
  const dimension = theme.spacing(SIZE_MAP[size]);

  if (isUnknown && hideUnknown) {
    return null;
  }

  let iconColor: string;
  if (customizedColor) {
    iconColor = customizedColor;
  } else if (color) {
    iconColor = PALETTE_COLOR[color] ?? color;
  } else if (tone) {
    iconColor = TONE_COLOR[tone];
  } else {
    iconColor = "action.active";
  }
  if (reverseDarkMode && theme.palette.mode === "dark" && !customizedColor) {
    iconColor = "action.active";
  }

  const content: ReactNode =
    icon && isImageSource(icon) ? (
      <Box
        component="img"
        src={icon}
        alt=""
        draggable={false}
        sx={{ width: dimension, height: dimension, objectFit: "contain" }}
      />
    ) : (
      <Icon name={resolvedIcon} size={dimension} color="inherit" />
    );

  return (
    <Box
      component="span"
      className={cn(className)}
      role="img"
      aria-label={label ?? name}
      sx={[
        {
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
          color: iconColor,
          "& svg": { display: "block" },
        },
        ...(Array.isArray(sx) ? sx : sx ? [sx] : []),
      ]}
      {...(props as Record<string, unknown>)}
    >
      {content}
    </Box>
  );
}
