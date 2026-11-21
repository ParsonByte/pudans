"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { FileWarning, Loader2 } from "lucide-react";

import { MarkdownContent } from "@/components/content/markdown";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

type PreviewKind =
  "docx" | "xlsx" | "zip" | "markdown" | "text" | "pdf" | "image" | "video" | "audio" | "unknown";

type SpreadsheetSheet = {
  name: string;
  rows: string[][];
  truncated: boolean;
};

type ZipEntry = {
  name: string;
  size: number;
  text?: string;
};

type PreviewState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "oversize" }
  | { status: "bytes"; bytes: ArrayBuffer }
  | { status: "sheets"; sheets: SpreadsheetSheet[] }
  | { status: "zip"; entries: ZipEntry[] };

type DocumentPreviewProps = {
  name?: string;
  mimeType?: string;
  size?: number;
  loadBytes: () => Promise<ArrayBuffer>;
  className?: string;
};

const MAX_PREVIEW_BYTES = 20 * 1024 * 1024;

const MAX_SHEET_ROWS = 200;

const MAX_SHEET_COLUMNS = 30;

const MAX_ZIP_ENTRIES = 200;

const MAX_ZIP_TEXT_BYTES = 200 * 1024;

const DOCX_EXTENSIONS = new Set(["docx"]);

const SPREADSHEET_EXTENSIONS = new Set(["xlsx", "xls", "csv"]);

const MARKDOWN_EXTENSIONS = new Set(["md", "markdown"]);

const TEXT_EXTENSIONS = new Set(["txt", "json", "log", "yaml", "yml", "xml", "html", "htm"]);

const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "webp", "svg", "avif"]);

const VIDEO_EXTENSIONS = new Set(["mp4", "webm", "mov", "m4v"]);

const AUDIO_EXTENSIONS = new Set(["mp3", "wav", "ogg", "m4a", "aac", "flac"]);

function extensionOf(name: string | undefined): string {
  if (!name) {
    return "";
  }
  const index = name.lastIndexOf(".");
  return index < 0 ? "" : name.slice(index + 1).toLowerCase();
}

function kindOf(name: string | undefined, mimeType: string | undefined): PreviewKind {
  const extension = extensionOf(name);
  const mime = mimeType?.toLowerCase() ?? "";
  if (DOCX_EXTENSIONS.has(extension) || mime.includes("wordprocessingml.document")) {
    return "docx";
  }
  if (SPREADSHEET_EXTENSIONS.has(extension) || mime.includes("spreadsheet")) {
    return "xlsx";
  }
  if (extension === "zip" || mime.includes("zip")) {
    return "zip";
  }
  if (MARKDOWN_EXTENSIONS.has(extension) || mime.includes("markdown")) {
    return "markdown";
  }
  if (extension === "pdf" || mime.includes("pdf")) {
    return "pdf";
  }
  if (IMAGE_EXTENSIONS.has(extension) || mime.startsWith("image/")) {
    return "image";
  }
  if (VIDEO_EXTENSIONS.has(extension) || mime.startsWith("video/")) {
    return "video";
  }
  if (AUDIO_EXTENSIONS.has(extension) || mime.startsWith("audio/")) {
    return "audio";
  }
  if (TEXT_EXTENSIONS.has(extension) || mime.startsWith("text/")) {
    return "text";
  }
  return "unknown";
}

function isTextEntry(name: string): boolean {
  const extension = extensionOf(name);
  return (
    MARKDOWN_EXTENSIONS.has(extension) ||
    TEXT_EXTENSIONS.has(extension) ||
    SPREADSHEET_EXTENSIONS.has(extension)
  );
}

async function spreadsheetSheets(buffer: ArrayBuffer): Promise<SpreadsheetSheet[]> {
  const XLSX = await import("xlsx");
  const workbook = XLSX.read(new Uint8Array(buffer), { type: "array" });
  return workbook.SheetNames.slice(0, 10).map((name) => {
    const sheet = workbook.Sheets[name];
    const rows = sheet
      ? (XLSX.utils.sheet_to_json(sheet, {
          header: 1,
          blankrows: false,
          defval: "",
          raw: false,
        }) as unknown[][])
      : [];
    const truncated = rows.length > MAX_SHEET_ROWS;
    return {
      name,
      rows: rows
        .slice(0, MAX_SHEET_ROWS)
        .map((row) => row.slice(0, MAX_SHEET_COLUMNS).map((value) => String(value ?? ""))),
      truncated,
    };
  });
}

