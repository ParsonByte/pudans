import {
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent,
  useEffect,
  useRef,
  useState,
} from "react";

import {
  FileText,
  Image as ImageIcon,
  Loader2,
  Mic,
  Paperclip,
  SendHorizontal,
  Settings2,
  Sparkles,
  Square,
  Video,
  X,
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { InputGroup, InputGroupAddon, InputGroupButton } from "@/components/ui/input-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { KnowledgeBaseItem } from "@/modules/workspace/ai/knowledge/lib/api";

import type {
  ChatAgent,
  ChatAttachmentRef,
  ChatGenerationKind,
  ChatMode,
  ChatModel,
  ChatRouteGroup,
  ChatSlashCommand,
} from "../lib/chat-api";
import { resolveChatError, uploadChatAttachment } from "../lib/chat-api";
import {
  type ChatCommandItem,
  type ChatRequestBody,
  chatFunctionsForModes,
  chatModeIcon,
  chatModeLabel,
  chatRouteGroupsForMode,
  parseChatCommand,
} from "../lib/chat-functions";
import { type ChatAttachment, ChatAttachments } from "./chat-attachments";
import { ChatCommandMenu } from "./chat-command-menu";
import { type ChatMentionItem, ChatMentionMenu } from "./chat-mention-menu";
import { ChatRichInput, type ChatRichInputHandle } from "./chat-rich-input";
import { ChatSettingsPopover } from "./chat-settings-popover";

const ADD_ATTACHMENT = "__add_attachment__";

const MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024;

const AUDIO_MIME_TYPES = [
  "audio/webm;codecs=opus",
  "audio/webm",
  "audio/ogg;codecs=opus",
  "audio/mp4",
  "audio/aac",
  "audio/mpeg",
] as const;

type ChatComposerProps = {
  modes: ChatMode[];
  mode: string | undefined;
  onModeChange: (mode: string) => void;
  agents: ChatAgent[];
  agentsLoading: boolean;
  agent?: string;
  agentLocked?: boolean;
  onAgentChange?: (agent: string | undefined) => void;
  models: ChatModel[];
  modelsLoading: boolean;
  model?: string;
  onModelChange: (model: string | undefined) => void;
  routeGroups: ChatRouteGroup[];
  routeGroupsLoading: boolean;
  routeGroup?: string;
  onRouteGroupChange: (routeGroup: string | undefined) => void;
  kbCodes: string[];
  knowledgeBases: KnowledgeBaseItem[];
  knowledgeLoading: boolean;
  knowledgeError?: string;
  onRetryKnowledge?: () => void;
  onKbCodesChange: (codes: string[]) => void;
  commands: ChatSlashCommand[];
  commandsLoading?: boolean;
  onSend: (request: ChatRequestBody) => Promise<void> | void;
  onGenerate: (input: {
    kind: ChatGenerationKind;
    prompt: string;
    format?: "MARKDOWN" | "HTML";
  }) => Promise<void> | void;
  sending?: boolean;
  placeholder?: string;
};

export function ChatComposer({
  modes,
  mode,
  onModeChange,
  agents,
  agentsLoading,
  agent,
  agentLocked = false,
  onAgentChange,
  models,
  modelsLoading,
  model,
  onModelChange,
  routeGroups,
  routeGroupsLoading,
  routeGroup,
  onRouteGroupChange,
  kbCodes,
  knowledgeBases,
  knowledgeLoading,
  knowledgeError,
  onRetryKnowledge,
  onKbCodesChange,
  commands,
  commandsLoading = false,
  onSend,
  onGenerate,
  sending = false,
  placeholder = "输入 / 唤起模式菜单，@ 引用附件",
}: ChatComposerProps) {
  const [prompt, setPrompt] = useState("");
  const [files, setFiles] = useState<ChatAttachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [generationKind, setGenerationKind] = useState<ChatGenerationKind | null>(null);
  const [documentFormat, setDocumentFormat] = useState<"MARKDOWN" | "HTML">("MARKDOWN");
  const [activeCommand, setActiveCommand] = useState("");
  const [activeMention, setActiveMention] = useState("");
  const [dismissedMentionAt, setDismissedMentionAt] = useState<number | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const richInputRef = useRef<ChatRichInputHandle>(null);
  const filesRef = useRef<ChatAttachment[]>([]);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const discardRecordingRef = useRef(false);
  const startingRef = useRef(false);
  const canRecord = supportsAudioRecording();
  const functions = chatFunctionsForModes(modes);

  useEffect(() => {
    filesRef.current = files;
  }, [files]);

  useEffect(
    () => () => {
      filesRef.current.forEach((file) => {
        if (file.url) {
          URL.revokeObjectURL(file.url);
        }
      });
      const recorder = recorderRef.current;
      if (recorder) {
        recorder.ondataavailable = null;
        recorder.onstop = null;
        recorder.onerror = null;
        if (recorder.state !== "inactive") {
          recorder.stop();
        }
      }
      streamRef.current?.getTracks().forEach((track) => track.stop());
    },
    [],
  );

  useEffect(() => {
    if (!recording) {
      return;
    }
    const timer = window.setInterval(() => {
      setRecordingSeconds((current) => current + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [recording]);

  const canSend = prompt.trim().length > 0 && !sending && !uploading && !recording;
  const slashQuery = prompt.startsWith("/") ? prompt.slice(1) : "";
  const slashOpen = prompt.startsWith("/") && !/\s/.test(slashQuery);
  const normalizedQuery = slashQuery.trim().toLowerCase();
  const commandMenuItems: ChatCommandItem[] = slashOpen
    ? [
        ...functions
          .filter((item) => item.label.toLowerCase().includes(normalizedQuery))
          .map((item) => ({
            kind: "mode" as const,
            value: `mode:${item.mode}`,
            label: item.label,
            mode: item.mode,
            icon: item.icon,
          })),
        ...commands
          .filter(
            (item) =>
              item.name.toLowerCase().includes(normalizedQuery) ||
              (item.description ?? "").toLowerCase().includes(normalizedQuery),
          )
          .map((item) => ({
            kind: "command" as const,
            value: `command:${item.name}`,
            label: `/${item.name}`,
            command: item.name,
            description: item.description,
            argumentHint: item.argumentHint,
          })),
      ]
    : [];
  const activeCommandValue = commandMenuItems.some((item) => item.value === activeCommand)
    ? activeCommand
    : (commandMenuItems[0]?.value ?? "");

  const mentionStart = prompt.lastIndexOf("@");
  const mentionQuery = mentionStart >= 0 ? prompt.slice(mentionStart + 1) : "";
  const mentionBoundary = mentionStart === 0 || !/[A-Za-z0-9_]/.test(prompt[mentionStart - 1]);
  const matchedMentionFiles = files.filter((file) =>
    file.name.toLowerCase().includes(mentionQuery.toLowerCase()),
  );
  const mentionDismissed =
    dismissedMentionAt !== null &&
    dismissedMentionAt === mentionStart &&
    prompt[dismissedMentionAt] === "@";
  const mentionOpen =
    !slashOpen &&
    generationKind === null &&
    mentionStart >= 0 &&
    mentionBoundary &&
    !mentionDismissed &&
    (mentionQuery.length === 0 || matchedMentionFiles.length > 0);
  const mentionFiles = mentionQuery.length === 0 ? files : matchedMentionFiles;
  const mentionItems: ChatMentionItem[] = mentionOpen
    ? [
        ...mentionFiles.map((file) => ({
          value: file.id,
          label: file.name,
          kind: "file" as const,
          url: file.url,
        })),
        { value: ADD_ATTACHMENT, label: "添加附件", kind: "add" as const },
      ]
    : [];
  const activeMentionValue = mentionItems.some((item) => item.value === activeMention)
    ? activeMention
    : (mentionItems[0]?.value ?? "");
  const effectivePlaceholder = generationKind
    ? generationKind === "IMAGE"
      ? "描述要生成的图片，发送后开始生成"
      : generationKind === "VIDEO"
        ? "描述要生成的视频，发送后开始生成"
        : "描述要生成的文档，发送后开始生成"
    : placeholder;

  const clearComposer = () => {
    richInputRef.current?.clear();
    setPrompt("");
    setActiveCommand("");
    filesRef.current.forEach((file) => {
      if (file.url) {
        URL.revokeObjectURL(file.url);
      }
    });
    setFiles([]);
  };

  const changeMode = (nextMode: string) => {
    onModeChange(nextMode);
    if (
      routeGroup &&
      onRouteGroupChange &&
      !chatRouteGroupsForMode(routeGroups, nextMode).some((item) => item.name === routeGroup)
    ) {
      onRouteGroupChange(undefined);
    }
  };

  const send = async () => {
    if (!canSend) {
      return;
    }
    const trimmed = prompt.trim();
    if (generationKind) {
      if (filesRef.current.length > 0) {
        toast.error("生成模式不使用附件，请先移除附件。");
        return;
      }
      try {
        await onGenerate({
          kind: generationKind,
          prompt: trimmed,
          format: generationKind === "DOCUMENT" ? documentFormat : undefined,
        });
        clearComposer();
        setGenerationKind(null);
      } catch (error) {
        toast.error(resolveChatError(error));
      }
      return;
    }
    setUploading(true);
    try {
      const attachments: ChatAttachmentRef[] = [];
      for (const file of files) {
        if (!file.file) {
          continue;
        }
        if (file.file.size > MAX_ATTACHMENT_BYTES) {
          toast.error(`${file.name} 超过 25 MB，无法上传。`);
          return;
        }
        attachments.push(
          await uploadChatAttachment(file.file, (percent) => {
            setFiles((current) =>
              current.map((item) => (item.id === file.id ? { ...item, progress: percent } : item)),
            );
          }),
        );
      }
      await onSend({ prompt: trimmed, attachments, ...parseChatCommand(trimmed, commands) });
      clearComposer();
    } catch (error) {
      toast.error(resolveChatError(error));
    } finally {
      setUploading(false);
    }
  };

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void send();
  };

  const addFiles = (incoming: File[], pasted = false) => {
    if (incoming.length === 0) {
      return;
    }
    setFiles((current) => {
      let imageIndex = current.filter((file) => file.url).length;
      return [
        ...current,
        ...incoming.map((file) => {
          const isImage = file.type.startsWith("image/");
          const extension =
            file.type === "image/jpeg"
              ? "jpg"
              : file.type === "image/webp"
                ? "webp"
                : file.type === "image/gif"
                  ? "gif"
                  : "png";
          const name =
            pasted && isImage
              ? `图片${++imageIndex}.${extension}`
              : file.name || (isImage ? "图片.png" : "附件");
          return {
            id: crypto.randomUUID(),
            name,
            url: isImage ? URL.createObjectURL(file) : undefined,
            file,
          };
        }),
      ];
    });
  };

  const onAttach = (event: ChangeEvent<HTMLInputElement>) => {
    addFiles(Array.from(event.target.files ?? []));
    event.target.value = "";
  };

  const stopRecording = () => {
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== "inactive") {
      recorder.stop();
    }
  };

  const startRecording = async () => {
    if (recorderRef.current || startingRef.current || sending || uploading) {
      return;
    }
    if (!canRecord) {
      toast.error("当前浏览器不支持录音。");
      return;
    }
    startingRef.current = true;
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
      startingRef.current = false;
      toast.error(microphoneErrorMessage(error));
      return;
    }
    try {
      const mimeType = preferredAudioMimeType();
      const recorder = mimeType
        ? new MediaRecorder(stream, { mimeType })
        : new MediaRecorder(stream);
      chunksRef.current = [];
      discardRecordingRef.current = false;
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
        recorderRef.current = null;
        setRecording(false);
        const recordedType = recorder.mimeType || mimeType || "audio/webm";
        const chunks = chunksRef.current;
        chunksRef.current = [];
        const discarded = discardRecordingRef.current;
        discardRecordingRef.current = false;
        if (discarded) {
          return;
        }
        const blob = new Blob(chunks, { type: recordedType });
        if (blob.size === 0) {
          toast.error("没有录到音频，请重试。");
          return;
        }
        addFiles([new File([blob], audioFileName(recordedType), { type: recordedType })]);
        toast.success("录音已加入附件。");
      };
      recorder.onerror = () => {
        discardRecordingRef.current = true;
        toast.error("录音失败，请重试。");
        if (recorder.state !== "inactive") {
          recorder.stop();
        }
      };
      recorder.start();
      recorderRef.current = recorder;
      streamRef.current = stream;
      setRecordingSeconds(0);
      setRecording(true);
      startingRef.current = false;
    } catch (error) {
      stream.getTracks().forEach((track) => track.stop());
      startingRef.current = false;
      toast.error(microphoneErrorMessage(error));
    }
  };

  const removeFile = (id: string) => {
    richInputRef.current?.removeMention(id);
    setFiles((current) => {
      const target = current.find((file) => file.id === id);
      if (target?.url) {
        URL.revokeObjectURL(target.url);
      }
      return current.filter((file) => file.id !== id);
    });
  };

  const applyFunction = (item: Extract<ChatCommandItem, { kind: "mode" }>) => {
    changeMode(item.mode);
    richInputRef.current?.clear();
    setPrompt("");
    setActiveCommand("");
  };

  const applyCommand = (item: Extract<ChatCommandItem, { kind: "command" }>) => {
    const text = `/${item.command} `;
    richInputRef.current?.setPlainText(text);
    setPrompt(text);
    setActiveCommand("");
  };

  const selectCommandItem = (item: ChatCommandItem) => {
    if (item.kind === "mode") {
      applyFunction(item);
      return;
    }
    applyCommand(item);
  };

  const selectMention = (item: ChatMentionItem) => {
    setDismissedMentionAt(null);
    if (item.kind === "add") {
      richInputRef.current?.removeMentionQuery();
      setActiveMention("");
      fileInputRef.current?.click();
      return;
    }
    const file = files.find((entry) => entry.id === item.value);
    if (file) {
      richInputRef.current?.insertMention({ id: file.id, name: file.name, url: file.url });
    }
    setActiveMention("");
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (slashOpen) {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        if (commandMenuItems.length === 0) {
          return;
        }
        const index = Math.max(
          0,
          commandMenuItems.findIndex((item) => item.value === activeCommandValue),
        );
        const nextIndex =
          event.key === "ArrowDown"
            ? (index + 1) % commandMenuItems.length
            : (index - 1 + commandMenuItems.length) % commandMenuItems.length;
        setActiveCommand(commandMenuItems[nextIndex].value);
        return;
      }
      if (event.key === "Enter" && activeCommandValue) {
        event.preventDefault();
        const item = commandMenuItems.find((entry) => entry.value === activeCommandValue);
        if (item) {
          selectCommandItem(item);
        }
        return;
      }
      if (event.key === "Escape") {
        event.preventDefault();
        richInputRef.current?.clear();
        setPrompt("");
        return;
      }
    }
    if (mentionOpen) {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const index = Math.max(
          0,
          mentionItems.findIndex((item) => item.value === activeMentionValue),
        );
        const nextIndex =
          event.key === "ArrowDown"
            ? (index + 1) % mentionItems.length
            : (index - 1 + mentionItems.length) % mentionItems.length;
        setActiveMention(mentionItems[nextIndex].value);
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        const item = mentionItems.find((entry) => entry.value === activeMentionValue);
        if (item) {
          selectMention(item);
        }
        return;
      }
      if (event.key === "Escape") {
        event.preventDefault();
        setDismissedMentionAt(mentionStart);
        return;
      }
    }
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      formRef.current?.requestSubmit();
    }
  };

  return (
    <div className="space-y-2">
      {generationKind ? (
        <div className="flex flex-wrap items-center gap-2 border border-dashed px-3 py-2 text-xs">
          <Badge variant="secondary">
            {generationKind === "IMAGE" ? (
              <ImageIcon />
            ) : generationKind === "VIDEO" ? (
              <Video />
            ) : (
              <FileText />
            )}
            {generationKind === "IMAGE"
              ? "生成图片"
              : generationKind === "VIDEO"
                ? "生成视频"
                : "生成文档"}
          </Badge>
          {generationKind === "DOCUMENT" ? (
            <Select
              value={documentFormat}
              onValueChange={(value) => setDocumentFormat(value as "MARKDOWN" | "HTML")}
            >
              <SelectTrigger size="sm" className="w-28" aria-label="文档格式">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="MARKDOWN">Markdown</SelectItem>
                <SelectItem value="HTML">HTML</SelectItem>
              </SelectContent>
            </Select>
          ) : null}
          <span className="text-muted-foreground">生成结果会保存到当前会话。</span>
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            className="ms-auto"
            aria-label="退出生成模式"
            onClick={() => setGenerationKind(null)}
          >
            <X />
          </Button>
        </div>
      ) : null}
      <div className="relative">
        <form ref={formRef} onSubmit={onSubmit}>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            className="sr-only"
            tabIndex={-1}
            onChange={onAttach}
          />
          <InputGroup className="">
            {files.length > 0 ? <ChatAttachments files={files} onRemove={removeFile} /> : null}
            <ChatRichInput
              ref={richInputRef}
              placeholder={effectivePlaceholder}
              onChangeText={setPrompt}
              onKeyDown={handleKeyDown}
              onPasteFiles={(pastedFiles) => addFiles(pastedFiles, true)}
            />
            <InputGroupAddon align="block-end">
              <div className="flex items-center gap-1">
                {modes.map((item) => {
                  const Icon = chatModeIcon(item.name);
                  return (
                    <InputGroupButton
                      key={item.name}
                      size="sm"
                      variant="ghost"
                      aria-pressed={mode === item.name}
                      className={
                        mode === item.name
                          ? "bg-muted text-foreground"
                          : "text-muted-foreground hover:text-foreground"
                      }
                      onClick={() => changeMode(item.name)}
                    >
                      <Icon />
                      {chatModeLabel(item)}
                    </InputGroupButton>
                  );
                })}
              </div>
              <div className="ms-auto flex items-center gap-1">
                <ChatSettingsPopover
                  mode={mode}
                  agent={agent}
                  agentLocked={agentLocked}
                  agents={agents}
                  agentsLoading={agentsLoading}
                  onAgentChange={onAgentChange}
                  model={model}
                  models={models}
                  modelsLoading={modelsLoading}
                  onModelChange={onModelChange}
                  routeGroup={routeGroup}
                  routeGroups={routeGroups}
                  routeGroupsLoading={routeGroupsLoading}
                  onRouteGroupChange={onRouteGroupChange}
                  kbCodes={kbCodes}
                  knowledgeBases={knowledgeBases}
                  knowledgeLoading={knowledgeLoading}
                  knowledgeError={knowledgeError}
                  onRetryKnowledge={onRetryKnowledge}
                  onKbCodesChange={onKbCodesChange}
                  trigger={
                    <InputGroupButton size="icon-sm" variant="ghost" aria-label="对话设置">
                      <Settings2 />
                    </InputGroupButton>
                  }
                />
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <InputGroupButton size="icon-sm" variant="ghost" aria-label="生成内容">
                      <Sparkles />
                    </InputGroupButton>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-40">
                    <DropdownMenuItem onSelect={() => setGenerationKind("IMAGE")}>
                      <ImageIcon />
                      生成图片
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => setGenerationKind("VIDEO")}>
                      <Video />
                      生成视频
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => setGenerationKind("DOCUMENT")}>
                      <FileText />
                      生成文档
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
                {recording ? (
                  <span className="flex items-center gap-1.5 px-1 text-xs text-destructive tabular-nums">
                    <span className="size-2 animate-pulse bg-destructive" />
                    {formatRecordingTime(recordingSeconds)}
                  </span>
                ) : null}
                {canRecord ? (
                  <InputGroupButton
                    size="icon-sm"
                    variant={recording ? "destructive" : "ghost"}
                    aria-label={recording ? "停止录音" : "语音输入"}
                    aria-pressed={recording}
                    disabled={sending || uploading}
                    onClick={() => {
                      if (recording) {
                        stopRecording();
                        return;
                      }
                      void startRecording();
                    }}
                  >
                    {recording ? <Square className="fill-current" /> : <Mic />}
                  </InputGroupButton>
                ) : null}
                <InputGroupButton
                  size="icon-sm"
                  variant="outline"
                  className=""
                  aria-label="添加附件"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Paperclip />
                </InputGroupButton>
                <InputGroupButton
                  type="submit"
                  size="icon-sm"
                  variant="default"
                  className=""
                  disabled={!canSend}
                  aria-label="发送"
                >
                  {sending || uploading ? <Loader2 className="animate-spin" /> : <SendHorizontal />}
                </InputGroupButton>
              </div>
            </InputGroupAddon>
          </InputGroup>
        </form>
        {slashOpen ? (
          <ChatCommandMenu
            items={commandMenuItems}
            activeValue={activeCommandValue}
            loading={commandsLoading}
            onActiveValueChange={setActiveCommand}
            onSelect={selectCommandItem}
          />
        ) : mentionOpen ? (
          <ChatMentionMenu
            items={mentionItems}
            activeValue={activeMentionValue}
            onActiveValueChange={setActiveMention}
            onSelect={selectMention}
          />
        ) : null}
      </div>
    </div>
  );
}

