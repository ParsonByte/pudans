import type { AppHeaderMenu } from "./header-types";

type NavItemProps = {
  active: boolean;
  menu: AppHeaderMenu;
  onClick: () => void;
  onMouseEnter: () => void;
};

export function NavItem({ active, menu, onClick, onMouseEnter }: NavItemProps) {
  const className = [
    "pudans-app-header__nav-trigger",
    active ? "is-active" : "",
  ]
    .filter(Boolean)
    .join(" ");

  if (!menu.groups?.length) {
    return (
      <a
        className="pudans-app-header__nav-trigger"
        href={menu.href}
        onMouseEnter={onMouseEnter}
      >
        {menu.label}
      </a>
    );
  }

  return (
    <button
      aria-expanded={active}
      aria-haspopup="true"
      className={className}
      onClick={onClick}
      onFocus={onMouseEnter}
      onMouseEnter={onMouseEnter}
      type="button"
    >
      {menu.label}
    </button>
  );
}
