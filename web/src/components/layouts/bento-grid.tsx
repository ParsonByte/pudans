import type { ReactNode } from "react";

type BentoGridProps = {
    children: ReactNode;
};

export function BentoGrid({ children }: BentoGridProps) {
    return <div className="bento-grid">{children}</div>;
}
