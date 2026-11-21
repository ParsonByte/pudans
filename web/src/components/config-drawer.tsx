import { type ComponentType, type SVGProps } from "react";

import { CircleCheck, RotateCcw, Settings } from "lucide-react";
import { useTheme } from "next-themes";

import { IconDir } from "@/assets/custom/icon-dir";
import { IconLayoutCompact } from "@/assets/custom/icon-layout-compact";
import { IconLayoutDefault } from "@/assets/custom/icon-layout-default";
import { IconLayoutFull } from "@/assets/custom/icon-layout-full";
import { IconSidebarFloating } from "@/assets/custom/icon-sidebar-floating";
import { IconSidebarInset } from "@/assets/custom/icon-sidebar-inset";
import { IconSidebarSidebar } from "@/assets/custom/icon-sidebar-sidebar";
import { IconThemeDark } from "@/assets/custom/icon-theme-dark";
import { IconThemeLight } from "@/assets/custom/icon-theme-light";
import { IconThemeSystem } from "@/assets/custom/icon-theme-system";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { useSidebar } from "@/components/ui/sidebar";
import { type Direction, useDirection } from "@/context/direction-provider";
import { type Collapsible, type LayoutVariant, useLayout } from "@/context/layout-provider";
import { cn } from "@/lib/utils";

type ConfigOptionData = {
  value: string;
  label: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
};

type SectionTitleProps = {
  title: string;
  showReset?: boolean;
  onReset?: () => void;
  resetAriaLabel?: string;
};

function SectionTitle({ title, showReset = false, onReset, resetAriaLabel }: SectionTitleProps) {
  return (
    <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-muted-foreground">
      {title}
      {showReset && onReset ? (
        <Button
          type="button"
          size="icon"
          variant="secondary"
          className="size-4"
          onClick={onReset}
          aria-label={resetAriaLabel}
        >
          <RotateCcw className="size-3" />
        </Button>
      ) : null}
    </div>
  );
}

function ConfigOption({ item, isTheme = false }: { item: ConfigOptionData; isTheme?: boolean }) {
  return (
    <RadioGroupItem
      value={item.value}
      aria-label={`选择${item.label}`}
      className={cn(
        "group relative block aspect-auto size-auto w-full shrink-0 cursor-pointer border-0 bg-transparent p-1 pb-2 text-left shadow-none transition-colors",
        "data-[state=checked]:bg-muted/60! data-[state=checked]:text-foreground",
        "after:hidden focus-visible:ring-0",
        "data-checked:border-transparent data-checked:bg-transparent data-checked:text-inherit",
      )}
    >
      <span
        aria-hidden="true"
        className="relative block bg-muted/20 ring-1 ring-border transition group-focus-visible:ring-2 group-focus-visible:ring-foreground/60 group-data-[state=checked]:shadow-sm group-data-[state=checked]:ring-2 group-data-[state=checked]:ring-foreground"
      >
        <item.icon
          className={cn(
            "w-full",
            !isTheme &&
              "fill-muted-foreground/40 stroke-muted-foreground/60 group-data-[state=checked]:fill-foreground/80 group-data-[state=checked]:stroke-foreground/80",
          )}
        />
        <CircleCheck className="absolute top-0 right-0 size-6 translate-x-1/2 -translate-y-1/2 fill-foreground stroke-background group-data-[state=unchecked]:hidden" />
      </span>
      <span className="mt-2 block text-center text-sm font-medium text-foreground">
        {item.label}
      </span>
    </RadioGroupItem>
  );
}

type ConfigGridProps = {
  title: string;
  ariaLabel: string;
  value: string;
  defaultValue: string;
  options: ConfigOptionData[];
  onValueChange: (value: string) => void;
  onReset?: () => void;
  resetAriaLabel: string;
  isTheme?: boolean;
  className?: string;
};

function ConfigGrid({
  title,
  ariaLabel,
  value,
  defaultValue,
  options,
  onValueChange,
  onReset,
  resetAriaLabel,
  isTheme = false,
  className,
}: ConfigGridProps) {
  return (
    <div className={className}>
      <SectionTitle
        title={title}
        showReset={value !== defaultValue}
        onReset={onReset ?? (() => onValueChange(defaultValue))}
        resetAriaLabel={resetAriaLabel}
      />
      <RadioGroup
        value={value}
        onValueChange={onValueChange}
        className="grid w-full max-w-md grid-cols-3 gap-4"
        aria-label={ariaLabel}
      >
        {options.map((item) => (
          <ConfigOption key={item.value} item={item} isTheme={isTheme} />
        ))}
      </RadioGroup>
    </div>
  );
}

