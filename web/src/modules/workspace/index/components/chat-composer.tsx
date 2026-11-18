import {
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent,
  useEffect,
  useRef,
  useState,
} from "react";

import { Mic, Paperclip, SendHorizontal } from "lucide-react";
import { toast } from "sonner";

import { InputGroup, InputGroupAddon, InputGroupButton } from "@/components/ui/input-group";

import { CHAT_FUNCTIONS, CHAT_MODES, type ChatMode } from "../lib/chat-functions";
import { type ChatAttachment, ChatAttachments } from "./chat-attachments";
import { ChatCommandMenu } from "./chat-command-menu";
import { type ChatMentionItem, ChatMentionMenu } from "./chat-mention-menu";
import { ChatRichInput, type ChatRichInputHandle } from "./chat-rich-input";
import { ChatSuggestions } from "./chat-suggestions";

const ADD_ATTACHMENT = "__add_attachment__";

type ChatComposerProps = {
  placeholder?: string;
  showSuggestions?: boolean;
};

export function ChatComposer({
  placeholder = "输入 / 唤起功能菜单，@ 引用附件",
  showSuggestions = true,
}: ChatComposerProps) {
  const [prompt, setPrompt] = useState("");
  const [files, setFiles] = useState<ChatAttachment[]>([]);
  const [mode, setMode] = useState<ChatMode>("fast");
  const [activeFunction, setActiveFunction] = useState("");
  const [activeMention, setActiveMention] = useState("");
  const [dismissedMentionAt, setDismissedMentionAt] = useState<number | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const richInputRef = useRef<ChatRichInputHandle>(null);
  const filesRef = useRef<ChatAttachment[]>([]);

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
    },
    [],
  );

  const canSend = prompt.trim().length > 0 || files.length > 0;
  const slashOpen = prompt.startsWith("/");
  const functionQuery = slashOpen ? prompt.slice(1).trim().toLowerCase() : "";
  const matchedFunctions = CHAT_FUNCTIONS.filter((item) =>
    item.label.toLowerCase().includes(functionQuery),
  );
  const activeFunctionValue = matchedFunctions.some((item) => item.label === activeFunction)
    ? activeFunction
    : (matchedFunctions[0]?.label ?? "");

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

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSend) {
      return;
    }
    toast.info("对话功能即将上线。");
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
          };
        }),
      ];
    });
  };

  const onAttach = (event: ChangeEvent<HTMLInputElement>) => {
    addFiles(Array.from(event.target.files ?? []));
    event.target.value = "";
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

  const applyFunction = (label: string) => {
    richInputRef.current?.setPlainText(`${label}：`);
    setActiveFunction("");
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
        if (matchedFunctions.length === 0) {
          return;
        }
        const index = Math.max(
          0,
          matchedFunctions.findIndex((item) => item.label === activeFunctionValue),
        );
        const nextIndex =
          event.key === "ArrowDown"
            ? (index + 1) % matchedFunctions.length
            : (index - 1 + matchedFunctions.length) % matchedFunctions.length;
        setActiveFunction(matchedFunctions[nextIndex].label);
        return;
      }
      if (event.key === "Enter" && activeFunctionValue) {
        event.preventDefault();
        applyFunction(activeFunctionValue);
        return;
      }
      if (event.key === "Escape") {
        event.preventDefault();
        richInputRef.current?.clear();
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
          <InputGroup className="rounded-2xl">
            {files.length > 0 ? <ChatAttachments files={files} onRemove={removeFile} /> : null}
            <ChatRichInput
              ref={richInputRef}
              placeholder={placeholder}
              onChangeText={setPrompt}
              onKeyDown={handleKeyDown}
              onPasteFiles={(pastedFiles) => addFiles(pastedFiles, true)}
            />
            <InputGroupAddon align="block-end">
              <div className="flex items-center gap-1">
                {CHAT_MODES.map((item) => (
                  <InputGroupButton
                    key={item.value}
                    size="sm"
                    variant="ghost"
                    aria-pressed={mode === item.value}
                    className={
                      mode === item.value
                        ? "bg-muted text-foreground"
                        : "text-muted-foreground hover:text-foreground"
                    }
                    onClick={() => setMode(item.value)}
                  >
                    <item.icon />
                    {item.label}
                  </InputGroupButton>
                ))}
              </div>
              <div className="ms-auto flex items-center gap-1">
                <InputGroupButton size="icon-sm" variant="ghost" aria-label="语音输入">
                  <Mic />
                </InputGroupButton>
                <InputGroupButton
                  size="icon-sm"
                  variant="outline"
                  className="rounded-full"
                  aria-label="添加附件"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Paperclip />
                </InputGroupButton>
                <InputGroupButton
                  type="submit"
                  size="icon-sm"
                  variant="default"
                  className="rounded-full"
                  disabled={!canSend}
                  aria-label="发送"
                >
                  <SendHorizontal />
                </InputGroupButton>
              </div>
            </InputGroupAddon>
          </InputGroup>
        </form>
        {slashOpen ? (
          <ChatCommandMenu
            functions={matchedFunctions}
            activeValue={activeFunctionValue}
            onActiveValueChange={setActiveFunction}
            onSelect={applyFunction}
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
      {showSuggestions ? <ChatSuggestions onSelect={applyFunction} /> : null}
    </div>
  );
}