function supportsAudioRecording(): boolean {
  return (
    typeof navigator !== "undefined" &&
    Boolean(navigator.mediaDevices?.getUserMedia) &&
    typeof MediaRecorder !== "undefined"
  );
}

function preferredAudioMimeType(): string | undefined {
  if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
    return undefined;
  }
  return AUDIO_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type));
}

function audioFileName(mimeType: string): string {
  const stamp = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  const time = `${pad(stamp.getHours())}-${pad(stamp.getMinutes())}-${pad(stamp.getSeconds())}`;
  return `录音 ${time}.${audioExtension(mimeType)}`;
}

function audioExtension(mimeType: string): string {
  const normalized = mimeType.toLowerCase();
  if (normalized.includes("ogg")) {
    return "ogg";
  }
  if (normalized.includes("mp4")) {
    return "m4a";
  }
  if (normalized.includes("mpeg")) {
    return "mp3";
  }
  if (normalized.includes("wav")) {
    return "wav";
  }
  return "webm";
}

function formatRecordingTime(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function microphoneErrorMessage(error: unknown): string {
  if (typeof DOMException !== "undefined" && error instanceof DOMException) {
    switch (error.name) {
      case "NotAllowedError":
      case "PermissionDeniedError":
      case "SecurityError":
        return "麦克风权限被拒绝，请在浏览器设置中允许访问后重试。";
      case "NotFoundError":
      case "DevicesNotFoundError":
        return "未检测到可用的麦克风。";
      case "NotReadableError":
      case "TrackStartError":
        return "麦克风被其他应用占用，无法录音。";
      case "OverconstrainedError":
        return "当前麦克风不满足录音要求。";
      default:
        return error.message ? `无法录音：${error.message}` : "无法录音，请稍后重试。";
    }
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return "无法录音，请稍后重试。";
}