export function ConfigDrawer() {
  const { setOpen } = useSidebar();
  const { resetDir } = useDirection();
  const { setTheme } = useTheme();
  const { resetLayout } = useLayout();

  const handleReset = () => {
    setOpen(false);
    resetDir();
    setTheme("system");
    resetLayout();
  };

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button type="button" size="icon" variant="ghost" aria-label="打开主题设置" className="">
          <Settings aria-hidden="true" />
        </Button>
      </SheetTrigger>
      <SheetContent className="flex flex-col data-[side=right]:w-full">
        <SheetHeader className="pb-0 text-start">
          <SheetTitle>主题设置</SheetTitle>
          <SheetDescription>调整主题、侧边栏、布局和文字方向。</SheetDescription>
        </SheetHeader>
        <ScrollArea className="min-h-0 flex-1 px-4">
          <div className="space-y-6">
            <ThemeConfig />
            <SidebarConfig />
            <LayoutConfig />
            <DirConfig />
          </div>
        </ScrollArea>
        <SheetFooter className="gap-2">
          <Button
            type="button"
            variant="destructive"
            onClick={handleReset}
            aria-label="恢复所有设置为默认值"
            className="w-full"
          >
            恢复默认
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

function ThemeConfig() {
  const { theme, setTheme } = useTheme();
  const defaultTheme = "system";
  const options: ConfigOptionData[] = [
    { value: "system", label: "跟随系统", icon: IconThemeSystem },
    { value: "light", label: "浅色", icon: IconThemeLight },
    { value: "dark", label: "深色", icon: IconThemeDark },
  ];

  return (
    <ConfigGrid
      title="主题"
      ariaLabel="选择主题"
      value={theme ?? defaultTheme}
      defaultValue={defaultTheme}
      options={options}
      onValueChange={setTheme}
      resetAriaLabel="重置主题为默认值"
      isTheme
    />
  );
}

function SidebarConfig() {
  const { defaultVariant, variant, setVariant } = useLayout();
  const options: ConfigOptionData[] = [
    { value: "inset", label: "嵌入", icon: IconSidebarInset },
    { value: "floating", label: "悬浮", icon: IconSidebarFloating },
    { value: "sidebar", label: "侧边栏", icon: IconSidebarSidebar },
  ];

  return (
    <ConfigGrid
      title="侧边栏"
      ariaLabel="选择侧边栏样式"
      value={variant}
      defaultValue={defaultVariant}
      options={options}
      onValueChange={(value) => setVariant(value as LayoutVariant)}
      resetAriaLabel="重置侧边栏样式为默认值"
      className="max-md:hidden"
    />
  );
}

function LayoutConfig() {
  const { open, setOpen } = useSidebar();
  const { defaultCollapsible, collapsible, setCollapsible } = useLayout();
  const options: ConfigOptionData[] = [
    { value: "default", label: "默认", icon: IconLayoutDefault },
    { value: "icon", label: "紧凑", icon: IconLayoutCompact },
    { value: "offcanvas", label: "全宽布局", icon: IconLayoutFull },
  ];

  return (
    <ConfigGrid
      title="布局"
      ariaLabel="选择布局"
      value={open ? "default" : collapsible}
      defaultValue={defaultCollapsible}
      options={options}
      onValueChange={(value) => {
        if (value === "default") {
          setOpen(true);
          return;
        }

        setOpen(false);
        setCollapsible(value as Collapsible);
      }}
      onReset={() => {
        setOpen(false);
        setCollapsible(defaultCollapsible);
      }}
      resetAriaLabel="重置布局为默认值"
      className="max-md:hidden"
    />
  );
}

function DirConfig() {
  const { defaultDir, dir, setDir } = useDirection();
  const options: ConfigOptionData[] = [
    {
      value: "ltr",
      label: "从左到右",
      icon: (props) => <IconDir dir="ltr" {...props} />,
    },
    {
      value: "rtl",
      label: "从右到左",
      icon: (props) => <IconDir dir="rtl" {...props} />,
    },
  ];

  return (
    <ConfigGrid
      title="方向"
      ariaLabel="选择文字方向"
      value={dir}
      defaultValue={defaultDir}
      options={options}
      onValueChange={(value) => setDir(value as Direction)}
      resetAriaLabel="重置文字方向为默认值"
    />
  );
}
