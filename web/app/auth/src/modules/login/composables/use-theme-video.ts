import { usePudansTheme } from "@pudans/components";
import { useAppSelector } from "@web/store/hooks";

/**
 * Bridges the persisted app preference with the auth page's light/dark
 * variables. The video itself is rendered by AuthBranding with loop/cover.
 */
export function useThemeVideo() {
  const themePreference = useAppSelector((state) => state.app.themePreference);
  const { resolvedMode } = usePudansTheme();

  return {
    themePreference,
    resolvedMode,
    isDark: resolvedMode === "dark",
  };
}
