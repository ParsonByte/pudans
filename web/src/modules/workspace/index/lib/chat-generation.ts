import {
  type ChatArtifactInput,
  type ChatGeneration,
  type ChatGenerationKind,
  type ChatGenerationOutput,
  type ChatMessage,
  appendConversationArtifact,
  generateDocument,
  generateImage,
  generateVideo,
  uploadChatAttachment,
  waitForChatGeneration,
} from "./chat-api";

export type ChatGenerationProgress = {
  kind: ChatGenerationKind;
  prompt: string;
  stage: string;
  progress: number;
};

export type ChatGenerationOptions = {
  conversationId: number;
  kind: ChatGenerationKind;
  prompt: string;
  routeGroup?: string;
  format?: "MARKDOWN" | "HTML";
  onProgress?: (progress: ChatGenerationProgress) => void;
};

const TITLE_LIMIT = 40;

export async function createChatArtifact(options: ChatGenerationOptions): Promise<ChatMessage> {
  const { conversationId, kind, prompt, routeGroup, format, onProgress } = options;
  const report = (stage: string, progress: number) =>
    onProgress?.({ kind, prompt, stage, progress });
  if (kind === "DOCUMENT") {
    report("正在生成文档", 30);
    const document = await generateDocument({
      title: documentTitle(prompt),
      prompt,
      format: format ?? "MARKDOWN",
      routeGroup,
    });
    report("正在保存文档", 80);
    const message = await appendConversationArtifact(conversationId, {
      kind: "DOCUMENT",
      prompt,
      requestId: document.requestId,
      fileId: document.fileId,
      name: document.name,
      mimeType: document.mimeType,
      size: document.size,
    });
    report("生成完成", 100);
    return message;
  }

  report("正在提交生成任务", 10);
  let generation =
    kind === "IMAGE"
      ? await generateImage(prompt, routeGroup)
      : await generateVideo(prompt, routeGroup);
  if (generation.status === "PENDING" || generation.status === "RUNNING") {
    report("模型生成中", 45);
    generation = await waitForChatGeneration(generation.requestId, (current) => {
      report(current.status === "RUNNING" ? "模型生成中" : "排队等待中", 55);
    });
  }
  if (generation.status !== "SUCCEEDED") {
    throw new Error(generation.errorMessage || "生成失败，请稍后重试。");
  }
  const artifact = await resolveArtifact(generation, kind, report);
  report("正在保存生成物", 90);
  const message = await appendConversationArtifact(conversationId, {
    ...artifact,
    kind,
    prompt,
    requestId: generation.requestId,
  });
  report("生成完成", 100);
  return message;
}

type ArtifactFile = Omit<ChatArtifactInput, "kind" | "prompt" | "requestId">;

async function resolveArtifact(
  generation: ChatGeneration,
  kind: ChatGenerationKind,
  report: (stage: string, progress: number) => void,
): Promise<ArtifactFile> {
  const outputs = generation.outputs ?? [];
  const expectedType = kind === "IMAGE" ? "image" : "video";
  const output =
    outputs.find(
      (item) => item.type === expectedType && Boolean(item.fileId || item.url || item.data),
    ) ?? outputs.find((item) => Boolean(item.fileId || item.url || item.data));
  if (!output) {
    throw new Error("生成结果为空，请稍后重试。");
  }
  return artifactFromOutput(output, kind, report);
}

async function artifactFromOutput(
  output: ChatGenerationOutput,
  kind: ChatGenerationKind,
  report: (stage: string, progress: number) => void,
): Promise<ArtifactFile> {
  if (output.fileId) {
    return {
      fileId: output.fileId,
      name: output.filename || outputName(kind, output.mediaType),
      mimeType: output.mediaType,
    };
  }
  if (output.url) {
    return {
      url: output.url,
      name: output.filename || outputName(kind, output.mediaType),
      mimeType: output.mediaType,
    };
  }
  const mediaType = output.mediaType || (kind === "IMAGE" ? "image/png" : "video/mp4");
  const file = base64File(output.data ?? "", outputName(kind, mediaType), mediaType);
  report("正在上传生成物", 75);
  const uploaded = await uploadChatAttachment(file, (percent) =>
    report("正在上传生成物", 75 + Math.round(percent * 0.15)),
  );
  return {
    fileId: uploaded.fileId,
    name: uploaded.name,
    mimeType: uploaded.contentType,
    size: uploaded.size,
  };
}

function base64File(data: string, name: string, mediaType: string): File {
  let binary: string;
  try {
    binary = atob(data);
  } catch {
    throw new Error("生成结果无法解析，请稍后重试。");
  }
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return new File([bytes], name, { type: mediaType });
}

function outputName(kind: ChatGenerationKind, mediaType?: string): string {
  const extension = fileExtension(mediaType, kind);
  return `${kind === "IMAGE" ? "生成图片" : "生成视频"}.${extension}`;
}

function fileExtension(mediaType: string | undefined, kind: ChatGenerationKind): string {
  switch (mediaType) {
    case "image/png":
      return "png";
    case "image/webp":
      return "webp";
    case "image/gif":
      return "gif";
    case "image/jpeg":
      return "jpg";
    case "video/webm":
      return "webm";
    case "video/mp4":
      return "mp4";
    default:
      return kind === "IMAGE" ? "png" : "mp4";
  }
}

function documentTitle(prompt: string): string {
  const firstLine = prompt.trim().split(/\r?\n/, 1)[0] ?? "";
  return firstLine.length <= TITLE_LIMIT ? firstLine : firstLine.slice(0, TITLE_LIMIT);
}
