import type { ReactNode } from "react";

import { Divider } from "tdesign-react";

type FormSectionProps = {
    title: string;
    children: ReactNode;
};

export function FormSection({ children, title }: FormSectionProps) {
    return (
        <section className="space-y-4">
            <Divider align="left">{title}</Divider>
            <div className="space-y-4">{children}</div>
        </section>
    );
}
