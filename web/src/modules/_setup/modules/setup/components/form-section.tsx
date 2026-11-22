import type { ReactNode } from "react";

type FormSectionProps = {
    title: string;
    children: ReactNode;
};

export function FormSection({ children, title }: FormSectionProps) {
    return (
        <section className="space-y-4">
            <div className="flex items-center gap-3">
                <h3 className="shrink-0 text-sm font-semibold text-neutral-900 dark:text-neutral-100">{title}</h3>
                <span aria-hidden="true" className="h-px flex-1 bg-neutral-200 dark:bg-neutral-800" />
            </div>
            <div className="space-y-4">{children}</div>
        </section>
    );
}
