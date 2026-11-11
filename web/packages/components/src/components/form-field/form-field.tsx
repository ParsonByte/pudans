import { useId, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./form-field.module.css";

export interface FormFieldProps {
  label?: ReactNode;
  description?: ReactNode;
  error?: ReactNode;
  required?: boolean;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Label + control + helper/error wrapper migrated from the legacy
 * StyledFormControl / Form files. Keeps validation copy, required marker and
 * description typography consistent across every form.
 */
export function FormField({
  label,
  description,
  error,
  required = false,
  htmlFor,
  children,
  className,
}: FormFieldProps) {
  const generatedId = useId();
  const descriptionId = "field-description-" + generatedId.replace(/:/g, "");
  const errorId = "field-error-" + generatedId.replace(/:/g, "");

  return (
    <div
      className={cn(styles.field, error && styles.invalid, className)}
      data-required={required || undefined}
    >
      {label ? (
        <label className={styles.label} htmlFor={htmlFor}>
          {label}
          {required ? (
            <span className={styles.required} aria-hidden="true">
              *
            </span>
          ) : null}
        </label>
      ) : null}
      {description ? (
        <p id={descriptionId} className={styles.description}>
          {description}
        </p>
      ) : null}
      <div className={styles.control}>{children}</div>
      {error ? (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export interface FormSectionProps {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  bordered?: boolean;
  className?: string;
}

/** Grouped form/settings section with an optional header and action slot. */
export function FormSection({
  title,
  description,
  actions,
  children,
  bordered = true,
  className,
}: FormSectionProps) {
  return (
    <section className={cn(styles.section, bordered && styles.bordered, className)}>
      {title || description || actions ? (
        <header className={styles.sectionHeader}>
          <div className={styles.sectionHeading}>
            {title ? <h2 className={styles.sectionTitle}>{title}</h2> : null}
            {description ? <p className={styles.sectionDescription}>{description}</p> : null}
          </div>
          {actions ? <div className={styles.sectionActions}>{actions}</div> : null}
        </header>
      ) : null}
      <div className={styles.sectionBody}>{children}</div>
    </section>
  );
}

export interface SettingItemProps {
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned control (switch, button, select). */
  control?: ReactNode;
  children?: ReactNode;
  className?: string;
}

/**
 * Title/description/control row migrated from the legacy SettingListItem.
 * Used by UC profile & security and by console settings sections.
 */
export function SettingItem({
  title,
  description,
  control,
  children,
  className,
}: SettingItemProps) {
  return (
    <div className={cn(styles.settingItem, className)}>
      <div className={styles.settingBody}>
        <span className={styles.settingTitle}>{title}</span>
        {description ? <span className={styles.settingDescription}>{description}</span> : null}
        {children}
      </div>
      {control ? <div className={styles.settingControl}>{control}</div> : null}
    </div>
  );
}
