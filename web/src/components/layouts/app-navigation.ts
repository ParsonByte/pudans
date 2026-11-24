import type { MouseEvent as ReactMouseEvent } from "react";

export function navigateWithMenuLink(
    event: ReactMouseEvent<HTMLElement>,
    href: string,
    onNavigate: (href: string) => void,
) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
    }

    event.preventDefault();
    onNavigate(href);
}
