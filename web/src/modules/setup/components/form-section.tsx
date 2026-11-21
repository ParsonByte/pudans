import type { ComponentType, ReactNode } from "react";

type FormSectionProps = {
  icon: ComponentType<{ className?: string }>;
  title: string;
  description: string;
  children: ReactNode;
};

export function FormSection({ icon: Icon, title, description, children }: FormSectionProps) {
  return (
    <section className="space-y-4">
      <div className="flex items-start gap-3">
        <span className="flex size-8 shrink-0 items-center justify-center bg-primary/10 text-primary">
          <Icon className="size-4" />
        </span>
        <div className="space-y-0.5">
          <h3 className="text-sm font-semibold">{title}</h3>
          <p className="text-xs leading-5 text-muted-foreground">{description}</p>
        </div>
      </div>
      <div className="space-y-4">{children}</div>
    </section>
  );
}
