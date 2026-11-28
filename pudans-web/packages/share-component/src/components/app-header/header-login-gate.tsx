import { Button } from "antd";
import { Check, ShieldCheck } from "lucide-react";

type LoginGateProps = {
  className?: string;
  compact?: boolean;
  description?: string;
  items?: string[];
  loginHref?: string;
  overlay?: boolean;
  title?: string;
};

const DEFAULT_ITEMS = ["统一身份管理", "安全访问控制", "应用快速接入"];

export function LoginGate({
  className,
  compact = false,
  description,
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
        compact ? "pudans-login-gate--compact" : "",
        className,
      ].filter(Boolean).join(" ")}
    >
      <div className="pudans-login-gate__panel">
        {compact ? (
          <span className="pudans-login-gate__compact-icon"><ShieldCheck size={20} /></span>
        ) : (
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
        )}
        <h3>{title}</h3>
        {description ? <p className="pudans-login-gate__description">{description}</p> : null}
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