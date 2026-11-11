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
  audio: "cr:MusicNote1",
  video: "cr:Video",
  image: "cr:Image",
  pdf: "cr:DocumentPDF",
  word: "cr:FileWordBox",
  ppt: "cr:FilePowerPointBox",
  excel: "cr:FileExclBox",
  text: "cr:Notepad",
  torrent: "cr:MagnetOn",
  zip: "cr:FolderZip",
  exe: "cr:WindowApps",
  android: "cr:PhoneLaptop",
  go: "cr:LanguageGo",
  c: "cr:LanguageC",
  cpp: "cr:LanguageCPP",
  js: "cr:LanguageJS",
  python: "cr:LanguagePython",
  book: "cr:Book",
  rust: "cr:LanguageRust",
  raw: "cr:Raw",
  flowchart: "cr:DocumentFlowchart",
  whiteboard: "cr:Whiteboard",
  markdown: "cr:Markdown",
};

const FOLDER_ICON = "cr:Folder";
const FOLDER_OUTLINED_ICON = "cr:FolderOutlined";
const UNKNOWN_ICON = "cr:Document";

const EXTENSION_MAP: Record<string, FileTypeIconMapping> = {
  jpg: { icon: "cr:Image", tone: "neutral" },
  jpeg: { icon: "cr:Image", tone: "neutral" },
  png: { icon: "cr:Image", tone: "neutral" },
  gif: { icon: "cr:Image", tone: "neutral" },
  webp: { icon: "cr:Image", tone: "neutral" },
  heic: { icon: "cr:Image", tone: "neutral" },
  svg: { icon: "cr:Image", tone: "neutral" },
  avif: { icon: "cr:Image", tone: "neutral" },
  mp4: { icon: "cr:Video", tone: "neutral" },
  mov: { icon: "cr:Video", tone: "neutral" },
  mkv: { icon: "cr:Video", tone: "neutral" },
  webm: { icon: "cr:Video", tone: "neutral" },
  avi: { icon: "cr:Video", tone: "neutral" },
  mp3: { icon: "cr:MusicNote1", tone: "neutral" },
  flac: { icon: "cr:MusicNote1", tone: "neutral" },
  wav: { icon: "cr:MusicNote1", tone: "neutral" },
  aac: { icon: "cr:MusicNote1", tone: "neutral" },
  pdf: { icon: "cr:DocumentPDF", tone: "neutral" },
  doc: { icon: "cr:FileWordBox", tone: "neutral" },
  docx: { icon: "cr:FileWordBox", tone: "neutral" },
  xls: { icon: "cr:FileExclBox", tone: "neutral" },
  xlsx: { icon: "cr:FileExclBox", tone: "neutral" },
  csv: { icon: "cr:FileExclBox", tone: "neutral" },
  ppt: { icon: "cr:FilePowerPointBox", tone: "neutral" },
  pptx: { icon: "cr:FilePowerPointBox", tone: "neutral" },
  zip: { icon: "cr:FolderZip", tone: "neutral" },
  rar: { icon: "cr:FolderZip", tone: "neutral" },
  "7z": { icon: "cr:FolderZip", tone: "neutral" },
  tar: { icon: "cr:FolderZip", tone: "neutral" },
  gz: { icon: "cr:FolderZip", tone: "neutral" },
  ts: { icon: "cr:LanguageJS", tone: "neutral" },
  tsx: { icon: "cr:LanguageJS", tone: "neutral" },
  js: { icon: "cr:LanguageJS", tone: "neutral" },
  jsx: { icon: "cr:LanguageJS", tone: "neutral" },
  json: { icon: "cr:LanguageJS", tone: "neutral" },
  py: { icon: "cr:LanguagePython", tone: "neutral" },
  go: { icon: "cr:LanguageGo", tone: "neutral" },
  rs: { icon: "cr:LanguageRust", tone: "neutral" },
  c: { icon: "cr:LanguageC", tone: "neutral" },
  cpp: { icon: "cr:LanguageCPP", tone: "neutral" },
  php: { icon: "cr:LanguagePHP", tone: "neutral" },
  md: { icon: "cr:Markdown", tone: "neutral" },
  txt: { icon: "cr:Notepad", tone: "neutral" },
  log: { icon: "cr:Notepad", tone: "neutral" },
  epub: { icon: "cr:Book", tone: "neutral" },
  mobi: { icon: "cr:Book", tone: "neutral" },
  torrent: { icon: "cr:MagnetOn", tone: "neutral" },
  exe: { icon: "cr:WindowApps", tone: "neutral" },
  apk: { icon: "cr:PhoneLaptop", tone: "neutral" },
  sql: { icon: "cr:Server", tone: "neutral" },
  db: { icon: "cr:Server", tone: "neutral" },
  ttf: { icon: "cr:TextCaseTitle", tone: "neutral" },
  otf: { icon: "cr:TextCaseTitle", tone: "neutral" },
  woff: { icon: "cr:TextCaseTitle", tone: "neutral" },
  woff2: { icon: "cr:TextCaseTitle", tone: "neutral" },
  excalidraw: { icon: "cr:Whiteboard", tone: "neutral" },
  canvas: { icon: "cr:Whiteboard", tone: "neutral" },
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
  /** Override the mapped icon, e.g. "cr:Image". */
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
 * same built-in category map used by the Cloudreve backend defaults.
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
