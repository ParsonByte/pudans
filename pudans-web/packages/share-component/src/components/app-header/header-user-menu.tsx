import { Avatar, Button, Divider } from "antd";

import { UserOutlined } from "@ant-design/icons";
import { Setting, ShieldNetwork } from "reicon-react";

import type { AppHeaderUser } from "./header-types";

type UserMenuProps = {
  onAccessKeyClick?: () => void;
  onAccountCenterClick?: () => void;
  onLogout?: () => void;
  onSecurityClick?: () => void;
  user: AppHeaderUser;
};

export function UserMenu({
  onAccessKeyClick,
  onAccountCenterClick,
  onLogout,
  onSecurityClick,
  user,
}: UserMenuProps) {
  return (
    <div className="pudans-app-header__user-menu" role="menu">
      <section className="pudans-app-header__user-section">
        <div className="pudans-app-header__user-section-header">
          <span className="pudans-app-header__user-section-title">
            <UserOutlined style={{ fontSize: 16 }} />
            账号
          </span>
          <Button onClick={onLogout} size="small">
            退出登录
          </Button>
        </div>
        <div className="pudans-app-header__user-profile">
          <Avatar
            className="pudans-app-header__user-menu-avatar"
            size={40}
            src={user.avatarSrc}
          >
            {user.name.slice(0, 1).toUpperCase()}
          </Avatar>
          <div className="pudans-app-header__user-profile-copy">
            <div className="pudans-app-header__user-profile-name">{user.name}</div>
            {user.id ? (
              <div className="pudans-app-header__user-profile-id">ID: {user.id}</div>
            ) : null}
          </div>
        </div>
      </section>
      <Divider className="pudans-app-header__user-divider" />
      <section className="pudans-app-header__user-section">
        <div className="pudans-app-header__user-section-title">
          <ShieldNetwork size={16} />
          安全
        </div>
        <div className="pudans-app-header__user-links">
          <Button onClick={onSecurityClick} size="small" type="link">
            安全管理
          </Button>
          <span aria-hidden="true" className="pudans-app-header__user-link-divider" />
          <Button onClick={onAccessKeyClick} size="small" type="link">
            AccessKey
          </Button>
        </div>
      </section>
      <Divider className="pudans-app-header__user-divider" />
      <section className="pudans-app-header__user-section">
        <div className="pudans-app-header__user-section-title">
          <Setting size={16} />
          其他
        </div>
        <Button block onClick={onAccountCenterClick}>
          账号中心
        </Button>
      </section>
    </div>
  );
}
