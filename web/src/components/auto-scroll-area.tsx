import { type ReactNode, useLayoutEffect, useRef, useState } from "react";

import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";

type AutoScrollAreaProps = {
  children: ReactNode;
  maxHeightRatio?: number;
  className?: string;
};

export function AutoScrollArea({ children, maxHeightRatio = 0.5, className }: AutoScrollAreaProps) {
  const contentRef = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState<number>();

  useLayoutEffect(() => {
    const element = contentRef.current;
    if (!element) {
      return;
    }
    const update = () => {
      const maxHeight = window.innerHeight * maxHeightRatio;
      setHeight(Math.min(element.scrollHeight, maxHeight));
    };

    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    window.addEventListener("resize", update);

    return () => {
      observer.disconnect();
      window.removeEventListener("resize", update);
    };
  }, [maxHeightRatio]);

  return (
    <ScrollArea type="auto" style={{ height }} className={cn("pe-5", className)}>
      <div ref={contentRef}>{children}</div>
    </ScrollArea>
  );
}
