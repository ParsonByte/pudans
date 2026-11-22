import type { ReactNode } from "react";

type BentoGridProps = {
    children: ReactNode;
    className?: string;
};

export function BentoGrid({ children, className }: BentoGridProps) {
    return <div className={className ? `bento-grid ${className}` : "bento-grid"}>{children}</div>;
}
