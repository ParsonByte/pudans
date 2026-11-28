import { ChevronDown } from "lucide-react";

import type { AppHeaderMenu } from "./header-types";

type HamburgerMenuProps = {
  menus: AppHeaderMenu[];
  onSelect: (key: string) => void;
};

export function HamburgerMenu({ menus, onSelect }: HamburgerMenuProps) {
  return (
    <div
      className="pudans-app-header__hamburger-panel"
      id="pudans-app-header-hamburger"
      role="menu"
    >
      {menus.map((menu) => (
        <button
          className="pudans-app-header__hamburger-item"
          key={menu.key}
          onClick={() => onSelect(menu.key)}
          role="menuitem"
          type="button"
        >
          <span>{menu.label}</span>
          <ChevronDown size={14} />
        </button>
      ))}
    </div>
  );
}
