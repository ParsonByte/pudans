import { useCallback } from "react";

import { ArrowRight, ChevronRight, Laptop, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useRouter } from "next/navigation";

import type { NavGroup } from "@/components/layout/types";
import {
  Command,
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useSearch } from "@/context/search-provider";

type CommandMenuProps = {
  navGroups?: NavGroup[];
};

export function CommandMenu({ navGroups = [] }: CommandMenuProps) {
  const router = useRouter();
  const { setTheme } = useTheme();
  const { open, setOpen } = useSearch();

  const runCommand = useCallback(
    (command: () => unknown) => {
      setOpen(false);
      command();
    },
    [setOpen],
  );

  return (
    <CommandDialog modal open={open} onOpenChange={setOpen} className="sm:max-w-lg">
      <Command>
        <CommandInput placeholder="输入命令或搜索..." />
        <CommandList>
          <ScrollArea type="hover" className="max-h-72 pe-1">
            <CommandEmpty>未找到结果。</CommandEmpty>
            {navGroups.map((group) => (
              <CommandGroup key={group.title} heading={group.title}>
                {group.items.map((navItem, index) => {
                  if (navItem.url) {
                    return (
                      <CommandItem
                        key={`${navItem.url}-${index}`}
                        value={navItem.title}
                        onSelect={() => {
                          runCommand(() => router.push(navItem.url));
                        }}
                      >
                        <div className="flex size-4 items-center justify-center">
                          <ArrowRight className="size-2 text-muted-foreground/80" />
                        </div>
                        {navItem.title}
                      </CommandItem>
                    );
                  }

                  return navItem.items?.map((subItem, subIndex) => (
                    <CommandItem
                      key={`${navItem.title}-${subItem.url}-${subIndex}`}
                      value={`${navItem.title}-${subItem.url}`}
                      onSelect={() => {
                        runCommand(() => router.push(subItem.url));
                      }}
                    >
                      <div className="flex size-4 items-center justify-center">
                        <ArrowRight className="size-2 text-muted-foreground/80" />
                      </div>
                      {navItem.title} <ChevronRight /> {subItem.title}
                    </CommandItem>
                  ));
                })}
              </CommandGroup>
            ))}
            {navGroups.length > 0 ? <CommandSeparator /> : null}
            <CommandGroup heading="主题">
              <CommandItem onSelect={() => runCommand(() => setTheme("light"))}>
                <Sun /> <span>浅色</span>
              </CommandItem>
              <CommandItem onSelect={() => runCommand(() => setTheme("dark"))}>
                <Moon className="scale-90" />
                <span>深色</span>
              </CommandItem>
              <CommandItem onSelect={() => runCommand(() => setTheme("system"))}>
                <Laptop />
                <span>跟随系统</span>
              </CommandItem>
            </CommandGroup>
          </ScrollArea>
        </CommandList>
      </Command>
    </CommandDialog>
  );
}
