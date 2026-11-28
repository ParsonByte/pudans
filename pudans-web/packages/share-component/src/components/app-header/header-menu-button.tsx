import { Button } from "antd";
import { Menu, X } from "lucide-react";

type HeaderMenuButtonProps = {
  expanded: boolean;
  onClick: () => void;
};

export function HeaderMenuButton({ expanded, onClick }: HeaderMenuButtonProps) {
  return (
    <Button
      aria-controls="pudans-app-header-hamburger"
      aria-expanded={expanded}
      aria-label={expanded ? "关闭导航菜单" : "打开导航菜单"}
      className="pudans-app-header__hamburger"
      icon={expanded ? <X size={16} /> : <Menu size={16} />}
      onClick={onClick}
      type="text"
    />
  );
}
