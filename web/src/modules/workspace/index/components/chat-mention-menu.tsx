import { FileText, Paperclip } from "lucide-react";

import { Command, CommandGroup, CommandItem, CommandList } from "@/components/ui/command";

export type ChatMentionItem = {
  value: string;
  label: string;
  kind: "file" | "add";
  url?: string;
};

type ChatMentionMenuProps = {
  items: ChatMentionItem[];
  activeValue: string;
  onActiveValueChange: (value: string) => void;
  onSelect: (item: ChatMentionItem) => void;
};

export function ChatMentionMenu({
  items,
  activeValue,
  onActiveValueChange,
  onSelect,
}: ChatMentionMenuProps) {
  const files = items.filter((item) => item.kind === "file");
  const actions = items.filter((item) => item.kind === "add");

  return (
    <div className="absolute inset-x-0 bottom-full z-50 mb-2 overflow-hidden border bg-popover shadow-lg">
      <Command shouldFilter={false} value={activeValue} onValueChange={onActiveValueChange}>
        <CommandList>
          {files.length > 0 ? (
            <CommandGroup heading="附件">
              {files.map((item) => (
                <CommandItem key={item.value} value={item.value} onSelect={() => onSelect(item)}>
                  {item.url ? (
                    <img src={item.url} alt="" className="size-5 shrink-0 rounded object-cover" />
                  ) : (
                    <FileText />
                  )}
                  <span className="truncate">{item.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          ) : null}
          <CommandGroup heading="操作">
            {actions.map((item) => (
              <CommandItem key={item.value} value={item.value} onSelect={() => onSelect(item)}>
                <Paperclip />
                <span>{item.label}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </Command>
    </div>
  );
}
