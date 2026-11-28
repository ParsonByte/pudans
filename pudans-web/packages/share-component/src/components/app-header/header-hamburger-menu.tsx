import { ChevronDown } from "lucide-react";

import { LoginGate } from "./header-login-gate";
import type { AppHeaderMenu } from "./header-types";

type HamburgerMenuProps = {
  locked?: boolean;
  loginHref?: string;
  menus: AppHeaderMenu[];
  onSelect: (key: string) => void;
};

export function HamburgerMenu({ locked = false, loginHref, menus, onSelect }: HamburgerMenuProps) {
  if (locked) {
    return (
      <div
        className="pudans-app-header__hamburger-panel pudans-app-header__hamburger-panel--locked"
        id="pudans-app-header-hamburger"
        role="menu"
      >
        <LoginGate loginHref={loginHref} />
      </div>
    );
  }
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
