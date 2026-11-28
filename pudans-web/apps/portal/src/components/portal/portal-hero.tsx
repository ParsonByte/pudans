import { PortalTitleCanvas } from "./portal-title-canvas";

export function PortalHero() {
  return (
    <header className="portal-hero" data-portal="hero">
      <div className="portal-hero-rule" />
      <PortalTitleCanvas />
      <p className="portal-hero-sub">构想，在这里成真</p>
    </header>
  );
}