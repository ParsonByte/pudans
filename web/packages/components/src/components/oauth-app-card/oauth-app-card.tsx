import { useState, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Card } from "../card";
import { Icon } from "../icon";
import { Skeleton } from "../skeleton";
import styles from "./oauth-app-card.module.css";

export interface OAuthAppCardProps {
  name: ReactNode;
  description?: ReactNode;
  /** Absolute URL or Iconify name (e.g. "cr:LinkOutlined"). */
  icon?: string;
  loading?: boolean;
  action?: ReactNode;
  className?: string;
}

function isUrl(value: string): boolean {
  return /^(https?:|data:|\/)/.test(value);
}

/**
 * Third-party application summary migrated from the legacy Frame/OauthAppCard.
 * Accepts either a brand icon URL or an Iconify name and keeps the skeleton
 * shape while loading.
 */
export function OAuthAppCard({
  name,
  description,
  icon,
  loading = false,
  action,
  className,
}: OAuthAppCardProps) {
  const [imageFailed, setImageFailed] = useState(false);

  if (loading) {
    return (
      <Card className={cn(styles.card, className)}>
        <Skeleton variant="circular" width={48} height={48} />
        <div className={styles.body}>
          <Skeleton variant="text" width="55%" height={22} />
          <Skeleton variant="text" width="80%" height={18} />
        </div>
      </Card>
    );
  }

  const showImage = Boolean(icon) && !imageFailed && isUrl(icon as string);

  return (
    <Card className={cn(styles.card, className)}>
      <span className={styles.icon} aria-hidden="true">
        {showImage ? (
          <img className={styles.image} src={icon} alt="" onError={() => setImageFailed(true)} />
        ) : icon ? (
          <Icon name={icon} size={24} />
        ) : (
          <Icon name="cr:LinkOutlined" size={24} />
        )}
      </span>
      <div className={styles.body}>
        <span className={styles.name}>{name}</span>
        {description ? <span className={styles.description}>{description}</span> : null}
      </div>
      {action ? <div className={styles.action}>{action}</div> : null}
    </Card>
  );
}
