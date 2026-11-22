import type { ReactNode } from "react";

import { ThemeMenu } from "@/components/ui";

type AppHeaderProps = {
    children?: ReactNode;
};

export function AppHeader({ children }: AppHeaderProps) {
    return (
        <div className="flex items-center gap-2">
            {children}
            <ThemeMenu />
        </div>
    );
}
