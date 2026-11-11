import { cn } from "../../utils/cn";
import { DropdownMenu } from "../dropdown-menu";
import { Icon } from "../icon";
import styles from "./language-switcher.module.css";

export interface LanguageOption {
  code: string;
  label: string;
  description?: string;
}

export interface LanguageSwitcherProps {
  languages: readonly LanguageOption[];
  value: string;
  onChange: (code: string) => void;
  /** Icon-only trigger when true. */
  compact?: boolean;
  showCurrentLabel?: boolean;
  ariaLabel?: string;
  className?: string;
}

/**
 * Language picker migrated from the legacy Common/LanguageSwitcher. i18n
 * runtime is injected via props so the component stays app-agnostic.
 */
export function LanguageSwitcher({
  languages,
  value,
  onChange,
  compact = false,
  showCurrentLabel = false,
  ariaLabel = "Change language",
  className,
}: LanguageSwitcherProps) {
  const current = languages.find((language) => language.code === value);
  const items = languages.map((language) => ({
    key: language.code,
    label: (
      <span className={styles.itemLabel}>
        <span>{language.label}</span>
        {language.description ? (
          <span className={styles.itemDescription}>{language.description}</span>
        ) : null}
      </span>
    ),
    icon:
      language.code === value ? (
        <Icon name="cr:Checkmark" size={14} />
      ) : (
        <span className={styles.iconSpacer} />
      ),
    onSelect: () => onChange(language.code),
  }));

  return (
    <div className={cn(styles.root, className)}>
      <DropdownMenu
        trigger={
          <span className={styles.trigger}>
            <Icon name="cr:Translate" size={18} />
            {!compact && showCurrentLabel ? (
              <span className={styles.current}>{current?.label ?? value}</span>
            ) : null}
          </span>
        }
        triggerLabel={ariaLabel}
        items={items}
        showChevron={false}
      />
    </div>
  );
}
