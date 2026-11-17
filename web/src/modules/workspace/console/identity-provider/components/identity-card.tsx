import type { KeyboardEvent } from "react";

import { Info, Plus, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import type { IdentityKind } from "./identity-icon";
import { IdentityIcon } from "./identity-icon";

type IdentityCardProps = {
  kind: IdentityKind;
  title: string;
  tags: readonly string[];
  description: string;
  onEdit: () => void;
  onDelete?: () => void;
};

const WATERMARK_CLASSES: Record<IdentityKind, string> = {
  totp: "text-sky-500",
  oauth2: "text-slate-500",
  oidc: "text-orange-500",
  feishu: "text-emerald-500",
  pat: "text-amber-500",
};

export function IdentityCard({
  kind,
  title,
  tags,
  description,
  onEdit,
  onDelete,
}: IdentityCardProps) {
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.target !== event.currentTarget) {
      return;
    }
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onEdit();
    }
  };

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={`编辑${title}`}
      onClick={onEdit}
      onKeyDown={handleKeyDown}
      className="group relative flex min-h-56 cursor-pointer flex-col overflow-hidden rounded-xl border bg-card p-6 text-left transition-colors outline-none hover:bg-muted/20 focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <div className="pointer-events-none absolute top-0 right-0 h-full w-1/2 bg-gradient-to-l from-muted/50 to-transparent" />
      <div className="pointer-events-none absolute top-1/2 right-6 -translate-y-1/2 opacity-20">
        <IdentityIcon kind={kind} size={112} className={WATERMARK_CLASSES[kind]} />
      </div>

      <div className="relative z-10 flex-1 space-y-3">
        <h3 className="max-w-[70%] truncate text-lg font-semibold tracking-tight">{title}</h3>
        <div className="flex flex-wrap gap-2">
          {tags.map((tag) => (
            <Badge key={tag} variant="secondary" className="rounded-full px-2.5">
              {tag}
            </Badge>
          ))}
        </div>
        <p className="flex items-start gap-1.5 text-sm leading-5 text-muted-foreground">
          <Info className="mt-0.5 size-4 shrink-0" />
          <span className="line-clamp-2">{description}</span>
        </p>
      </div>

      <div className="relative z-10 mt-8 flex items-center justify-between gap-3 border-t pt-4">
        <span className="text-sm font-medium text-primary group-hover:underline">编辑配置</span>
        {onDelete ? (
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            aria-label={`删除${title}`}
            onClick={(event) => {
              event.stopPropagation();
              onDelete();
            }}
          >
            <Trash2 />
          </Button>
        ) : (
          <span className="size-7" aria-hidden="true" />
        )}
      </div>
    </div>
  );
}

type AddIdentityCardProps = {
  label: string;
  onClick: () => void;
};

export function AddIdentityCard({ label, onClick }: AddIdentityCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-56 flex-col items-center justify-center gap-3 rounded-xl border border-dashed bg-card/40 p-6 text-sm text-muted-foreground transition-colors hover:border-ring hover:bg-muted/30 hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      <span className="flex size-11 items-center justify-center rounded-full bg-muted">
        <Plus className="size-5" />
      </span>
      <span className="font-medium">{label}</span>
    </button>
  );
}
