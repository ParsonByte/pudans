import { type ElementType, type ReactNode, useCallback, useMemo } from "react";

import { ArrowRight, ChevronRight, Laptop, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useRouter } from "next/navigation";

import { withoutChatMenus } from "@/components/layout/nav-filter";
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
import { ConsoleState } from "@/modules/workspace/console/components/console-state";

type ThemeCommand = {
  value: string;
  label: string;
  icon: ElementType;
  iconClassName?: string;
};

const THEME_COMMANDS: ThemeCommand[] = [
  { value: "light", label: "浅色", icon: Sun },
  { value: "dark", label: "深色", icon: Moon, iconClassName: "scale-90" },
  { value: "system", label: "跟随系统", icon: Laptop },
];

type NavCommand = {
  key: string;
  value: string;
  label: ReactNode;
  url: string;
};

type CommandMenuProps = {
  navGroups?: NavGroup[];
};

export function CommandMenu({ navGroups = [] }: CommandMenuProps) {
  const router = useRouter();
  const { setTheme } = useTheme();
  const { open, setOpen } = useSearch();
  const groups = useMemo(() => withoutChatMenus(navGroups, true), [navGroups]);

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
            <CommandEmpty>
              <ConsoleState variant="empty" title="未找到结果。" className="min-h-0 px-0 py-6" />
            </CommandEmpty>
            {groups.map((group) => (
              <CommandGroup key={group.title} heading={group.title}>
                {navCommands(group).map((command) => (
                  <CommandItem
                    key={command.key}
                    value={command.value}
                    onSelect={() => {
                      runCommand(() => router.push(command.url));
                    }}
                  >
                    <div className="flex size-4 items-center justify-center">
                      <ArrowRight className="size-2 text-muted-foreground/80" />
                    </div>
                    {command.label}
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
            {groups.length > 0 ? <CommandSeparator /> : null}
            <CommandGroup heading="主题">
              {THEME_COMMANDS.map((command) => (
                <CommandItem
                  key={command.value}
                  onSelect={() => runCommand(() => setTheme(command.value))}
                >
                  <command.icon className={command.iconClassName} />
                  <span>{command.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </ScrollArea>
        </CommandList>
      </Command>
    </CommandDialog>
  );
}

function navCommands(group: NavGroup): NavCommand[] {
  return group.items.flatMap((item, index): NavCommand[] => {
    if (item.url) {
      return [{ key: `${item.url}-${index}`, value: item.title, label: item.title, url: item.url }];
    }

    return (item.items ?? []).map((subItem, subIndex) => ({
      key: `${item.title}-${subItem.url}-${subIndex}`,
      value: `${item.title}-${subItem.url}`,
      label: (
        <>
          {item.title} <ChevronRight /> {subItem.title}
        </>
      ),
      url: subItem.url,
    }));
  });
}
