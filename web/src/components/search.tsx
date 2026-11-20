import type { ComponentProps } from "react";

import { SearchIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useSearch } from "@/context/search-provider";
import { cn } from "@/lib/utils";

type SearchProps = ComponentProps<"button"> & {
  placeholder?: string;
};

export function Search({ className, placeholder = "搜索", ...props }: SearchProps) {
  const { setOpen } = useSearch();

  return (
    <Button
      {...props}
      variant="outline"
      className={cn(
        "h-8 w-8 justify-center gap-2 bg-muted/30 px-0 text-sm font-normal text-muted-foreground shadow-none hover:bg-accent sm:w-56 sm:justify-start sm:px-3 lg:w-64",
        className,
      )}
      aria-label={placeholder}
      aria-keyshortcuts="/"
      onClick={() => setOpen(true)}
    >
      <SearchIcon aria-hidden="true" className="size-4 shrink-0" />
      <span className="hidden truncate text-start sm:inline">{placeholder}</span>
      <kbd className="pointer-events-none ms-auto hidden h-5 items-center gap-0.5 rounded border bg-muted px-1.5 font-mono text-[10px] font-medium select-none sm:flex">
        <span className="text-xs leading-none">/</span>
      </kbd>
    </Button>
  );
}
