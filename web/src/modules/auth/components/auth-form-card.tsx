import type { ReactNode } from "react";

type AuthFormCardProps = {
  eyebrow: string;
  title: string;
  description: ReactNode;
  children: ReactNode;
};

export function AuthFormCard({ eyebrow, title, description, children }: AuthFormCardProps) {
  return (
    <div className="flex flex-1 flex-col gap-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.2em] text-muted-foreground">{eyebrow}</p>
        <h2 className="text-2xl font-semibold tracking-tight">{title}</h2>
        <p className="text-sm leading-6 text-muted-foreground">{description}</p>
      </div>
      {children}
    </div>
  );
}
