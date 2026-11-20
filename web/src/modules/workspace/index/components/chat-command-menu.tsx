import { SquareSlash } from "lucide-react";

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";

import type { ChatCommandItem } from "../lib/chat-functions";

type ChatCommandMenuProps = {
  items: readonly ChatCommandItem[];
  activeValue: string;
  loading?: boolean;
  onActiveValueChange: (value: string) => void;
  onSelect: (item: ChatCommandItem) => void;
};

export function ChatCommandMenu({
  items,
  activeValue,
  loading = false,
  onActiveValueChange,
  onSelect,
}: ChatCommandMenuProps) {
  const modes = items.filter((item) => item.kind === "mode");
  const commands = items.filter((item) => item.kind === "command");

  return (
    <div className="absolute inset-x-0 bottom-full z-50 mb-2 overflow-hidden border bg-popover shadow-lg">
      <Command shouldFilter={false} value={activeValue} onValueChange={onActiveValueChange}>
        <CommandList>
          <CommandEmpty>没有匹配的功能或命令</CommandEmpty>
          {modes.length > 0 ? (
            <CommandGroup heading="模式">
              {modes.map((item) => (
                <CommandItem key={item.value} value={item.value} onSelect={() => onSelect(item)}>
                  <item.icon />
                  <span>{item.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          ) : null}
          {loading ? (
            <div className="px-2 py-1.5 text-xs text-muted-foreground">正在加载斜杠命令…</div>
          ) : null}
          {commands.length > 0 ? (
            <CommandGroup heading="斜杠命令">
              {commands.map((item) => (
                <CommandItem key={item.value} value={item.value} onSelect={() => onSelect(item)}>
                  <SquareSlash />
                  <span className="font-mono">{item.label}</span>
                  {item.description ? (
                    <span className="truncate text-xs text-muted-foreground">
                      {item.description}
                    </span>
                  ) : null}
                  {item.argumentHint ? (
                    <span className="ms-auto shrink-0 font-mono text-[10px] text-muted-foreground">
                      {item.argumentHint}
                    </span>
                  ) : null}
                </CommandItem>
              ))}
            </CommandGroup>
          ) : null}
        </CommandList>
      </Command>
    </div>
  );
}
