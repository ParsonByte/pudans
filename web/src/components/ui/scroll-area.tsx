import type { HTMLAttributes, ReactNode } from "react";

type ScrollAxis = "both" | "x" | "y";

type ScrollAreaProps = HTMLAttributes<HTMLDivElement> & {
    axis?: ScrollAxis;
    children: ReactNode;
};

const AXIS_CLASS_NAMES: Record<ScrollAxis, string> = {
    both: "overflow-auto",
    x: "overflow-x-auto",
    y: "overflow-y-auto",
};

export function ScrollArea({ axis = "y", children, className, ...rest }: ScrollAreaProps) {
    return (
        <div className={`narrow-scrollbar ${AXIS_CLASS_NAMES[axis]} ${className ?? ""}`} {...rest}>
            {children}
        </div>
    );
}
