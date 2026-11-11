import { Fragment } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { cn } from "@pudans/shared";
import { Alert, Button, Icon, Input } from "@pudans/components";
import { useRecovery } from "../composables/use-recovery";
import styles from "./recovery-form.module.css";

const STEPS = [
  { number: 1, labelKey: "recoveryStepEmail" },
  { number: 2, labelKey: "recoveryStepPassword" },
  { number: 3, labelKey: "recoveryStepDone" },
] as const;

export function RecoveryForm() {
  const { t } = useTranslation("auth");
  const {
    step,
    account,
    setAccount,
    code,
    setCode,
    codeSent,
    codeSending,
    password,
    setPassword,
    confirmPassword,
    setConfirmPassword,
    passwordVisible,
    togglePasswordVisibility,
    loading,
    error,
    sendCode,
    submit,
  } = useRecovery();

  const description = step === 2 ? t("recoveryPasswordDescription") : t("recoveryDescription");
  const submitLabel = step === 2 ? t("recoveryResetSubmit") : t("recoveryNext");
  const loadingLabel = step === 2 ? t("recoveryResetting") : t("recoverySubmitting");

  return (
    <div className={styles.view}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>{t("recoveryEyebrow")}</span>
        <h2 className={styles.title}>{t("recoveryTitle")}</h2>
        <p className={styles.description}>{description}</p>
      </div>

      <div className={styles.stepper} aria-label={t("recoveryTitle")}>
        {STEPS.map((item, index) => (
          <Fragment key={item.number}>
            {index > 0 ? (
              <span
                className={cn(styles.connector, step > index && styles.connectorDone)}
                aria-hidden="true"
              />
            ) : null}
            <div
              className={cn(
                styles.step,
                step === item.number && styles.stepActive,
                step > item.number && styles.stepDone,
              )}
              aria-current={step === item.number ? "step" : undefined}
            >
              <span className={styles.stepDot}>
                {step > item.number ? <Icon name="mdi:check" size={14} /> : item.number}
              </span>
              <span className={styles.stepLabel}>{t(item.labelKey)}</span>
            </div>
          </Fragment>
        ))}
      </div>

      {step === 3 ? (
        <div className={styles.successPanel} role="status">
          <span className={styles.successIcon} aria-hidden="true">
            <Icon name="mdi:check-circle-outline" size={38} />
          </span>
          <h3 className={styles.successTitle}>{t("recoverySuccessTitle")}</h3>
          <p className={styles.successDescription}>{t("recoverySuccessDescription")}</p>
        </div>
      ) : (
        <form className={styles.form} onSubmit={submit} noValidate>
          {step === 1 ? (
            <>
              <Input
                className={styles.input}
                label={t("recoveryEmailLabel")}
                placeholder={t("recoveryPlaceholder")}
                value={account}
                autoComplete="email"
                autoCapitalize="off"
                spellCheck={false}
                autoFocus
                startIcon={<Icon name="mdi:email-outline" size={18} />}
                onChange={(event) => setAccount(event.target.value)}
              />

              <div className={styles.codeRow}>
                <Input
                  className={cn(styles.input, styles.codeInput)}
                  label={t("recoveryCodeLabel")}
                  placeholder={t("recoveryCodePlaceholder")}
                  value={code}
                  autoComplete="one-time-code"
                  inputProps={{ inputMode: "numeric" }}
                  startIcon={<Icon name="mdi:shield-key-outline" size={18} />}
                  onChange={(event) => setCode(event.target.value)}
                />
                <button
                  type="button"
                  className={styles.codeButton}
                  disabled={codeSending}
                  onClick={() => {
                    void sendCode();
                  }}
                >
                  {codeSending
                    ? t("recoverySendingCode")
                    : codeSent
                      ? t("recoveryResendCode")
                      : t("recoverySendCode")}
                </button>
              </div>
            </>
          ) : (
            <>
              <Input
                className={styles.input}
                label={t("recoveryNewPassword")}
                placeholder={t("recoveryNewPasswordPlaceholder")}
                type={passwordVisible ? "text" : "password"}
                value={password}
                autoComplete="new-password"
                autoFocus
                startIcon={<Icon name="mdi:lock-outline" size={18} />}
                endIcon={
                  <button
                    type="button"
                    className={styles.visibilityButton}
                    aria-label={passwordVisible ? t("hidePassword") : t("showPassword")}
                    onClick={togglePasswordVisibility}
                  >
                    <Icon
                      name={passwordVisible ? "mdi:eye-off-outline" : "mdi:eye-outline"}
                      size={18}
                    />
                  </button>
                }
                onChange={(event) => setPassword(event.target.value)}
              />

              <Input
                className={styles.input}
                label={t("recoveryConfirmPassword")}
                placeholder={t("recoveryConfirmPasswordPlaceholder")}
                type={passwordVisible ? "text" : "password"}
                value={confirmPassword}
                autoComplete="new-password"
                startIcon={<Icon name="mdi:lock-check-outline" size={18} />}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </>
          )}

          {error ? (
            <Alert severity="error" className={styles.errorAlert}>
              {error}
            </Alert>
          ) : null}

          {step === 1 ? (
            <div className={styles.hints}>
              <span>
                <Icon name="mdi:shield-check-outline" size={13} />
                {t("recoveryCodeHint")}
              </span>
              <span>{t("recoveryPrivacyHint")}</span>
            </div>
          ) : (
            <div className={styles.hints}>
              <span>
                <Icon name="mdi:shield-check-outline" size={13} />
                {t("recoveryPasswordRule")}
              </span>
              <span>{t("recoveryPasswordHint")}</span>
            </div>
          )}

          <Button
            type="submit"
            size="lg"
            radius="lg"
            block
            loading={loading}
            loadingText={loadingLabel}
            endIcon={<Icon name="mdi:arrow-right" size={18} />}
            className={styles.submit}
          >
            {submitLabel}
          </Button>
        </form>
      )}

      <Link className={styles.back} to="/">
        <Icon name="mdi:arrow-left" size={15} />
        {t("recoveryBack")}
      </Link>
    </div>
  );
}
