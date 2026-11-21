import type { ReactNode } from "react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

import { AutoScrollArea } from "./auto-scroll-area";
import { FloatingActionBar } from "./floating-action-bar";

export type DialogSize = "sm" | "md" | "lg" | "xl" | "2xl" | "3xl" | "4xl" | "5xl";

const DIALOG_SIZE_CLASSES: Record<DialogSize, string> = {
  sm: "sm:max-w-sm",
  md: "sm:max-w-md",
  lg: "sm:max-w-lg",
  xl: "sm:max-w-xl",
  "2xl": "sm:max-w-2xl",
  "3xl": "sm:max-w-3xl",
  "4xl": "sm:max-w-4xl",
  "5xl": "sm:max-w-5xl",
};

type DialogFormShellProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  headerActions?: ReactNode;
  footer?: ReactNode;
  footerMode?: "always" | "dirty";
  footerVisible?: boolean;
  footerClassName?: string;
  size?: DialogSize;
  maxHeightRatio?: number;
  fill?: boolean;
  contentClassName?: string;
  bodyClassName?: string;
  children: ReactNode;
};

export function DialogFormShell({
  open,
  onOpenChange,
  title,
  description,
  icon,
  headerActions,
  footer,
  footerMode = "always",
  footerVisible = false,
  footerClassName,
  size,
  maxHeightRatio = 0.5,
  fill = false,
  contentClassName,
  bodyClassName,
  children,
}: DialogFormShellProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className={cn(
          "flex max-h-[75vh] flex-col overflow-hidden",
          size && DIALOG_SIZE_CLASSES[size],
          contentClassName,
        )}
      >
        <DialogHeader>
          <div className="flex items-start gap-3 pe-8">
            {icon ? (
              <span className="flex size-10 shrink-0 items-center justify-center bg-muted">
                {icon}
              </span>
            ) : null}
            <div className="space-y-1">
              <DialogTitle>{title}</DialogTitle>
              {description ? <DialogDescription>{description}</DialogDescription> : null}
            </div>
          </div>
          {headerActions ? <div>{headerActions}</div> : null}
        </DialogHeader>

        <AutoScrollArea maxHeightRatio={maxHeightRatio} fill={fill} className={bodyClassName}>
          {children}
        </AutoScrollArea>

        {footer ? (
          footerMode === "dirty" ? (
            <FloatingActionBar open={footerVisible} className={footerClassName}>
              {footer}
            </FloatingActionBar>
          ) : (
            <DialogFooter className={cn("border-t pt-4", footerClassName)}>{footer}</DialogFooter>
          )
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
