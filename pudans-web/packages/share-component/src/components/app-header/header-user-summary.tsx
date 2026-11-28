import { Avatar } from "antd";

import type { AppHeaderUser } from "./header-types";

type UserSummaryProps = {
  user: AppHeaderUser;
};

export function UserSummary({ user }: UserSummaryProps) {
  const content = (
    <>
      <Avatar className="pudans-app-header__avatar" size={28} src={user.avatarSrc}>
        {user.name.slice(0, 1).toUpperCase()}
      </Avatar>
      <span className="pudans-app-header__user-name">{user.name}</span>
    </>
  );

  if (user.href) {
    return (
      <a aria-haspopup="menu" className="pudans-app-header__user" href={user.href}>
        {content}
      </a>
    );
  }

  return (
    <button aria-haspopup="menu" className="pudans-app-header__user" type="button">
      {content}
    </button>
  );
}
