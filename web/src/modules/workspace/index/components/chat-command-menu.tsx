import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";

import type { ChatFunction } from "../lib/chat-functions";

type ChatCommandMenuProps = {
  functions: readonly ChatFunction[];
  activeValue: string;
  onActiveValueChange: (value: string) => void;
  onSelect: (label: string) => void;
};

export function ChatCommandMenu({
  functions,
  activeValue,
  onActiveValueChange,
  onSelect,
}: ChatCommandMenuProps) {
  return (
    <div className="absolute inset-x-0 bottom-full z-50 mb-2 overflow-hidden rounded-xl border bg-popover shadow-lg">
      <Command shouldFilter={false} value={activeValue} onValueChange={onActiveValueChange}>
        <CommandList>
          <CommandEmpty>没有匹配的功能</CommandEmpty>
          <CommandGroup heading="功能">
            {functions.map((item) => (
              <CommandItem
                key={item.label}
                value={item.label}
                onSelect={() => onSelect(item.label)}
              >
                <item.icon />
                <span>{item.label}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </Command>
    </div>
  );
}
