import { Copy } from "lucide-react";
import { toast } from "sonner";

type CopyButtonProps = {
  value: string;
  label?: string;
  className?: string;
};

export function CopyButton({ value, label = "复制", className = "" }: CopyButtonProps) {
  return (
    <button
      type="button"
      onClick={() => {
        void navigator.clipboard
          .writeText(value)
          .then(() => toast.success(`${label}已复制。`))
          .catch(() => toast.error("复制失败，请手动选择文本。"));
      }}
      className={`inline-flex size-7 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground ${className}`}
      aria-label={label}
      title={label}
    >
      <Copy className="size-3.5" />
    </button>
  );
}

type CopyFieldProps = {
  value: string;
  label: string;
  hint?: string;
  mono?: boolean;
};

export function CopyField({ value, label, hint, mono = true }: CopyFieldProps) {
  return (
    <div className="space-y-1.5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="flex items-center gap-2 rounded-lg border bg-muted/30 py-2 ps-3 pe-1.5">
        <span
          className={`min-w-0 flex-1 truncate text-xs ${mono ? "font-mono" : ""}`}
          title={value}
        >
          {value}
        </span>
        <CopyButton value={value} label={`${label}`} />
      </div>
      {hint ? <p className="text-[11px] text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
