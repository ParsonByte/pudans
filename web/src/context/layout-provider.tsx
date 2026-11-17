import { type ReactNode, createContext, useContext, useState } from "react";

import { getCookie, setCookie } from "@/lib/cookies";

export type Collapsible = "offcanvas" | "icon" | "none";
export type LayoutVariant = "inset" | "sidebar" | "floating";

const LAYOUT_COLLAPSIBLE_COOKIE_NAME = "pudans-ui-layout-collapsible";
const LAYOUT_VARIANT_COOKIE_NAME = "pudans-ui-layout-variant";
const LAYOUT_COOKIE_MAX_AGE = 60 * 60 * 24 * 7;
const DEFAULT_VARIANT: LayoutVariant = "floating";
const DEFAULT_COLLAPSIBLE: Collapsible = "offcanvas";

type LayoutContextState = {
  resetLayout: () => void;
  defaultCollapsible: Collapsible;
  collapsible: Collapsible;
  setCollapsible: (collapsible: Collapsible) => void;
  defaultVariant: LayoutVariant;
  variant: LayoutVariant;
  setVariant: (variant: LayoutVariant) => void;
};

const LayoutContext = createContext<LayoutContextState | null>(null);

export function LayoutProvider({ children }: { children: ReactNode }) {
  const [collapsible, setCollapsibleState] = useState<Collapsible>(() => {
    const saved = getCookie(LAYOUT_COLLAPSIBLE_COOKIE_NAME) as Collapsible | undefined;
    return saved ?? DEFAULT_COLLAPSIBLE;
  });

  const [variant, setVariantState] = useState<LayoutVariant>(() => {
    const saved = getCookie(LAYOUT_VARIANT_COOKIE_NAME) as LayoutVariant | undefined;
    return saved ?? DEFAULT_VARIANT;
  });

  const setCollapsible = (nextCollapsible: Collapsible) => {
    setCollapsibleState(nextCollapsible);
    setCookie(LAYOUT_COLLAPSIBLE_COOKIE_NAME, nextCollapsible, LAYOUT_COOKIE_MAX_AGE);
  };

  const setVariant = (nextVariant: LayoutVariant) => {
    setVariantState(nextVariant);
    setCookie(LAYOUT_VARIANT_COOKIE_NAME, nextVariant, LAYOUT_COOKIE_MAX_AGE);
  };

  const resetLayout = () => {
    setCollapsible(DEFAULT_COLLAPSIBLE);
    setVariant(DEFAULT_VARIANT);
  };

  return (
    <LayoutContext.Provider
      value={{
        resetLayout,
        defaultCollapsible: DEFAULT_COLLAPSIBLE,
        collapsible,
        setCollapsible,
        defaultVariant: DEFAULT_VARIANT,
        variant,
        setVariant,
      }}
    >
      {children}
    </LayoutContext.Provider>
  );
}

export function useLayout() {
  const context = useContext(LayoutContext);

  if (!context) {
    throw new Error("useLayout 必须在 LayoutProvider 内使用");
  }

  return context;
}
