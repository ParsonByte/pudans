import type { ReactNode } from "react";
import { createPortal } from "react-dom";

import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type FloatingActionBarProps = {
  open: boolean;
  children: ReactNode;
  className?: string;
  onDismiss?: () => void;
};

export function FloatingActionBar({
  open,
  children,
  className,
  onDismiss,
}: FloatingActionBarProps) {
  if (!open || typeof document === "undefined") {
    return null;
  }

  return createPortal(
    <div className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4">
      <div
        role="region"
        aria-label="浮动操作栏"
        aria-live="polite"
        data-motion="enter"
        className={cn(
          "pointer-events-auto flex max-w-[calc(100vw-2rem)] flex-wrap items-center gap-2 border bg-background/95 p-2 shadow-lg backdrop-blur",
          className,
        )}
      >
        {onDismiss ? (
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            onClick={onDismiss}
            aria-label="关闭操作栏"
          >
            <X />
          </Button>
        ) : null}
        {children}
      </div>
    </div>,
    document.body,
  );
}
