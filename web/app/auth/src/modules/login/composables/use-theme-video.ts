import { usePudansTheme } from "@pudans/components";
import { useAppStore } from "@web/store/app-store";

/**
 * Bridges the persisted app preference with the auth page's light/dark
 * variables. The video itself is rendered by AuthBranding with loop/cover.
 */
export function useThemeVideo() {
  const themePreference = useAppStore((state) => state.themePreference);
  const { resolvedMode } = usePudansTheme();

  return {
    themePreference,
    resolvedMode,
    isDark: resolvedMode === "dark",
  };
}
