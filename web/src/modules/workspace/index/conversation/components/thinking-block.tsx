import { useState } from "react";

import { Brain, ChevronDown, Loader2 } from "lucide-react";

import { MarkdownContent } from "@/components/content/markdown";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";

type ThinkingBlockProps = {
  reasoning: string;
  streaming?: boolean;
};

export function ThinkingBlock({ reasoning, streaming = false }: ThinkingBlockProps) {
  const [open, setOpen] = useState(streaming);
  if (!reasoning.trim() && !streaming) {
    return null;
  }
  return (
    <Collapsible open={open} onOpenChange={setOpen} className="w-full">
      <CollapsibleTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-7 gap-1.5 px-2 text-xs text-muted-foreground hover:text-foreground"
        >
          <Brain className="size-3.5" />
          {streaming ? "思考中" : "思考过程"}
          {streaming ? (
            <Loader2 className="size-3 animate-spin" />
          ) : (
            <ChevronDown className={cn("size-3.5 transition-transform", open && "rotate-180")} />
          )}
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-1 max-h-80 overflow-y-auto border-s-2 border-border ps-3">
          {reasoning.trim() ? (
            <MarkdownContent
              content={reasoning}
              className="text-xs leading-6 text-muted-foreground"
            />
          ) : (
            <span className="text-xs leading-6 whitespace-pre-wrap text-muted-foreground">
              模型正在思考…
            </span>
          )}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
