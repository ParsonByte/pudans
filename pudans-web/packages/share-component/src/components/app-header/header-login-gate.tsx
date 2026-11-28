import { Button } from "antd";
import { Check } from "lucide-react";

type LoginGateProps = {
  className?: string;
  items?: string[];
  loginHref?: string;
  overlay?: boolean;
  title?: string;
};

const DEFAULT_ITEMS = ["统一身份管理", "安全访问控制", "应用快速接入"];

export function LoginGate({
  className,
  items = DEFAULT_ITEMS,
  loginHref,
  overlay = false,
  title = "登录以查看我们提供的能力",
}: LoginGateProps) {
  return (
    <div
      className={[
        "pudans-login-gate",
        overlay ? "pudans-login-gate--overlay" : "",
        className,
      ].filter(Boolean).join(" ")}
    >
      <div className="pudans-login-gate__panel">
        <div aria-hidden="true" className="pudans-login-gate__art">
          <span className="pudans-login-gate__art-grid" />
          <span className="pudans-login-gate__art-window">
            <span className="pudans-login-gate__art-avatar" />
            <span className="pudans-login-gate__art-copy">
              <i />
              <i />
              <i />
            </span>
          </span>
          <span className="pudans-login-gate__art-chip" />
        </div>
        <h3>{title}</h3>
        {items.length > 0 ? (
          <ul className="pudans-login-gate__items">
            {items.map((item) => (
              <li key={item}><Check size={13} /><span>{item}</span></li>
            ))}
          </ul>
        ) : null}
        <Button block href={loginHref} type="primary">登录</Button>
      </div>
    </div>
  );
}