async function zipEntries(buffer: ArrayBuffer): Promise<ZipEntry[]> {
  const { strFromU8, unzipSync } = await import("fflate");
  const unzipped = unzipSync(new Uint8Array(buffer));
  return Object.entries(unzipped)
    .slice(0, MAX_ZIP_ENTRIES)
    .map(([name, bytes]) => ({
      name,
      size: bytes.byteLength,
      text:
        isTextEntry(name) && bytes.byteLength <= MAX_ZIP_TEXT_BYTES ? strFromU8(bytes) : undefined,
    }));
}

export function DocumentPreview({
  name,
  mimeType,
  size,
  loadBytes,
  className,
}: DocumentPreviewProps) {
  const kind = useMemo(() => kindOf(name, mimeType), [mimeType, name]);
  const loadRef = useRef(loadBytes);
  const docxRef = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<PreviewState>({ status: "loading" });
  const [docxLoading, setDocxLoading] = useState(true);
  const [selectedEntry, setSelectedEntry] = useState<string | undefined>(undefined);

  useEffect(() => {
    loadRef.current = loadBytes;
  }, [loadBytes]);

  useEffect(() => {
    let cancelled = false;
    loadRef
      .current()
      .then(async (buffer) => {
        if (cancelled) {
          return;
        }
        if (buffer.byteLength > MAX_PREVIEW_BYTES) {
          setState({ status: "oversize" });
          return;
        }
        if (kind === "xlsx") {
          const sheets = await spreadsheetSheets(buffer);
          if (!cancelled) {
            setState({ status: "sheets", sheets });
          }
          return;
        }
        if (kind === "zip") {
          const entries = await zipEntries(buffer);
          if (!cancelled) {
            setState({ status: "zip", entries });
          }
          return;
        }
        setState({ status: "bytes", bytes: buffer });
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({
            status: "error",
            message: error instanceof Error ? error.message : "无法加载文件。",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [kind]);

  useEffect(() => {
    if (state.status !== "bytes" || kind !== "docx") {
      return;
    }
    const container = docxRef.current;
    if (!container) {
      return;
    }
    let cancelled = false;
    container.innerHTML = "";
    void import("docx-preview")
      .then(({ renderAsync }) =>
        renderAsync(new Blob([state.bytes]), container, undefined, {
          className: "docx-preview",
          inWrapper: true,
          ignoreWidth: true,
          ignoreHeight: true,
          breakPages: false,
          useBase64URL: true,
        }),
      )
      .then(() => {
        if (!cancelled) {
          setDocxLoading(false);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setDocxLoading(false);
          setState({
            status: "error",
            message: error instanceof Error ? error.message : "无法渲染 Word 文档。",
          });
        }
      });
    return () => {
      cancelled = true;
      container.innerHTML = "";
    };
  }, [kind, state]);

  const objectUrl = useMemo(() => {
    if (state.status !== "bytes") {
      return undefined;
    }
    if (kind !== "pdf" && kind !== "image" && kind !== "video" && kind !== "audio") {
      return undefined;
    }
    return URL.createObjectURL(new Blob([state.bytes], { type: mimeType }));
  }, [kind, mimeType, state]);

  useEffect(() => {
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [objectUrl]);

  if (state.status === "loading") {
    return (
      <div className={className}>
        <div className="flex items-center gap-2 px-1 py-3 text-xs text-muted-foreground">
          <Loader2 className="animate-spin" />
          正在加载预览…
        </div>
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (state.status === "oversize") {
    return (
      <div className={className}>
        <div className="flex items-center gap-2 border border-dashed px-4 py-6 text-xs text-muted-foreground">
          <FileWarning className="size-4" />
          文件超过 {formatFileSize(MAX_PREVIEW_BYTES)}，请下载后查看。
        </div>
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className={className}>
        <div className="border border-destructive/30 bg-destructive/5 px-4 py-3 text-xs text-destructive">
          {state.message}
        </div>
      </div>
    );
  }

  if (state.status === "sheets") {
    const first = state.sheets[0]?.name ?? "";
    return (
      <div className={className}>
        {state.sheets.length === 0 ? (
          <p className="px-1 py-3 text-xs text-muted-foreground">工作簿里没有工作表。</p>
        ) : (
          <Tabs defaultValue={first}>
            <TabsList>
              {state.sheets.map((sheet) => (
                <TabsTrigger key={sheet.name} value={sheet.name}>
                  {sheet.name}
                </TabsTrigger>
              ))}
            </TabsList>
            {state.sheets.map((sheet) => (
              <TabsContent key={sheet.name} value={sheet.name}>
                <div className="max-h-[60vh] overflow-auto border">
                  <Table>
                    <TableBody>
                      {sheet.rows.map((row, rowIndex) => (
                        <TableRow key={rowIndex}>
                          {row.map((cell, cellIndex) => (
                            <TableCell key={cellIndex} className="whitespace-nowrap">
                              {cell}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                {sheet.truncated ? (
                  <p className="mt-2 text-[11px] text-muted-foreground">
                    仅显示前 {MAX_SHEET_ROWS} 行、{MAX_SHEET_COLUMNS} 列。
                  </p>
                ) : null}
              </TabsContent>
            ))}
          </Tabs>
        )}
      </div>
    );
  }

  if (state.status === "zip") {
    const selected = state.entries.find((entry) => entry.name === selectedEntry);
    return (
      <div className={className}>
        <div className="max-h-[60vh] overflow-y-auto border">
          {state.entries.map((entry) => (
            <button
              key={entry.name}
              type="button"
              onClick={() => setSelectedEntry(entry.name)}
              className="flex w-full items-center justify-between gap-3 border-b px-3 py-2 text-left text-xs last:border-b-0 hover:bg-muted/50"
            >
              <span className="min-w-0 truncate font-mono">{entry.name}</span>
              <span className="shrink-0 text-muted-foreground">{formatFileSize(entry.size)}</span>
            </button>
          ))}
        </div>
        {selected?.text ? (
          <pre className="mt-3 max-h-72 overflow-auto border bg-muted/30 p-3 font-mono text-xs whitespace-pre-wrap">
            {selected.text}
          </pre>
        ) : null}
      </div>
    );
  }

  if (kind === "docx") {
    return (
      <div className={className}>
        {docxLoading ? (
          <div className="flex items-center gap-2 px-1 py-3 text-xs text-muted-foreground">
            <Loader2 className="animate-spin" />
            正在渲染文档…
          </div>
        ) : null}
        <div ref={docxRef} className="max-h-[65vh] overflow-auto border p-4" />
      </div>
    );
  }

  if (kind === "markdown") {
    return (
      <div className={className}>
        <MarkdownContent content={new TextDecoder().decode(state.bytes)} />
      </div>
    );
  }

  if (kind === "text") {
    return (
      <div className={className}>
        <pre className="max-h-[65vh] overflow-auto border bg-muted/30 p-4 font-mono text-xs whitespace-pre-wrap">
          {new TextDecoder().decode(state.bytes)}
        </pre>
      </div>
    );
  }

  if (kind === "pdf" && objectUrl) {
    return (
      <div className={className}>
        <iframe src={objectUrl} title={name ?? "PDF 预览"} className="h-[65vh] w-full border" />
      </div>
    );
  }

  if (kind === "image" && objectUrl) {
    return (
      <div className={className}>
        <img
          src={objectUrl}
          alt={name ?? "图片"}
          className="max-h-[65vh] max-w-full border object-contain"
        />
      </div>
    );
  }

  if (kind === "video" && objectUrl) {
    return (
      <div className={className}>
        <video src={objectUrl} controls className="max-h-[65vh] w-full border" />
      </div>
    );
  }

  if (kind === "audio" && objectUrl) {
    return (
      <div className={className}>
        <audio src={objectUrl} controls className="w-full" />
      </div>
    );
  }

  return (
    <div className={className}>
      <div className="flex items-center gap-2 border border-dashed px-4 py-6 text-xs text-muted-foreground">
        <FileWarning className="size-4" />
        暂不支持预览此文件类型
        {size != null ? `（${formatFileSize(size)}）` : ""}。
      </div>
    </div>
  );
}

export { kindOf };
