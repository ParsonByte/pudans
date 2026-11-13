import { useTranslation } from "react-i18next";
import { Box } from "@mui/material";
import { Alert, Button, Icon, Input } from "@pudans/components";
import { useSetup } from "../composables/use-setup";

export function SetupForm() {
  const { t } = useTranslation("setup");
  const { values, setField, loading, error, submit } = useSetup();

  return (
    <Box
      component="form"
      onSubmit={(event) => {
        void submit(event);
      }}
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 2,
        height: "100%",
        overflowY: "auto",
        p: 3.75,
      }}
      noValidate
    >
      <Box>
        <Box component="span" sx={{ color: "primary.main", fontSize: "0.75rem", fontWeight: 700, letterSpacing: "0.18em" }}>
          {t("eyebrow")}
        </Box>
        <Box component="h2" sx={{ m: 0, mt: 1, fontSize: "1.75rem", fontWeight: 600 }}>
          {t("title")}
        </Box>
        <Box component="p" sx={{ m: 0, mt: 1, color: "text.secondary", fontSize: "0.875rem", lineHeight: 1.65 }}>
          {t("description")}
        </Box>
      </Box>

      <Input
        label={t("username")}
        placeholder={t("usernamePlaceholder")}
        value={values.username}
        autoComplete="username"
        autoFocus
        startIcon={<Icon name="mdi:account-outline" size={18} />}
        onChange={(event) => setField("username", event.target.value)}
      />
      <Input
        label={t("email")}
        placeholder={t("emailPlaceholder")}
        value={values.email}
        autoComplete="email"
        startIcon={<Icon name="mdi:email-outline" size={18} />}
        onChange={(event) => setField("email", event.target.value)}
      />
      <Input
        label={t("password")}
        placeholder={t("passwordPlaceholder")}
        type="password"
        value={values.password}
        autoComplete="new-password"
        startIcon={<Icon name="mdi:lock-outline" size={18} />}
        onChange={(event) => setField("password", event.target.value)}
      />
      <Input
        label={t("confirmPassword")}
        placeholder={t("confirmPasswordPlaceholder")}
        type="password"
        value={values.confirmPassword}
        autoComplete="new-password"
        startIcon={<Icon name="mdi:lock-check-outline" size={18} />}
        onChange={(event) => setField("confirmPassword", event.target.value)}
      />
      <Input
        label={t("siteTitle")}
        placeholder={t("siteTitlePlaceholder")}
        value={values.siteTitle}
        onChange={(event) => setField("siteTitle", event.target.value)}
      />
      <Input
        label={t("siteUrl")}
        placeholder={t("siteUrlPlaceholder")}
        value={values.siteUrl}
        autoComplete="url"
        onChange={(event) => setField("siteUrl", event.target.value)}
      />

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Button type="submit" size="lg" radius="lg" block loading={loading} loadingText={t("submitting")}>
        {t("submit")}
      </Button>
    </Box>
  );
}
