import { type ComponentProps, type Ref, useEffect, useState } from "react";

import { SidebarTrigger } from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";

type HeaderProps = ComponentProps<"header"> & {
  fixed?: boolean;
  ref?: Ref<HTMLElement>;
};

export function Header({ className, fixed, children, ...props }: HeaderProps) {
  const [offset, setOffset] = useState(0);
  const scrolled = Boolean(fixed && offset > 10);

  useEffect(() => {
    const onScroll = () => {
      setOffset(document.body.scrollTop || document.documentElement.scrollTop);
    };

    document.addEventListener("scroll", onScroll, { passive: true });

    return () => document.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={cn("z-50 h-16", fixed && "peer/header sticky top-0 w-[inherit]", className)}
      {...props}
    >
      <div
        className={cn(
          "relative flex h-full items-center gap-3 transition-all duration-200 sm:gap-4",
          scrolled
            ? "mx-3 my-2 h-12 border bg-background/70 px-4 shadow-lg backdrop-blur-xl"
            : "p-4",
        )}
      >
        <SidebarTrigger variant="outline" className="max-md:scale-125" />
        {children}
      </div>
    </header>
  );
}
