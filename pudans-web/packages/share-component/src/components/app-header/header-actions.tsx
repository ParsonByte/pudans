import { Button, Dropdown } from "antd";

import type { AppHeaderUser } from "./header-types";
import { UserMenu } from "./header-user-menu";
import { UserSummary } from "./header-user-summary";

type HeaderActionsProps = {
  consoleHref?: string;
  docsHref?: string;
  getPopupContainer?: () => HTMLElement;
  loginHref?: string;
  onAccessKeyClick?: () => void;
  onAccountCenterClick?: () => void;
  onConsoleClick?: () => void;
  onDocsClick?: () => void;
  onLoginClick?: () => void;
  onLogout?: () => void;
  onSecurityClick?: () => void;
  user?: AppHeaderUser;
};

export function HeaderActions({
  consoleHref,
  docsHref,
  getPopupContainer,
  loginHref,
  onAccessKeyClick,
  onAccountCenterClick,
  onConsoleClick,
  onDocsClick,
  onLoginClick,
  onLogout,
  onSecurityClick,
  user,
}: HeaderActionsProps) {
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
      <Button
        className="pudans-app-header__link"
        href={consoleHref}
        onClick={onConsoleClick}
        type="text"
      >
        控制台
      </Button>
      {user ? (
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
