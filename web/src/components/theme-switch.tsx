import { useEffect } from "react";

import { Check, Moon, Sun } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { type Theme, useTheme } from "@/context/theme-provider";
import { cn } from "@/lib/utils";

const THEME_COLORS: Record<Theme, string> = {
  dark: "#020817",
  light: "#fff",
  system: "#fff",
};

export function ThemeSwitch({ className }: { className?: string }) {
  const { theme, setTheme } = useTheme();

  useEffect(() => {
    const metaThemeColor = document.querySelector("meta[name='theme-color']");

    if (metaThemeColor) {
      metaThemeColor.setAttribute("content", THEME_COLORS[theme]);
    }
  }, [theme]);

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className={cn("scale-95 rounded-full", className)}>
          <Sun className="size-[1.2rem] scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
          <Moon className="absolute size-[1.2rem] scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
          <span className="sr-only">切换主题</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => setTheme("light")}>
          浅色
          <Check size={14} className={cn("ms-auto", theme !== "light" && "hidden")} />
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("dark")}>
          深色
          <Check size={14} className={cn("ms-auto", theme !== "dark" && "hidden")} />
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("system")}>
          跟随系统
          <Check size={14} className={cn("ms-auto", theme !== "system" && "hidden")} />
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
