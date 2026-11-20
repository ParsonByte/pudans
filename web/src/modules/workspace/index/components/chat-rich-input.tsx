import {
  type ClipboardEvent,
  type KeyboardEvent,
  type Ref,
  useImperativeHandle,
  useRef,
  useState,
} from "react";

type ChatMentionChip = {
  id: string;
  name: string;
  url?: string;
};

export type ChatRichInputHandle = {
  focus: () => void;
  clear: () => void;
  setPlainText: (text: string) => void;
  insertMention: (chip: ChatMentionChip) => void;
  removeMention: (id: string) => void;
  removeMentionQuery: () => void;
};

type ChatRichInputProps = {
  ref?: Ref<ChatRichInputHandle>;
  placeholder: string;
  onChangeText: (text: string) => void;
  onKeyDown?: (event: KeyboardEvent<HTMLDivElement>) => void;
  onPasteFiles?: (files: File[]) => void;
};

const CHIP_CLASS =
  "mx-0.5 inline-flex max-w-full items-center gap-1  bg-primary/15 py-0.5 pr-2 pl-1 align-middle text-sm text-foreground";

const FILE_ICON =
  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' class='size-3.5 shrink-0'><path d='M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z'/><path d='M14 2v4a2 2 0 0 0 2 2h4'/></svg>";

function serialize(editor: HTMLElement): string {
  let out = "";
  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      out += node.textContent ?? "";
      return;
    }
    if (!(node instanceof HTMLElement)) {
      return;
    }
    if (node.dataset.mentionName) {
      out += node.dataset.mentionName;
      return;
    }
    if (node.tagName === "BR") {
      out += "\n";
      return;
    }
    const block = node.tagName === "DIV" || node.tagName === "P";
    if (block && out.length > 0 && !out.endsWith("\n")) {
      out += "\n";
    }
    node.childNodes.forEach(walk);
  };
  editor.childNodes.forEach(walk);
  return out;
}

function buildChip(chip: ChatMentionChip): HTMLSpanElement {
  const element = document.createElement("span");
  element.contentEditable = "false";
  element.dataset.mentionId = chip.id;
  element.dataset.mentionName = chip.name;
  element.className = CHIP_CLASS;
  if (chip.url) {
    const image = document.createElement("img");
    image.src = chip.url;
    image.alt = "";
    image.className = "size-4 shrink-0  object-cover";
    element.append(image);
  } else {
    const icon = document.createElement("span");
    icon.className = "flex shrink-0 items-center";
    icon.innerHTML = FILE_ICON;
    element.append(icon);
  }
  const label = document.createElement("span");
  label.className = "truncate";
  label.textContent = chip.name;
  element.append(label);
  return element;
}

