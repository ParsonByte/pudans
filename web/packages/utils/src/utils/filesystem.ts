/**
 * File System Access API helpers migrated from the legacy frontend
 * util/filesystem. Structural interfaces keep the package framework agnostic
 * while remaining compatible with the native handles.
 */
export interface FileSystemWritableLike {
  write(data: FileSystemWriteChunkType | Uint8Array): Promise<void>;
  close(): Promise<void>;
  abort?(): Promise<void>;
}

export interface FileSystemFileHandleLike {
  getFile(): Promise<File>;
  createWritable(): Promise<FileSystemWritableLike>;
}

export interface FileSystemDirectoryHandleLike {
  entries(): AsyncIterableIterator<[string, FileSystemDirectoryHandleLike | FileSystemFileHandleLike]>;
  getDirectoryHandle(name: string, options?: { create?: boolean }): Promise<FileSystemDirectoryHandleLike>;
  getFileHandle(name: string, options?: { create?: boolean }): Promise<FileSystemFileHandleLike>;
  queryPermission?(descriptor: { mode: "read" | "readwrite" }): Promise<PermissionState>;
  requestPermission?(descriptor: { mode: "read" | "readwrite" }): Promise<PermissionState>;
}

function isFileHandle(
  handle: FileSystemDirectoryHandleLike | FileSystemFileHandleLike,
): handle is FileSystemFileHandleLike {
  return typeof (handle as FileSystemFileHandleLike).getFile === "function";
}

export async function getFileSystemDirectoryPaths(
  handle: FileSystemDirectoryHandleLike,
  parent = "",
): Promise<Map<string, string>> {
  const paths = new Map<string, string>();
  for await (const [name, child] of handle.entries()) {
    if (isFileHandle(child)) {
      paths.set(parent + name, "1");
    } else {
      const nested = await getFileSystemDirectoryPaths(child, parent + name + "/");
      for (const [key, value] of nested) {
        paths.set(key, value);
      }
    }
  }
  return paths;
}

export async function createFileSystemDirectory(
  handle: FileSystemDirectoryHandleLike,
  segments: string[],
): Promise<FileSystemDirectoryHandleLike> {
  let current = handle;
  for (const segment of segments) {
    if (!segment) {
      continue;
    }
    current = await current.getDirectoryHandle(segment, { create: true });
  }
  return current;
}

export async function saveFileToFileSystemDirectory(
  handle: FileSystemDirectoryHandleLike,
  stream: FileSystemWriteChunkType,
  path: string,
  create = true,
): Promise<void> {
  const segments = path.split("/");
  const fileName = segments.pop();
  if (!fileName) {
    return;
  }
  const directory = await createFileSystemDirectory(handle, segments);
  const file = await directory.getFileHandle(fileName, { create });
  const writable = await file.createWritable();
  await writable.write(stream);
  await writable.close();
}

export async function streamFileToFileSystemDirectory(
  handle: FileSystemDirectoryHandleLike,
  reader: ReadableStreamDefaultReader<Uint8Array>,
  path: string,
  onProgress: (bytesWritten: number) => void,
  create = true,
): Promise<void> {
  const segments = path.split("/");
  const fileName = segments.pop();
  if (!fileName) {
    return;
  }
  const directory = await createFileSystemDirectory(handle, segments);
  const file = await directory.getFileHandle(fileName, { create });
  const writable = await file.createWritable();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      if (value) {
        await writable.write(value);
        onProgress(value.byteLength);
      }
    }
    await writable.close();
  } catch (error) {
    await writable.abort?.();
    throw error;
  }
}

export async function verifyFileSystemRWPermission(
  fileHandle: FileSystemDirectoryHandleLike,
): Promise<boolean> {
  const options = { mode: "readwrite" as const };
  const query = fileHandle.queryPermission;
  const request = fileHandle.requestPermission;
  if (!query || !request) {
    return true;
  }
  if ((await query.call(fileHandle, options)) === "granted") {
    return true;
  }
  return (await request.call(fileHandle, options)) === "granted";
}
