export type AppShellGroup = {
    href?: string;
    key: string;
    label: string;
};

type AppShellItemGroup = {
    key: string;
    label: string;
};

export type AppShellItem = {
    group?: AppShellItemGroup;
    href?: string;
    icon?: string;
    key: string;
    label: string;
};

export type AppShellSidebarProps = {
    activeItem?: string;
    collapsed: boolean;
    isDark: boolean;
    onNavigate: (href: string) => void;
};

export const SIDE_NAVIGATION_ID = "workspace-side-navigation";
