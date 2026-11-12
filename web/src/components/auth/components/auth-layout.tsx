import { useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useOutlet } from "react-router-dom";
import { CSSTransition, TransitionGroup } from "react-transition-group";
import { Icon } from "@pudans/components";
import { cn } from "@pudans/utils";
import { AuthBranding } from "./auth-branding";
import { AuthCard } from "./auth-card";
import { AuthFooter } from "./auth-footer";
import { AuthTopbar } from "./auth-topbar";
import { useThemeVideo } from "../composables/use-theme-video";
import { useSignedInRedirect } from "../../../hooks/use-signed-in-redirect";
import styles from "./auth-layout.module.css";

const routeTransitionClasses = {
  enter: styles.routeEnter,
  enterActive: styles.routeEnterActive,
  exit: styles.routeExit,
  exitActive: styles.routeExitActive,
};

interface RoutePanelProps {
  children: ReactNode;
  in?: boolean;
  onExited?: () => void;
}

function RoutePanel({ children, in: inProp, onExited }: RoutePanelProps) {
  const nodeRef = useRef<HTMLDivElement>(null);

  return (
    <CSSTransition
      in={inProp}
      nodeRef={nodeRef}
      onExited={onExited}
      timeout={300}
      classNames={routeTransitionClasses}
    >
      <div ref={nodeRef} className={styles.routePanel}>
        {children}
      </div>
    </CSSTransition>
  );
}

/**
 * Persistent auth shell. The video, top bar, branding, footer and route
 * transition stage all live above the router Outlet, so navigating between the
 * auth pages never remounts or restarts the background video.
 */
export function AuthLayout() {
  const { t, i18n } = useTranslation("auth");
  const { themePreference, isDark } = useThemeVideo();
  const location = useLocation();
  const outlet = useOutlet();
  const isLegal = location.pathname.includes("/legal");
  const isDone = location.pathname.includes("/done");
  // Legal documents stay public; signed-in confirmation and every other auth
  // route bounce visitors who already hold a session to the post-login page.
  useSignedInRedirect(!isLegal && !isDone);

  const videoRef = useRef<HTMLVideoElement>(null);
  const [videoPlaying, setVideoPlaying] = useState(true);

  const toggleVideo = (): void => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    if (video.paused) {
      void video.play();
      setVideoPlaying(true);
    } else {
      video.pause();
      setVideoPlaying(false);
    }
  };

  return (
    <main
      className={cn(styles.page, isDark ? styles.dark : undefined)}
      data-app="pudans-auth"
      data-theme-preference={themePreference}
      data-locale={i18n.language}
    >
      <video
        ref={videoRef}
        className={styles.video}
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        poster={`${import.meta.env.BASE_URL}login-bg-poster.jpg`}
        disablePictureInPicture
        aria-hidden="true"
        tabIndex={-1}
        onPlay={() => setVideoPlaying(true)}
        onPause={() => setVideoPlaying(false)}
      >
        <source src={`${import.meta.env.BASE_URL}login-bg.mp4`} type="video/mp4" />
      </video>
      <div className={styles.videoOverlay} aria-hidden="true" />
      <div className={styles.videoTint} aria-hidden="true" />

      <AuthTopbar />
      <AuthBranding showSlogan={!isLegal} />

      {!isLegal ? (
        <button
          type="button"
          className={styles.videoControl}
          aria-label={videoPlaying ? t("videoPause") : t("videoResume")}
          aria-pressed={!videoPlaying}
          onClick={toggleVideo}
        >
          <Icon
            name={videoPlaying ? "mdi:pause-outline" : "mdi:play-outline"}
            size={14}
            className={styles.videoControlIcon}
          />
          {videoPlaying ? t("systemStatus") : t("videoStart")}
        </button>
      ) : null}

      <section className={styles.content}>
        <AuthCard>
          <TransitionGroup component="div" className={styles.routeStage}>
            <RoutePanel key={location.pathname}>{outlet}</RoutePanel>
          </TransitionGroup>
        </AuthCard>
      </section>

      <AuthFooter />
    </main>
  );
}
