import { useMemo, useRef, useState, type ChangeEvent } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Avatar, Box, Button, Chip, Stack, Typography } from "@mui/material";
import TimeAgo from "timeago-react";
import {
  ImageCropper,
  Icon,
  PageContainer,
  useToast,
  type ImageCropperResult,
} from "@pudans/components";
import { useAuth } from "@web/providers/auth-provider";
import { UcPanel } from "../../components/uc-panel";

export function ProfileView() {
  const { t } = useTranslation("uc");
  const toast = useToast();
  const { user } = useAuth();
  const inputRef = useRef<HTMLInputElement>(null);
  const [cropSrc, setCropSrc] = useState<string | null>(null);
  const [avatar, setAvatar] = useState<string | undefined>(user?.avatar);

  const registeredAt = useMemo(() => new Date(Date.now() - 420 * 24 * 3600 * 1000), []);

  const pickFile = (event: ChangeEvent<HTMLInputElement>): void => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setCropSrc(URL.createObjectURL(file));
    event.target.value = "";
  };

  return (
    <PageContainer title={t("profileTitle")}>
      <UcPanel>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={3}
          sx={{ alignItems: { sm: "center" } }}
        >
          <Box sx={{ position: "relative", alignSelf: "flex-start" }}>
            <Avatar
              src={avatar}
              alt={user?.displayName}
              sx={{ width: 96, height: 96, fontSize: 34 }}
            >
              {(user?.displayName ?? "?").slice(0, 1).toUpperCase()}
            </Avatar>
            <Button
              size="small"
              variant="outlined"
              onClick={() => inputRef.current?.click()}
              startIcon={<Icon name="mdi:camera-outline" size={15} />}
              sx={{ mt: 1.5 }}
            >
              {t("profileChangeAvatar")}
            </Button>
            <input ref={inputRef} type="file" accept="image/*" hidden onChange={pickFile} />
          </Box>

          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {user?.displayName || user?.name || t("profileUnknown")}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {user?.email || t("profileNoEmail")}
            </Typography>
            <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: "wrap" }} useFlexGap>
              <Chip size="small" label={user?.name || "—"} />
              <Chip size="small" color="success" variant="outlined" label={t("profileActive")} />
              <Chip
                size="small"
                variant="outlined"
                label={t("profileMemberSince", { time: "" })}
                icon={<Icon name="mdi:calendar-outline" size={14} />}
              />
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1 }}>
              <TimeAgo datetime={registeredAt} /> · {t("profileJoined")}
            </Typography>
          </Box>
        </Stack>
      </UcPanel>

      <Box sx={{ mt: 2 }}>
        <UcPanel>
          <Typography variant="h6" sx={{ mb: 1 }}>
            {t("profileSecuritySummary")}
          </Typography>
          <Alert severity="info">{t("profileSecurityHint")}</Alert>
        </UcPanel>
      </Box>

      {cropSrc ? (
        <ImageCropper
          open
          src={cropSrc}
          onClose={() => setCropSrc(null)}
          onComplete={(result: ImageCropperResult) => {
            setAvatar(result.dataUrl);
            toast.success(t("profileAvatarUpdated"));
          }}
          aspect={1}
          circularCrop
          title={t("profileCropTitle")}
          confirmText={t("profileApply")}
          cancelText={t("profileCancel")}
          maxOutputSize={512}
        />
      ) : null}
    </PageContainer>
  );
}