export function ChatRichInput({
  ref,
  placeholder,
  onChangeText,
  onKeyDown,
  onPasteFiles,
}: ChatRichInputProps) {
  const editorRef = useRef<HTMLDivElement>(null);
  const [empty, setEmpty] = useState(true);

  const emit = () => {
    const editor = editorRef.current;
    if (!editor) {
      return;
    }
    const text = serialize(editor);
    setEmpty(text.trim().length === 0 && !editor.querySelector("[data-mention-id]"));
    onChangeText(text);
  };

  const placeCaretAtEnd = () => {
    const editor = editorRef.current;
    const selection = window.getSelection();
    if (!editor || !selection) {
      return;
    }
    const range = document.createRange();
    range.selectNodeContents(editor);
    range.collapse(false);
    selection.removeAllRanges();
    selection.addRange(range);
  };

  const caretRange = (): Range | null => {
    const editor = editorRef.current;
    const selection = window.getSelection();
    if (!editor || !selection || selection.rangeCount === 0) {
      return null;
    }
    const range = selection.getRangeAt(0);
    return editor.contains(range.commonAncestorContainer) ? range : null;
  };

  const removeTrailingMentionQuery = () => {
    const editor = editorRef.current;
    if (!editor) {
      return false;
    }
    let textNode: Text | null = null;
    let offset: number;
    const range = caretRange();
    if (range && range.collapsed && range.startContainer.nodeType === Node.TEXT_NODE) {
      textNode = range.startContainer as Text;
      offset = range.startOffset;
    } else {
      const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT);
      let node = walker.nextNode();
      while (node) {
        textNode = node as Text;
        node = walker.nextNode();
      }
      if (!textNode) {
        return false;
      }
      offset = textNode.data.length;
    }
    const content = textNode.data.slice(0, offset);
    const at = content.lastIndexOf("@");
    const tail = at >= 0 ? content.slice(at + 1) : "";
    if (at < 0 || /\s/.test(tail)) {
      return false;
    }
    textNode.deleteData(at, offset - at);
    const selection = window.getSelection();
    if (selection) {
      const next = document.createRange();
      next.setStart(textNode, at);
      next.collapse(true);
      selection.removeAllRanges();
      selection.addRange(next);
    }
    return true;
  };

  const removeChip = (chip: HTMLElement) => {
    let next = chip.nextSibling;
    while (next?.nodeType === Node.TEXT_NODE && (next.textContent ?? "").trim() === "") {
      const following = next.nextSibling;
      next.remove();
      next = following;
    }
    chip.remove();
    emit();
  };

  useImperativeHandle(ref, () => ({
    focus: () => {
      editorRef.current?.focus();
      if (!caretRange()) {
        placeCaretAtEnd();
      }
    },
    clear: () => {
      const editor = editorRef.current;
      if (editor) {
        editor.innerHTML = "";
        setEmpty(true);
        onChangeText("");
      }
    },
    setPlainText: (text) => {
      const editor = editorRef.current;
      if (!editor) {
        return;
      }
      editor.textContent = text;
      editor.focus();
      placeCaretAtEnd();
      emit();
    },
    insertMention: (chip) => {
      const editor = editorRef.current;
      if (!editor) {
        return;
      }
      editor.focus();
      if (!caretRange()) {
        placeCaretAtEnd();
      }
      removeTrailingMentionQuery();
      const range = caretRange();
      if (!range) {
        return;
      }
      const element = buildChip(chip);
      range.insertNode(element);
      const space = document.createTextNode(" ");
      element.after(space);
      const selection = window.getSelection();
      if (selection) {
        const next = document.createRange();
        next.setStartAfter(space);
        next.collapse(true);
        selection.removeAllRanges();
        selection.addRange(next);
      }
      emit();
    },
    removeMention: (id) => {
      const editor = editorRef.current;
      if (!editor) {
        return;
      }
      const chip = editor.querySelector<HTMLElement>(`[data-mention-id="${id}"]`);
      if (chip) {
        removeChip(chip);
      }
    },
    removeMentionQuery: () => {
      if (removeTrailingMentionQuery()) {
        emit();
      }
    },
  }));

  const handlePaste = (event: ClipboardEvent<HTMLDivElement>) => {
    const files = Array.from(event.clipboardData.files);
    if (files.length > 0 && onPasteFiles) {
      event.preventDefault();
      onPasteFiles(files);
      return;
    }
    event.preventDefault();
    const text = event.clipboardData.getData("text/plain");
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) {
      return;
    }
    const range = selection.getRangeAt(0);
    range.deleteContents();
    range.insertNode(document.createTextNode(text));
    range.collapse(false);
    selection.removeAllRanges();
    selection.addRange(range);
    emit();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Backspace") {
      const range = caretRange();
      const editor = editorRef.current;
      if (range && range.collapsed && editor) {
        let chip: HTMLElement | null = null;
        if (range.startContainer.nodeType === Node.TEXT_NODE && range.startOffset === 0) {
          const previous = range.startContainer.previousSibling;
          if (previous instanceof HTMLElement && previous.dataset.mentionId) {
            chip = previous;
          }
        } else if (range.startContainer === editor && range.startOffset > 0) {
          const previous = editor.childNodes[range.startOffset - 1];
          if (previous instanceof HTMLElement && previous.dataset.mentionId) {
            chip = previous;
          }
        }
        if (chip) {
          event.preventDefault();
          removeChip(chip);
          return;
        }
      }
    }
    onKeyDown?.(event);
  };

  return (
    <div className="relative w-full">
      <div
        ref={editorRef}
        contentEditable
        role="textbox"
        aria-multiline="true"
        aria-label={placeholder}
        data-slot="input-group-control"
        onInput={emit}
        onKeyDown={handleKeyDown}
        onPaste={handlePaste}
        className="relative max-h-64 min-h-24 w-full flex-1 overflow-y-auto overscroll-contain border-0 bg-transparent p-3 text-base break-words whitespace-pre-wrap outline-none md:text-base dark:bg-transparent"
      />
      {empty ? (
        <span className="pointer-events-none absolute inset-0 p-3 text-base text-muted-foreground md:text-base">
          {placeholder}
        </span>
      ) : null}
    </div>
  );
}
