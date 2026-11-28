import { useState } from "react";

import { Button, Dropdown, Skeleton } from "antd";

import type { AppHeaderConsole, AppHeaderUser } from "./header-types";
import { UserMenu } from "./header-user-menu";
import { UserSummary } from "./header-user-summary";

type HeaderActionsProps = {
  consoles?: AppHeaderConsole[];
  docsHref?: string;
  getPopupContainer?: () => HTMLElement;
  loading?: boolean;
  loginHref?: string;
  onAccessKeyClick?: () => void;
  onAccountCenterClick?: () => void;
  onDocsClick?: () => void;
  onLoginClick?: () => void;
  onLogout?: () => void;
  onSecurityClick?: () => void;
  user?: AppHeaderUser;
};

export function HeaderActions({
  consoles,
  docsHref,
  getPopupContainer,
  loading = false,
  loginHref,
  onAccessKeyClick,
  onAccountCenterClick,
  onDocsClick,
  onLoginClick,
  onLogout,
  onSecurityClick,
  user,
}: HeaderActionsProps) {
  const [consolesOpen, setConsolesOpen] = useState(false);

  const selectConsole = (href: string) => {
    setConsolesOpen(false);
    if (href !== "#") {
      window.location.assign(href);
    }
  };

  return (
    <div className="pudans-app-header__actions">
      <Button
        className="pudans-app-header__link"
        href={docsHref}
        onClick={onDocsClick}
        type="text"
      >
        文档
      </Button>
      {consoles?.length ? (
        <Dropdown
          getPopupContainer={getPopupContainer}
          onOpenChange={setConsolesOpen}
          open={consolesOpen}
          placement="bottomRight"
          popupRender={() => (
            <div className="pudans-app-header__console-panel" role="menu">
              {consoles.map((entry) => (
                <button
                  className="pudans-app-header__console-item"
                  key={entry.label}
                  onClick={() => selectConsole(entry.href)}
                  role="menuitem"
                  type="button"
                >
                  {entry.label}
                </button>
              ))}
            </div>
          )}
          trigger={["hover", "click"]}
        >
          <Button className="pudans-app-header__link" type="text">
            控制台
          </Button>
        </Dropdown>
      ) : null}
      {loading && !user ? (
        <span className="pudans-app-header__user-loading">
          <Skeleton.Avatar active shape="circle" size={28} />
        </span>
      ) : user ? (
        <Dropdown
          getPopupContainer={getPopupContainer}
          placement="bottomRight"
          popupRender={() => (
            <UserMenu
              onAccessKeyClick={onAccessKeyClick}
              onAccountCenterClick={onAccountCenterClick}
              onLogout={onLogout}
              onSecurityClick={onSecurityClick}
              user={user}
            />
          )}
          trigger={["hover", "click"]}
        >
          <span className="pudans-app-header__user-trigger">
            <UserSummary user={user} />
          </span>
        </Dropdown>
      ) : (
        <Button
          className="pudans-app-header__login"
          href={loginHref}
          onClick={onLoginClick}
          type="primary"
        >
          登录
        </Button>
      )}
    </div>
  );
}
