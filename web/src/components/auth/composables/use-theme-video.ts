import { usePudansTheme } from "@pudans/shared/theme";
import { useAppSelector } from "../../../redux/store";

/** Bridges the persisted appearance preference with the auth page variables. */
export function useThemeVideo() {
  const themePreference = useAppSelector((state) => state.app.themePreference);
  const { resolvedMode } = usePudansTheme();

  return {
    themePreference,
    resolvedMode,
    isDark: resolvedMode === "dark",
  };
}
