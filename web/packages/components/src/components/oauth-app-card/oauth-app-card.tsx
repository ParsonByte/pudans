import { useState, type ReactNode } from "react";
import { Avatar, Box, Paper, Skeleton, Typography } from "@mui/material";
import { Icon } from "../icon";

export interface OAuthAppCardProps {
  name: ReactNode;
  description?: ReactNode;
  /** Absolute URL or Iconify name (e.g. "pd:LinkOutlined"). */
  icon?: string;
  loading?: boolean;
  action?: ReactNode;
  className?: string;
}

function isUrl(value: string): boolean {
  return /^(https?:|data:|\/)/.test(value);
}

/**
 * Legacy Frame/OauthAppCard: Paper card with padding 16px, elevation 0, a 1px
 * divider border and the default 12px radius; 48x48 icon (image, primary Avatar
 * with Iconify glyph, or Link fallback) + 16px gap + name (subtitle1/500) and
 * description (body2/text.secondary) lines. Loading keeps the same geometry with
 * circular + text Skeletons.
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
  const showImage = Boolean(icon) && !imageFailed && isUrl(icon as string);

  return (
    <Paper
      className={className}
      elevation={0}
      sx={(theme) => ({
        p: 2,
        boxShadow: "none",
        border: `1px solid ${theme.palette.divider}`,
      })}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
        {loading ? (
          <Skeleton variant="circular" width={48} height={48} />
        ) : showImage ? (
          <Box
            component="img"
            src={icon}
            alt=""
            onError={() => setImageFailed(true)}
            sx={{
              width: 48,
              height: 48,
              maxWidth: 48,
              maxHeight: 48,
              objectFit: "contain",
              borderRadius: 0.5,
            }}
          />
        ) : (
          <Avatar sx={{ width: 48, height: 48, bgcolor: "primary.main" }}>
            {icon ? <Icon name={icon} size={24} /> : <Icon name="pd:LinkOutlined" size={24} />}
          </Avatar>
        )}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {loading ? (
            <>
              <Skeleton variant="text" width="60%" height={24} />
              <Skeleton variant="text" width="80%" height={20} />
            </>
          ) : (
            <>
              <Typography variant="subtitle1" noWrap sx={{ fontWeight: 500 }}>
                {name}
              </Typography>
              {description ? (
                <Typography variant="body2" color="text.secondary" noWrap>
                  {description}
                </Typography>
              ) : null}
            </>
          )}
        </Box>
        {action ? <Box sx={{ display: "inline-flex", flexShrink: 0 }}>{action}</Box> : null}
      </Box>
    </Paper>
  );
}

/**
 * Legacy OAuth connection separator shipped in the same module: 2x16px dotted
 * divider, 28px dashed circle with the 14px Link glyph, then another divider.
 */
export function ConnectingLine() {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
      <Box
        sx={(theme) => ({
          width: 2,
          height: 16,
          backgroundImage: `linear-gradient(${theme.palette.divider} 50%, transparent 50%)`,
          backgroundSize: "2px 6px",
        })}
      />
      <Box
        sx={(theme) => ({
          width: 28,
          height: 28,
          borderRadius: "50%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          bgcolor: "background.paper",
          border: `2px dashed ${theme.palette.divider}`,
          color: "text.secondary",
        })}
      >
        <Icon name="pd:Link" size={14} />
      </Box>
      <Box
        sx={(theme) => ({
          width: 2,
          height: 16,
          backgroundImage: `linear-gradient(${theme.palette.divider} 50%, transparent 50%)`,
          backgroundSize: "2px 6px",
        })}
      />
    </Box>
  );
}
