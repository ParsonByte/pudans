import { Button } from "@/components/ui/button";

import { CHAT_FUNCTIONS } from "../lib/chat-functions";

type ChatSuggestionsProps = {
  onSelect: (label: string) => void;
};

export function ChatSuggestions({ onSelect }: ChatSuggestionsProps) {
  return (
    <div className="flex flex-wrap items-center justify-center gap-2 pt-2">
      {CHAT_FUNCTIONS.map((item) => (
        <Button
          key={item.label}
          variant="outline"
          size="sm"
          className="rounded-full"
          onClick={() => onSelect(item.label)}
        >
          <item.icon />
          {item.label}
        </Button>
      ))}
    </div>
  );
}
