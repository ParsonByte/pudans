import { type ReactNode, createContext, useContext, useEffect, useState } from "react";

import { DirectionProvider as ShadcnDirectionProvider } from "@/components/ui/direction";
import { getCookie, removeCookie, setCookie } from "@/lib/cookies";

export type Direction = "ltr" | "rtl";

const DEFAULT_DIRECTION: Direction = "ltr";
const DIRECTION_COOKIE_NAME = "pudans-ui-dir";
const DIRECTION_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

type DirectionContextState = {
  defaultDir: Direction;
  dir: Direction;
  setDir: (dir: Direction) => void;
  resetDir: () => void;
};

const DirectionContext = createContext<DirectionContextState | null>(null);

export function DirectionProvider({ children }: { children: ReactNode }) {
  const [dir, setDirState] = useState<Direction>(
    () => (getCookie(DIRECTION_COOKIE_NAME) as Direction) || DEFAULT_DIRECTION,
  );

  useEffect(() => {
    document.documentElement.setAttribute("dir", dir);
  }, [dir]);

  const setDir = (nextDir: Direction) => {
    setDirState(nextDir);
    setCookie(DIRECTION_COOKIE_NAME, nextDir, DIRECTION_COOKIE_MAX_AGE);
  };

  const resetDir = () => {
    setDirState(DEFAULT_DIRECTION);
    removeCookie(DIRECTION_COOKIE_NAME);
  };

  return (
    <DirectionContext.Provider
      value={{
        defaultDir: DEFAULT_DIRECTION,
        dir,
        setDir,
        resetDir,
      }}
    >
      <ShadcnDirectionProvider dir={dir}>{children}</ShadcnDirectionProvider>
    </DirectionContext.Provider>
  );
}

export function useDirection() {
  const context = useContext(DirectionContext);

  if (!context) {
    throw new Error("useDirection 必须在 DirectionProvider 内使用");
  }

  return context;
}
