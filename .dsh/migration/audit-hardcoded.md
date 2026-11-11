# Hardcoded design values & design-system bypass audit

**Repo:** pudans monorepo (React 18 + Vite + MUI) · **Scope:** web/app/** (auth, console, setup, uc), web/src/**, web/packages/components/src/** · **Date:** 2026-11-16 · **Mode:** read-only audit (no source files modified).

**Design system under audit:** @pudans/components — CSS-variable tokens (styles/variables.css), TS token module (styles/tokens.ts), MUI theme (styles/create-theme.ts). Token definition files, dist/, node_modules/, public/, generated api-client, and theme-provider wiring are excluded.

**Method.** Automated regex scan of 207 source files (7,340 LOC app + 1,581 LOC src + 8,089 LOC component library) for hex/rgb()/hsl() colors, font-family, font-size, border-radius, box-shadow, z-index, motion durations/easings, spacing literals and direct MUI imports. 957 candidate lines were triaged manually: 489 already consume tokens (var(--pd-*) or channel form rgb(var(--pd-*) / a)) and are treated as compliant; 468 raw hits remained; 8 were false positives/resets; 4 duplicate same-line motion matches were collapsed; 37 were MUI import statements; 1 autofill workaround and 2 shadow-channel matches were folded into exempt/manual rows; 7 multi-line declarations missed by the line scan were added manually. Result: **423 hardcoded-value findings** plus **14 direct @mui/material import statements in app code** (437 flagged items total). 22 further MUI imports inside @pudans/components are the expected design-system boundary and are not counted; 1 theme-provider import is excluded as wiring.

Severity reflects design-system impact, not line count: **P0** = dark-mode / accent / theme correctness broken or a parallel token layer; **P1** = a token exists (or a token family is clearly required) and the bypass is visible/architectural; **P2** = off-scale literal that needs a token decision; **P3** = mechanical replacement, low risk.

> Paths are relative to `web/`. Line numbers refer to the tree state at scan time (2026-11-16, ~10:45 +08:00); the tree changed during/after the audit — read section 8 before applying any row. `tokens.ts` already contains uncommitted extensions (zIndex, motionDurations, motionEasing, transitions, borderWidths, fontWeights) while `variables.css` has not been updated to mirror them — see section 5.

---

## 1. Summary

### 1.1 Findings per category and per app/package

| Scope | hex | rgb/hsl | font-family | font-size | radius | shadow | z-index | motion | spacing | Total | Hardcoded share* |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| app/auth | 21 | 41 | 0 | 57 | 18 | 1 | 12 | 7 | 93 | **250** | 91% |
| app/console | 6 | 0 | 1 | 2 | 6 | 0 | 0 | 1 | 12 | **28** | 33% |
| app/setup | 0 | 0 | 0 | 8 | 1 | 0 | 1 | 0 | 15 | **25** | 64% |
| app/uc | 1 | 0 | 1 | 2 | 1 | 0 | 0 | 0 | 1 | **6** | 60% |
| src | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **1** | 100% |
| components | 0 | 8 | 0 | 21 | 9 | 1 | 4 | 10 | 60 | **113** | 23% |
| **total** | **29** | **49** | **2** | **90** | **35** | **2** | **17** | **18** | **181** | **423** | 46% |

\* share of design-value references in that scope that are hardcoded (complement = already using tokens). App auth is ~91% hardcoded; the component library is ~77% tokenized; console ~67% tokenized; setup and UC are small but mostly literal samples (64% / 60% hardcoded). Token-aware references found: **489** (components 389, console 57, auth 25, setup 14, uc 4).

Direct MUI imports (not included above): **14 app import statements** in 11 files. 9 statements in 6 files have an equivalent @pudans/components wrapper (section 4); 5 statements in 5 files are wrapper-less primitives/lab (Box/Stack/Typography/Chip/Timeline) that can stay direct.

### 1.2 Top hotspots by finding count

| # | File | Findings | Dominant categories |
|---:|---|---:|---|
| 1 | app/auth/src/layouts/auth-layout.module.css | 78 | color-fn 38, color-hex 19, z-index 9 |
| 2 | app/auth/src/modules/recovery/components/recovery-form.module.css | 48 | spacing 23, font-size 18, radius 7 |
| 3 | app/auth/src/modules/login/components/login-form.module.css | 47 | spacing 22, font-size 17, radius 6 |
| 4 | app/auth/src/modules/legal/legal-view.module.css | 36 | spacing 23, font-size 13 |
| 5 | app/setup/src/modules/setup/setup-view.module.css | 25 | spacing 15, font-size 8, z-index 1 |
| 6 | app/auth/src/modules/login/components/auth-topbar.module.css | 17 | spacing 10, font-size 4, radius 2 |
| 7 | packages/components/src/layouts/page-container/page-container.module.css | 11 | spacing 9, font-size 2 |
| 8 | packages/components/src/components/settings-panel/settings-panel.module.css | 10 | spacing 7, z-index 1, radius 1 |
| 9 | packages/components/src/components/tabs/tabs.tsx | 10 | color-fn 8, radius 1, font-size 1 |
| 10 | app/auth/src/layouts/auth-card.module.css | 9 | motion 3, color-fn 2, radius 2 |
| 11 | app/auth/src/modules/login/components/auth-branding.module.css | 9 | spacing 5, font-size 3, z-index 1 |
| 12 | app/console/src/modules/settings/components/appearance-settings.tsx | 8 | color-hex 6, font-family 1, radius 1 |
| 13 | packages/components/src/layouts/app-shell/app-shell.module.css | 8 | spacing 4, z-index 2, radius 1 |
| 14 | packages/components/src/components/dropdown-menu/dropdown-menu.module.css | 7 | spacing 5, radius 2 |
| 15 | app/auth/src/modules/login/components/auth-footer.module.css | 6 | spacing 4, z-index 1, font-size 1 |
| 16 | packages/components/src/components/graph/graph.module.css | 6 | font-size 3, spacing 3 |
| 17 | app/console/src/modules/overview/overview-view.module.css | 5 | spacing 4, radius 1 |
| 18 | packages/components/src/components/checkbox/checkbox.module.css | 5 | font-size 3, spacing 2 |
| 19 | packages/components/src/components/input/input.module.css | 5 | spacing 5 |
| 20 | packages/components/src/components/tabs/tabs.module.css | 5 | spacing 3, radius 1, motion 1 |

---

## 2. Detailed findings, sorted by severity

Each row lists every matching line in that file/category; repeated literals are grouped so the table stays readable while remaining exhaustive.

### P0 — theme / token-layer breakage (4 rows)

The auth app maintains its own theme; the component library itself bypasses tokens in one component; UC has a visible dark-mode bug.

| # | File | Literal(s) | Suggested replacement | Why |
|---|---|---|---|---|
| 1 | app/auth/src/layouts/auth-layout.module.css | L6 rgba(255, 255, 255, 0.72), L7 rgba(255, 255, 255, 0.5), L9 rgba(15, 23, 42, 0.12), L11 rgba(15, 23, 42, 0.18), L15 rgba(0, 0, 0, 0.5), L16 rgba(0, 0, 0, 0.36), L17 rgba(0, 0, 0, 0.22), L19 rgba(0, 0, 0, 0.2), L21 rgba(255, 255, 255, 0.88), L23 rgba(255, 255, 255, 0.55), L24 rgba(255, 255, 255, 0.1), L25 rgba(255, 255, 255, 0.2), L28 rgba(0, 0, 0, 0.28), L29 rgba(0, 0, 0, 0), L33 rgba(0, 0, 0, 0.3), L34 rgba(0, 0, 0, 0), L38 rgba(0, 0, 0, 0.18), L41 rgba(0, 0, 0, 0.2), L44 rgba(0, 0, 0, 0.35), L45 rgba(0, 0, 0, 0.25), L56 rgba(255, 255, 255, 0.62), L57 rgba(255, 255, 255, 0.42), L59 rgba(255, 255, 255, 0.62), L60 rgba(255, 255, 255, 0.42), L62 rgba(255, 255, 255, 0.11), L64 rgba(255, 255, 255, 0.16), L68 rgba(4, 6, 10, 0.02), L69 rgba(4, 6, 10, 0.1), L70 rgba(4, 6, 10, 0.38), L71 rgba(8, 8, 9, 0.9), L74 rgba(0, 0, 0, 0.5), L76 rgba(255, 255, 255, 0.84), L78 rgba(255, 255, 255, 0.44), L79 rgba(255, 255, 255, 0.08), L80 rgba(255, 255, 255, 0.14), L85 rgba(0, 0, 0, 0.24), L88 rgba(0, 0, 0, 0.18), L91 rgba(0, 0, 0, 0.3) | rgba(255,255,255,a) -> rgb(var(--pd-neutral-0) / a); rgba(0,0,0,a) -> rgb(var(--pd-neutral-1000) / a); rgba(15,23,42,a) -> var(--pd-border-default/strong); video overlay gradients stay local but build from --pd-neutral channels. | Same parallel-token problem: 38 literal rgba() values bake the login theme directly into CSS; shadows, borders and overlays are outside --pd-shadow-*/--pd-border-* control and do not match the component library dark surfaces. |
| 2 | app/auth/src/layouts/auth-layout.module.css | L2 #101114, L3 #5f6368, L4 #8a8f98, L5 #ffffff, L8 #ffffff, L10 #f7f8fa, L12 #f1f2f4, L20 #1976d2, L22 #ffffff, L50 #f5f6f7, L55 #f7f8fa, L58 #ffffff, L61 #1e1e20, L63 #18181a, L65 #242426, L72 #0a0a0a, L75 #90caf9, L77 #ffffff, L93 #0a0a0a | Replace local vars with global tokens: L2 --pd-text-primary; L3 --pd-text-secondary; L4 --pd-text-tertiary; L5/L22/L58/L77 --pd-text-inverse or rgb(var(--pd-neutral-0)); L8 --pd-bg-surface; L10 --pd-bg-subtle; L12 --pd-bg-muted; L20 --pd-brand-primary; L50 --pd-bg-canvas; L55 --pd-text-primary; L61 --pd-bg-elevated; L63 --pd-bg-canvas; L65 --pd-bg-muted; L72/L93 --pd-neutral-950 (dark canvas); L75 --pd-brand-primary (dark). Keep only the video-overlay values local. | The page defines a second theme system: 25 --login-* variables hand-written for light (L2-L45) and dark (L55-L93). It does not follow the accent color derived by buildAccentColor(), duplicates the brand ramp (#1976d2/#90caf9) and already drifts from the palette (#101114 vs #000, #8a8f98 vs #8a8a8a). |
| 3 | app/uc/src/modules/security/security-view.tsx | L65 #fff | backgroundColor: "#fff" -> rgb(var(--pd-bg-surface)); optionally replace the wrapper Box with the library Card and use a radius token. | Hardcoded white QR-code surface ignores [data-theme=dark]: the 2FA panel stays white while the rest of UC goes dark. Visible dark-mode bug, not just token drift. |
| 4 | packages/components/src/components/tabs/tabs.tsx | L47 rgba(255, 255, 255, 0.08), L47 rgba(0, 0, 0, 0.04), L71 rgba(144, 202, 249, 0.16), L71 rgba(25, 118, 210, 0.10), L76 rgba(144, 202, 249, 0.22), L76 rgba(25, 118, 210, 0.16), L81 rgba(144, 202, 249, 0.32), L82 rgba(25, 118, 210, 0.24) | Collapse the light/dark branches: hover -> rgb(var(--pd-bg-hover)); selected surface -> rgb(var(--pd-brand-soft)); selected hover -> rgb(var(--pd-brand-primary) / 0.16); focus ring -> var(--pd-shadow-focus) or rgb(var(--pd-brand-primary) / 0.24). | The design-system Tabs hardcodes 8 rgba() values in both palette modes. Because the accent color is user-configurable these fixed blues (25,118,210 / 144,202,249) never follow the accent and duplicate the token layer. |

### P1 — design-system contract bypasses (27 rows)

Token exists (or a token family is clearly needed) and the bypass is architectural or visible. Grouped: raw colors, motion, elevation, z-index, font-family, MUI shape-scale radius.

| # | File | Literal(s) | Suggested replacement | Why |
|---|---|---|---|---|
| 1 | app/auth/src/layouts/auth-card.module.css | L14 rgba(15, 23, 42, 0.12), L17 rgba(0, 0, 0, 0.2) | rgba(15,23,42,.12) -> var(--pd-border-default); rgba(0,0,0,.2) -> var(--pd-shadow-*) fallback; drop the fallback literals in favour of the global token | Fallback values for --login-card-border / --login-shadow are hardcoded and disconnected from --pd-border-*/--pd-shadow-*. |
| 2 | app/auth/src/layouts/auth-card.module.css | L16 #ffffff, L18 #101114 | #ffffff -> var(--pd-bg-surface); #101114 -> var(--pd-text-primary) | var() fallbacks in L14/L16/L17/L18 duplicate the local token layer; the fallback path renders light-mode colors if --login-* is missing. |
| 3 | app/auth/src/layouts/auth-card.module.css | L21 cubic-bezier(0.4, 0, 0.2, 1), L22 cubic-bezier(0.4, 0, 0.2, 1), L23 cubic-bezier(0.4, 0, 0.2, 1) | 300ms -> var(--pd-duration-slow); cubic-bezier(0.4,0,0.2,1) -> var(--pd-ease-standard) | Card expand/collapse writes duration + easing literally 3x, while the same file already uses --pd-duration-fast/--pd-ease-standard at L177-179. |
| 4 | app/auth/src/layouts/auth-layout.module.css | L223, L224, L244, L245 = 300ms | var(--pd-duration-slow) (300ms) for both route enter/exit transitions | Multi-line transition shorthand writes the duration literally; bypasses --pd-duration-* so reduced-motion and speed changes cannot retune it. |
| 5 | app/auth/src/layouts/auth-layout.module.css | L98 2, L128 0, L139 1, L149 1, L159 20, L213 2, L219 2, L228 1, L237 1 | Add the missing --pd-z-* CSS variables (section 5) and map this layer onto one of them; keep page-local stacking documented if it must stay local. | Login page numbers its layers manually (0/1/2/3/5/20) while the app is starting to use MUI z-index conventions; no --pd-z-* token family exists in variables.css. |
| 6 | app/auth/src/modules/login/components/auth-branding.module.css | L5 2 | Add the missing --pd-z-* CSS variables (section 5) and map this layer onto one of them; keep page-local stacking documented if it must stay local. | Slogan layer 2 is a local constant; cannot be reasoned about against nav/footer/video layers. |
| 7 | app/auth/src/modules/login/components/auth-footer.module.css | L6 3 | Add the missing --pd-z-* CSS variables (section 5) and map this layer onto one of them; keep page-local stacking documented if it must stay local. | Footer layer 3 duplicates a magic number that also lives in auth-layout/topbar. |
| 8 | app/auth/src/modules/login/components/auth-topbar.module.css | L6 5 | Add the missing --pd-z-* CSS variables (section 5) and map this layer onto one of them; keep page-local stacking documented if it must stay local. | Top bar 5 vs footer 3 vs content 2 are local magic numbers with no token anchor. |
| 9 | app/auth/src/modules/login/components/login-form.module.css | L75 rgba(0, 0, 0, 0.08) | rgba(0,0,0,.08) -> var(--pd-shadow-sm) / rgb(var(--pd-neutral-1000) / .08) | Active-tab elevation uses a literal tint on the same line as the literal box-shadow below. |
| 10 | app/auth/src/modules/login/components/login-form.module.css | L75 0 1px 3px rgba(0, 0, 0, 0.08) | 0 1px 3px rgba(0,0,0,.08) -> var(--pd-shadow-sm) | A one-off elevation value that almost matches --pd-shadow-sm; bypasses the elevation scale. |
| 11 | app/console/src/modules/settings/components/appearance-settings.tsx | L83 "monospace" | fontFamily: "monospace" -> fontFamily: "var(--pd-font-mono)" (fontFamily.mono token) | Accent swatch labels fall back to the browser monospace stack instead of the design-system mono family. |
| 12 | app/console/src/modules/settings/components/appearance-settings.tsx | L88 2 | borderRadius: 2 (sx) -> var(--pd-radius-xl) / --pd-radius-2xl, or render the accent swatch as a Card | MUI sx multiplies numeric borderRadius by theme.shape.borderRadius (12px): 2 means 24px, which is off the radius scale and inconsistent with cards (12px). |
| 13 | app/console/src/modules/settings/components/captcha-settings.tsx | L79 2 | borderRadius: 2 (sx) -> var(--pd-radius-xl) (16px) or --pd-radius-2xl (20px) | Same shape-scale bypass: 2 x 12px = 24px, off the radius token scale. |
| 14 | app/console/src/modules/settings/components/config-state.module.css | L51 1.4s | 1.4s -> --pd-duration-skeleton (1400ms); ease -> var(--pd-ease-standard) (or linear) | Skeleton shimmer duplicates the 1.4s used by the library button but has no token; the ease keyword is not one of the --pd-ease-* curves. |
| 15 | app/setup/src/modules/setup/setup-view.module.css | L17 20 | Add the missing --pd-z-* CSS variables (section 5) and map this layer onto one of them; keep page-local stacking documented if it must stay local. | Fixed settings cluster uses z-index 20 with no token and no relation to overlays. |
| 16 | app/uc/src/modules/security/security-view.tsx | L74 "monospace" | fontFamily: "monospace" -> fontFamily: "var(--pd-font-mono)" | TOTP secret uses an ad-hoc monospace stack; --pd-font-mono (Menlo/JetBrains/Fira fallbacks) already exists. |
| 17 | app/uc/src/modules/security/security-view.tsx | L62 3 | sx borderRadius: 3 -> var(--pd-radius-lg) (12px) or --pd-radius-xl (16px) | 3 x theme.shape.borderRadius = 36px; the radius scale stops at 20px, so this corner is unmanaged and larger than any other UC surface. |
| 18 | packages/components/src/components/animate-height/animate-height.tsx | L24 = duration = 220 (ms), L25 = cubic-bezier(0.4, 0, 0.2, 1) | Default duration from a motion token (200ms / --pd-duration-shorter) and motionEasing.standard | Disclosure primitive hardcodes its own motion budget; consumers cannot align it with --pd-duration-*. |
| 19 | packages/components/src/components/button/button.module.css | L165 700ms, L184 1.4s | 700ms -> --pd-duration-spin (motionDurations.spin=900 in uncommitted tokens.ts); 1.4s -> --pd-duration-skeleton (1400) | Spinner speed and reduced-motion pulse are raw literals; infinite-animation durations have no token yet (gap, section 5). |
| 20 | packages/components/src/components/icon/icon.module.css | L10 1s, L21 2s | 1s spin -> --pd-duration-spin token; reduced-motion 2s -> use a token or stop the animation | Icon spin is a raw 1s/2s pair; reduced-motion currently still animates, just slower. |
| 21 | packages/components/src/components/settings-panel/settings-panel.module.css | L83-L85 = 0 12px 32px rgb(0 0 0 / 0.12), 0 2px 8px rgb(0 0 0 / 0.06) | var(--pd-shadow-lg), or add --pd-shadow-float for the sticky action bar | Floating save bar re-invents elevation with black-alpha shadows; bypasses --pd-shadow-* and cannot adapt to the dark-mode shadow set. |
| 22 | packages/components/src/components/settings-panel/settings-panel.module.css | L65 8 | z-index: 8 -> --pd-z-sticky | Sticky save bar layer is a bare literal and lower than the mobile shell drawer layers, so the drawer covers it. |
| 23 | packages/components/src/components/table/table.module.css | L81 1 | z-index: 1 -> --pd-z-sticky (or a local --pd-z-table-sticky) | Sticky header layer is a bare literal inside the table wrapper; it cannot participate in a z-index scale. |
| 24 | packages/components/src/components/tabs/tabs.module.css | L55 cubic-bezier(0.4, 0, 0.2, 1) | animation: panel-in var(--pd-duration-base) var(--pd-ease-standard), or add --pd-duration-panel: 280ms | 280ms is not on the 150/200/250/300 motion scale and is a literal; pick a token or add the step once. |
| 25 | packages/components/src/layouts/app-shell/app-shell.module.css | L358 50, L388 40 | mobile drawer -> --pd-z-modal (1400) or a new --pd-z-drawer; click-away overlay one step below (--pd-z-overlay 1300); app bar -> --pd-z-sticky | Shell layers (50/40) were copied from the legacy app; zIndex exists in uncommitted tokens.ts but no CSS variables have been generated. |
| 26 | packages/components/src/layouts/app-shell/side-nav-item.module.css | L24 cubic-bezier(0.4, 0, 0.2, 1), L25 cubic-bezier(0.4, 0, 0.2, 1) | 250ms -> var(--pd-duration-base); cubic-bezier -> var(--pd-ease-standard) | Sidebar items bypass the motion tokens that app-shell.module.css already uses next door. |
| 27 | packages/components/src/layouts/sidebar-layout/sidebar-layout.tsx | L75 = duration={200} | motionDurations.shorter (200ms) from the token module, or the AnimateHeight default | Layout primitive passes a raw millisecond value; 200ms matches the scale but cannot be retuned centrally. |


### P2 — off-scale or product literals needing a token decision (61 rows)

The literal does not map 1:1 to an existing token (or maps only partly). Either round to the nearest token with design sign-off or add a token step (section 5). The auth app dominates: its bespoke login styling is the main source of 9-11.5px type and 3-30px spacing.

| # | File | Literal(s) | Suggested replacement | Why |
|---|---|---|---|---|
| 1 | app/auth/src/layouts/auth-layout.module.css | L169 11px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 2 | app/auth/src/layouts/auth-layout.module.css | L103 112px clamp(24px, 7vw, 112px) 96px, L108 clamp(24px, 5vw, 64px), L116 96px 16px 80px, L157 80px, L162 7px, L163 7px 12px | Keep clamp()/vw responsive distances local and documented; map fixed 80/96/112px and 7px/12px control offsets to --pd-space-* or auth-layout constants. | Auth layout mixes viewport-scaled distances with a 7px control offset that does not map to the 4px token scale. |
| 3 | app/auth/src/modules/legal/legal-view.module.css | L24 9.5px, L32 22px, L44 11px, L66 11.5px, L92 9px, L100 clamp(24px, 3vw, 32px), L109 10.5px, L117 12.5px, L128 13.5px, L135 12.5px, L144 10.5px, L159 19px, L163 10.5px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 4 | app/auth/src/modules/legal/legal-view.module.css | L7 28px 28px 22px, L16 18px, L17 16px, L22 0 0 6px, L40 5px, L42 3px, L55 2px, L59 2px, L64 9px 4px, L76 12px 6px 4px 2px, L86 0 0 6px, L90 0 0 8px, L107 8px 0 0, L113 18px 0 0, L114 18px, L122 22px, L126 0 0 7px, L133 0 0 8px, L140 26px 0 0, L141 14px, L150 20px 16px 18px, L155 8px, L167 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 5 | app/auth/src/modules/login/components/auth-branding.module.css | L14 10px, L25 clamp(32px, 4.1vw, 58px), L39 clamp(30px, 4.6vw, 44px) | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 6 | app/auth/src/modules/login/components/auth-branding.module.css | L3 clamp(28px, 6vw, 104px), L4 clamp(88px, 12vh, 132px), L12 0 0 16px, L22 2px, L35 100px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 7 | app/auth/src/modules/login/components/auth-footer.module.css | L14 10.5px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 8 | app/auth/src/modules/login/components/auth-footer.module.css | L10 16px, L11 18px 24px, L32 8px 14px, L33 14px 16px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 9 | app/auth/src/modules/login/components/auth-topbar.module.css | L43 17px, L58 14px, L65 9px, L85 12px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 10 | app/auth/src/modules/login/components/auth-topbar.module.css | L40 10px, L103 9px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 11 | app/auth/src/modules/login/components/auth-topbar.module.css | L10 20px, L11 22px clamp(20px, 4vw, 56px), L20 14px, L27 10px, L52 2px, L75 4px, L82 6px, L83 4px, L140 16px, L144 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 12 | app/auth/src/modules/login/components/login-form.module.css | L20 10px, L29 28px, L38 12.5px, L60 12px, L112 16px, L119 12.5px, L126 10.5px, L133 11.5px, L146 13px, L183 11px, L213 11.5px, L230 11px, L248 13px, L255 10.5px, L274 10px, L302 11.5px, L331 9.5px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 13 | app/auth/src/modules/login/components/login-form.module.css | L49 10px, L56 7px, L104 16px, L143 10px, L194 6px, L298 9px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 14 | app/auth/src/modules/login/components/login-form.module.css | L6 30px, L15 8px, L45 3px, L46 24px, L47 3px, L81 16px, L82 20px, L90 8px, L92 28px 0 20px, L102 6px, L124 4px 0 0, L131 7px, L181 6px 0 0, L208 -4px, L228 16px, L237 6px, L247 2px, L272 10px, L288 8px, L295 7px, L326 24px, L330 10px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 15 | app/auth/src/modules/recovery/components/recovery-form.module.css | L20 10px, L29 27px, L38 12.5px, L67 11px, L91 10.5px, L128 11.5px, L141 13px, L204 11.5px, L248 10px, L271 11.5px, L283 13px, L295 11.5px, L330 15px, L338 11.5px, L348 24px, L361 9.5px, L367 10.5px, L371 9.5px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 16 | app/auth/src/modules/recovery/components/recovery-form.module.css | L64 50%, L138 10px, L200 0 10px 10px 0, L231 6px, L268 8px, L311 14px, L322 50% | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 17 | app/auth/src/modules/recovery/components/recovery-form.module.css | L6 30px, L15 8px, L45 26px, L53 8px, L109 14px 8px 0, L121 18px, L122 26px, L126 7px, L197 0 12px, L246 10px, L255 5px, L266 10px 12px, L282 2px, L291 6px, L293 18px, L307 8px, L308 34px, L309 30px 16px, L328 4px 0 0, L344 24px, L356 5px, L357 5px, L366 0 9px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. |
| 18 | app/console/src/layouts/console-layout.module.css | L10 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. |
| 19 | app/console/src/modules/overview/overview-view.module.css | L32 2px, L64 20px 22px 22px, L70 6px, L89 4px 0 0 | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. |
| 20 | app/console/src/modules/overview/overview-view.tsx | L305 12 | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. |
| 21 | app/console/src/modules/settings/components/appearance-settings.tsx | L34 #1976d2, L34 #0f766e, L34 #7c3aed, L34 #db2777, L34 #ea580c, L34 #475569 | Product data, not tokens: derive #1976d2 from brandPalette[500]/lightTokens.brand; keep the other five accent presets as curated palette options (promote to tokens only if they become brand options). | Accent presets are user-selectable values, so they cannot map 1:1 to semantic tokens, but the default duplicates the brand token and will drift when the brand changes. |
| 22 | app/console/src/modules/settings/components/readonly-panel.module.css | L32 6px, L38 2px 8px, L50 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. |
| 23 | app/console/src/modules/settings/components/tag-input.module.css | L7 6px 8px, L25 2px, L26 2px 4px 2px 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. |
| 24 | app/setup/src/modules/setup/setup-view.module.css | L73 30px, L83 11px, L108 22px, L117 13px, L130 11px, L147 13px, L159 11px, L176 26px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Setup page is color-tokenized but still hardcodes the type and space scale. |
| 25 | app/setup/src/modules/setup/setup-view.module.css | L7 24px, L8 48px 24px, L15 20px, L16 20px, L20 4px, L67 10px, L100 8px, L101 20px, L124 20px, L125 24px, L140 16px, L157 -8px 0 0, L166 12px, L167 12px, L172 32px 16px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Setup page is color-tokenized but still hardcodes the type and space scale. |
| 26 | app/uc/src/layouts/uc-layout.module.css | L10 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | UC surface mixes raw MUI and literals; it cannot follow compact/density changes yet. |
| 27 | app/uc/src/modules/profile/profile-view.tsx | L46 34 | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | UC surface mixes raw MUI and literals; it cannot follow compact/density changes yet. |
| 28 | packages/components/src/components/alert/alert.module.css | L40 1px, L59 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 29 | packages/components/src/components/animate-height/animate-height.tsx | L25 cubic-bezier(0.4, 0, 0.2, 1) | Use var(--pd-duration-*) / var(--pd-ease-*) where a token exists; values without tokens need a new step or the nearest token. | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 30 | packages/components/src/components/avatar/avatar.module.css | L32 10px, L38 12px, L44 14px, L50 17px | Avatar initials track the avatar size (24/32/40/48px): add --pd-avatar-* size tokens or derive initials from --pd-text-xs/base. | Per-size initials typography is hardcoded and disconnected from both the avatar size classes and --pd-text-*. |
| 31 | packages/components/src/components/avatar/avatar.tsx | L81 Math.max(10 | No action required for now: only the numeric-size fallback computes fontSize as Math.max(10, round(size * 0.36)); if numeric avatar sizes become tokens, move this ratio next to them. | Computed initials size is functional sizing, not a design literal; kept here only so the avatar sizing story is complete. |
| 32 | packages/components/src/components/badge/badge.module.css | L15 11px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 33 | packages/components/src/components/badge/badge.module.css | L14 0 7px, L20 0 9px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 34 | packages/components/src/components/chart/chart.tsx | L76 12, L81 12, L86 12 | 12px -> var(--pd-text-xs) (12px) for tooltip/axis/legend; chart label sizes should follow --pd-text-*. | Recharts style objects hardcode 12px three times instead of the existing 12px token. |
| 35 | packages/components/src/components/chart/chart.tsx | L70 8px 10px | Use --pd-space-2 (8px) for the wrapper and add a chart-tooltip padding token if the 8px 10px box is intentional. | Chart tooltip padding is a bespoke spacing pair inside a CSSProperties object; no token covers it. |
| 36 | packages/components/src/components/checkbox/checkbox.module.css | L55 18px, L59 20px, L63 24px | These are MUI SVG glyph sizes, not text: add --pd-icon-sm/md/lg (18/20/24px) or derive from the control size tokens. | Checkbox glyph sizes are expressed as font-size on MuiSvgIcon-root; no icon-size token exists, so they cannot follow the control-size scale. |
| 37 | packages/components/src/components/checkbox/checkbox.module.css | L18 4px, L39 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 38 | packages/components/src/components/dialog/dialog.module.css | L67 4px 0 0, L75 -4px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 39 | packages/components/src/components/dropdown-menu/dropdown-menu.module.css | L28 4px, L40 4px 0, L49 0 4px, L50 7px 8px, L115 4px 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 40 | packages/components/src/components/graph/graph.module.css | L35 11px, L89 13px, L96 11px | Graph label sizes (11/13px) need chart-label tokens or nearest --pd-text-xs (12px) with a documented scale. | SVG text sizes are off the type scale (11/13px) and will not follow --pd-text-*. |
| 41 | packages/components/src/components/graph/graph.module.css | L105 4px, L106 4px, L116 0 7px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 42 | packages/components/src/components/highlight-text/highlight-text.module.css | L7 0 2px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 43 | packages/components/src/components/input/input.module.css | L8 0 0 8px, L33 0 12px, L84 6px 0 0, L139 8px, L143 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 44 | packages/components/src/components/otp-input/otp-input.module.css | L4 6px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 45 | packages/components/src/components/select/select.module.css | L8 0 0 8px, L34 0 12px, L73 6px 0 0 | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 46 | packages/components/src/components/settings-panel/settings-panel.module.css | L21 8px, L30 4px 0, L46 2px, L51 2px 0 0, L69 12px 0 4px, L79 10px 12px 10px 18px, L93 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 47 | packages/components/src/components/stat-card/stat-card.module.css | L41 28px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 48 | packages/components/src/components/stat-card/stat-card.module.css | L65 10px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 49 | packages/components/src/components/stat-card/stat-card.module.css | L29 6px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 50 | packages/components/src/components/switch/switch.module.css | L46 2px, L62 4px, L66 6px, L70 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 51 | packages/components/src/components/tabs/tabs.module.css | L19 4px 12px, L34 6px, L45 0 6px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 52 | packages/components/src/components/tooltip/tooltip.module.css | L2 6px 9px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 53 | packages/components/src/components/tree-view/tree-view.module.css | L34 18px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 54 | packages/components/src/components/tree-view/tree-view.module.css | L41 6px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 55 | packages/components/src/layouts/app-shell/app-shell.module.css | L72 9px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 56 | packages/components/src/layouts/app-shell/app-shell.module.css | L48 10px, L314 3px 4px, L355 8px, L357 8px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 57 | packages/components/src/layouts/app-shell/side-nav-item.module.css | L12 90px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 58 | packages/components/src/layouts/app-shell/side-nav-item.module.css | L5 14px, L8 4px 4px 4px 28px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 59 | packages/components/src/layouts/page-container/page-container.module.css | L31 28px, L80 24px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 60 | packages/components/src/layouts/page-container/page-container.module.css | L21 30px 32px 22px, L42 6px, L48 0 32px, L54 0 32px, L64 24px 32px 32px, L76 20px 18px 16px, L84 0 18px, L88 0 18px, L92 18px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. |
| 61 | src/store/app-slice.ts | L8 #1976d2 | import { brandPalette } from '@pudans/components'; DEFAULT_ACCENT_COLOR = brandPalette[500] (single source of truth). | Default accent duplicates the brand ramp; a brand update changes CSS tokens but not the persisted default. |

### P3 — mechanical replacements, low risk (18 rows)

Every literal in these rows maps exactly to an existing token; no visual change expected. Batch in one PR.

| # | File | Literal(s) | Suggested replacement | Why |
|---|---|---|---|---|
| 1 | app/auth/src/layouts/auth-card.module.css | L15 20px, L56 16px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. Mechanical replacement, low risk. |
| 2 | app/auth/src/layouts/auth-layout.module.css | L165 999px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Auth app keeps a parallel local token layer (P0); the literal is outside the global token system and will not follow dark mode / accent / density changes. Mechanical replacement, low risk. |
| 3 | app/console/src/components/console-placeholder.module.css | L8 12px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. Mechanical replacement, low risk. |
| 4 | app/console/src/layouts/console-layout.module.css | L2 14px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. Mechanical replacement, low risk. |
| 5 | app/console/src/modules/overview/overview-view.module.css | L65 12px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. Mechanical replacement, low risk. |
| 6 | app/console/src/modules/settings/components/config-state.module.css | L26 4px 0 0 | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. Mechanical replacement, low risk. |
| 7 | app/console/src/modules/settings/components/readonly-panel.module.css | L39 9999px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. Mechanical replacement, low risk. |
| 8 | app/console/src/modules/settings/components/tag-input.module.css | L42 9999px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Console CSS is mostly tokenized (57 token refs vs 28 literals); this is leftover residue that blocks retuning the scale in one place. Mechanical replacement, low risk. |
| 9 | app/setup/src/modules/setup/setup-view.module.css | L32 8px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Setup page is color-tokenized but still hardcodes the type and space scale. Mechanical replacement, low risk. |
| 10 | app/uc/src/layouts/uc-layout.module.css | L2 14px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | UC surface mixes raw MUI and literals; it cannot follow compact/density changes yet. Mechanical replacement, low risk. |
| 11 | packages/components/src/components/dropdown-menu/dropdown-menu.module.css | L30 8px, L52 8px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 12 | packages/components/src/components/pagination/pagination.module.css | L7 4px | Replace 4/8/12/16/20/24/32/40/48/64px with --pd-space-1/2/3/4/5/6/8/10/12; other values need the nearest token or a new --pd-space-* step (design sign-off). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 13 | packages/components/src/components/settings-panel/settings-panel.module.css | L102 9999px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 14 | packages/components/src/components/tabs/tabs.module.css | L46 9999px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 15 | packages/components/src/components/tabs/tabs.tsx | L54 14 | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 16 | packages/components/src/components/tabs/tabs.tsx | L52 9999 | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 17 | packages/components/src/components/tree-view/tree-view.module.css | L11 8px | Replace 4/6/8/12/16/20/9999px with --pd-radius-xs/sm/md/lg/xl/2xl/full; 7/9/10/14/15/18/24/36/90px have no token (nearest or new step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |
| 18 | packages/components/src/layouts/app-shell/app-shell.module.css | L75 14px | Replace 12/13/14/15/16/20/24/30px with --pd-text-xs/sm/base/md/lg/xl/2xl/3xl; 9-11.5px, 12.5/13.5, 17/18/19/22/26/27/28/34px have no token (nearest or new --pd-text-* step). | Design-system source bypasses its own tokens here; every app inherits the drift. Mechanical replacement, low risk. |

---

## 3. Remediation checklist (grouped by file)

**423 findings across 51 files.** Phase A = safe mechanical token substitution (no visual change); Phase B = mechanical but blocked on/coordinated with new token variables; Phase C = needs design judgment.

### 3.1 Apps

| File | Findings | Phase A — safe mechanical | Phase B — token coordination | Phase C — design judgment |
|---|---|---|---|---|
| app/auth/src/layouts/auth-layout.module.css | 78 (color-fn 38, color-hex 19, z-index 9, spacing 6, radius 1, font-size 1, motion 4) | 19 hex + 12 channel-safe rgba are near-token; spacing 80/96/112 nearest token; radius 999px -> --pd-radius-full | 3 motion durations -> --pd-duration-slow; 9 z-index -> new --pd-z-* | Local --login-* theme decomposition; video overlay gradients/tints; 7px/112px layout distances |
| app/auth/src/layouts/auth-card.module.css | 9 (motion 3, color-fn 2, radius 2, color-hex 2) | #ffffff/#101114 fallbacks; 20px -> 2xl, 16px -> xl; 300ms/easing | - | 460x660 / 800x720 card geometry is layout data (keep local) |
| app/auth/src/modules/login/components/login-form.module.css | 47 (spacing 22, font-size 17, radius 6, color-fn 1, shadow 1) | spacing 8/16/24, type 12/13/16, radius 16/6, shadow -> --pd-shadow-sm | - | 9.5-12.5px type, 7/9/10px radius, 3/6/7/10px spacing, 46px control height |
| app/auth/src/modules/recovery/components/recovery-form.module.css | 48 (spacing 23, font-size 18, radius 7) | spacing 8/16/24, type 12/13/15, radius 6/8 | - | 9.5-13.5px type, 5/7px spacing, 46/58px control sizes, 50% circles |
| app/auth/src/modules/legal/legal-view.module.css | 36 (spacing 23, font-size 13) | spacing 8/16/18, type 12.5/13.5 nearest | - | clamp() display sizes, 9-11.5px type, 2-7px micro spacing |
| app/auth/src/modules/login/components/auth-topbar.module.css | 17 (spacing 10, font-size 4, radius 2, z-index 1) | spacing 4/8/12/16/20, type 12/14/16, radius 10/9 nearest | z-index 5 | 17px/9px type, 10px/9px radius |
| app/auth/src/modules/login/components/auth-branding.module.css | 9 (spacing 5, font-size 3, z-index 1) | - | z-index 2 | clamp() slogan display type, viewport-positioned offsets |
| app/auth/src/modules/login/components/auth-footer.module.css | 6 (spacing 4, z-index 1, font-size 1) | spacing 8/14/16/18/24 | z-index 3 | 14/18px spacing |
| app/setup/src/modules/setup/setup-view.module.css | 25 (spacing 15, font-size 8, z-index 1, radius 1) | spacing 4/8/12/16/20/24/32, type 13/30, radius 8 | z-index 20 | 11/22/26px type, 10px gaps |
| app/console/src/modules/settings/components/appearance-settings.tsx | 8 (color-hex 6, font-family 1, radius 1) | fontFamily -> var(--pd-font-mono); #1976d2 -> brandPalette[500] | sx radius 2 (=24px) -> radius token | Accent preset palette #0f766e/#7c3aed/#db2777/#ea580c/#475569 is product data — keep and document |
| app/console/src/modules/settings/components/captcha-settings.tsx | 1 (radius 1) | - | sx radius 2 -> token | Preview panel geometry |
| app/console/src/modules/settings/components/config-state.module.css | 2 (spacing 1, motion 1) | spacing 4px -> --pd-space-1 | motion 1.4s -> new skeleton duration token | shimmer keyframes |
| app/console/src/modules/settings/components/readonly-panel.module.css | 4 (spacing 3, radius 1) | spacing 8 -> space-2; radius 9999 -> full | - | 2px/6px chip spacing |
| app/console/src/modules/settings/components/tag-input.module.css | 4 (spacing 3, radius 1) | radius 9999 -> full; spacing 8 -> space-2 | - | 2px/4px/6px chip spacing |
| app/console/src/layouts/console-layout.module.css | 2 (font-size 1, spacing 1) | type 14 -> --pd-text-base | - | 2px icon gap |
| app/uc/src/layouts/uc-layout.module.css | 2 (font-size 1, spacing 1) | type 14 -> --pd-text-base | - | 2px icon gap |
| app/uc/src/modules/profile/profile-view.tsx | 1 (font-size 1) | - | - | Avatar sx width/height 96 + fontSize 34 -> token/display size |
| app/uc/src/modules/security/security-view.tsx | 3 (radius 1, color-hex 1, font-family 1) | fontFamily -> var(--pd-font-mono) | sx radius 3 (=36px) -> token; #fff -> var(--pd-bg-surface) is P0 | 168px QR size, monospace secret display |
| app/console/src/modules/overview/overview-view.tsx | 1 (font-size 1) | - | - | sx fontSize 12 -> var(--pd-text-xs); Timeline density |
| src/store/app-slice.ts | 1 (color-hex 1) | #1976d2 -> brandPalette[500] | - | - |

### 3.2 Component library (@pudans/components)

| File | Findings | Phase A — safe mechanical | Phase B — token coordination | Phase C — design judgment |
|---|---|---|---|---|
| packages/components/src/components/tabs/tabs.tsx | 10 (color-fn 8, radius 1, font-size 1) | type 14 -> base; radius 9999 -> full | 8 rgba -> channel tokens (P0) | light/dark branch collapse to token channels |
| packages/components/src/components/tabs/tabs.module.css | 5 (spacing 3, radius 1, motion 1) | radius 9999 -> full | motion 280ms -> nearest/new duration | 6px label gap |
| packages/components/src/layouts/page-container/page-container.module.css | 11 (spacing 9, font-size 2) | spacing 24/32/20, type 24 -> 2xl | - | 30/32/22 header padding, 18px mobile padding, 28px title (display size) |
| packages/components/src/layouts/app-shell/app-shell.module.css | 8 (spacing 4, z-index 2, radius 1, font-size 1) | spacing 8, type 14 -> base, radius 9 nearest | z-index 50/40 -> --pd-z-* | 10px gap, 3px 4px trigger padding |
| packages/components/src/layouts/app-shell/side-nav-item.module.css | 5 (spacing 2, motion 2, radius 1) | - | motion 250ms/easing -> tokens | 90px capsule radius, 14px gap, 28px indent |
| packages/components/src/components/settings-panel/settings-panel.module.css | 10 (spacing 7, z-index 1, radius 1, shadow 1) | spacing 8/12, radius 9999 -> full | z-index 8 -> --pd-z-sticky; shadow -> --pd-shadow-* | 10/18px bar padding, 2px/7px dot sizes |
| packages/components/src/components/input/input.module.css | 5 (spacing 5) | spacing 8/12 -> space-2/3 | - | 6px helper gap; md 40px vs --pd-control-md 36px |
| packages/components/src/components/select/select.module.css | 3 (spacing 3) | spacing 8/12 | - | 6px helper gap; md 40px vs --pd-control-md 36px |
| packages/components/src/components/dropdown-menu/dropdown-menu.module.css | 7 (spacing 5, radius 2) | spacing 4 -> space-1; radius 8 -> md | - | 7px 8px item padding |
| packages/components/src/components/graph/graph.module.css | 6 (font-size 3, spacing 3) | spacing 4 -> space-1 | - | 11/13px SVG label type, 0 7px toolbar padding |
| packages/components/src/components/checkbox/checkbox.module.css | 5 (font-size 3, spacing 2) | spacing 4 -> space-1 | - | 2px label gap; 18/20/24px icon sizes need --pd-icon-* tokens |
| packages/components/src/components/switch/switch.module.css | 4 (spacing 4) | spacing 4/8 | - | 2px/6px padding |
| packages/components/src/components/avatar/avatar.module.css | 4 (font-size 4) | type 12 -> xs, 14 -> base | - | 10/17px initials, 24/32/40/48px avatar sizes need --pd-avatar-* |
| packages/components/src/components/avatar/avatar.tsx | 1 (font-size 1) | - | - | computed initials fontSize (Math.max(10, size * 0.36)) — tokenize only with avatar sizes |
| packages/components/src/components/badge/badge.module.css | 3 (spacing 2, font-size 1) | - | - | 11px type, 0 7px / 0 9px padding |
| packages/components/src/components/stat-card/stat-card.module.css | 3 (spacing 1, font-size 1, radius 1) | - | - | 28px value type, 6px padding, 10px icon radius |
| packages/components/src/components/tree-view/tree-view.module.css | 3 (radius 1, font-size 1, spacing 1) | radius 8 -> md | - | 18px icon/type, 6px gap |
| packages/components/src/components/button/button.module.css | 2 (motion 2) | - | 700ms/1.4s -> spin/skeleton duration tokens | - |
| packages/components/src/components/icon/icon.module.css | 2 (motion 2) | - | 1s/2s spin -> --pd-duration-spin; reduced-motion should stop | - |
| packages/components/src/components/chart/chart.tsx | 4 (font-size 3, spacing 1) | type 12 -> xs | tooltip padding -> spacing/tooltip token | 8px 10px tooltip box |
| packages/components/src/components/dialog/dialog.module.css | 2 (spacing 2) | spacing 4 -> space-1 | - | -4px close offset |
| packages/components/src/components/alert/alert.module.css | 2 (spacing 2) | - | - | 1px/2px micro spacing |
| packages/components/src/components/otp-input/otp-input.module.css | 1 (spacing 1) | - | - | 6px gap, 40px field |
| packages/components/src/components/pagination/pagination.module.css | 1 (spacing 1) | spacing 4 -> space-1 | - | - |
| packages/components/src/components/highlight-text/highlight-text.module.css | 1 (spacing 1) | - | - | 2px highlight padding |
| packages/components/src/components/tooltip/tooltip.module.css | 1 (spacing 1) | - | - | 6px 9px padding |
| packages/components/src/components/animate-height/animate-height.tsx | 2 (motion 2) | - | duration 220/easing -> motion tokens | - |
| packages/components/src/layouts/sidebar-layout/sidebar-layout.tsx | 1 (motion 1) | - | duration 200 -> motion token | - |

### 3.3 Suggested PR order

1. **P0 auth token bridge** (design review): replace the 25 --login-* definitions with global token references, keep only the video-overlay palette local, point --login-brand at --pd-brand-primary so the accent applies. Removes ~100 findings across 8 auth files and fixes light/dark + accent drift.
2. **P0 library/UC fixes**: tabs.tsx rgba -> channel tokens; UC QR surface #fff -> --pd-bg-surface.
3. **Phase B token generation**: mirror uncommitted tokens.ts additions (zIndex, motionDurations, transitions) into variables.css and add missing steps (section 5); then replace z-index/motion/literal-shadow rows mechanically.
4. **Phase A mechanical sweep**: exact spacing/type/radius/color mappings (~160 findings) with no visual diff.
5. **P1 MUI replacement** in console/uc (section 4), starting with Alert/Button, then TextField/Select/DatePicker that need API adaptation.
6. **Phase C design sign-off** on off-scale type/spacing/display sizes and bespoke auth geometry.


---

## 4. Direct MUI → @pudans/components replacement candidates

Only app code is discussed here. The component library itself must import MUI internally (22 expected imports); the auth app shows the target state — **0 direct @mui imports**, it consumes `Alert`, `Button`, `Input`, `Tabs`, `Tooltip`, `DropdownMenu`, `Icon`, `useToast` from @pudans/components.

Wrappers that already exist: Button (variants primary/secondary/outline/ghost, sizes sm/md/lg), Alert (severity info/success/warning/error, title/action/onClose), TextField = Input wrapper (label/helperText/startIcon/endIcon), Select (options or children), DatePicker (mode date/datetime/time; owns LocalizationProvider + AdapterDayjs), Table, Tabs, Card, Tooltip (delay/arrow), Dialog, Checkbox, Switch, Avatar (name/size/status), Badge, Skeleton, Pagination, TreeView, Toast, SettingsPanel, StatCard, Chart, Icon. There is **no wrapper** for Box, Stack, Typography, Chip, Divider, InputAdornment, MenuItem, @mui/lab Timeline, or MUI Grid — those remain direct MUI imports for now.

| # | App file:line | MUI symbols imported | Wrapper replacement | Call sites (file:line) | Effort |
|---:|---|---|---|---|---|
| 1 | app/console/src/modules/audit/audit-list-view.tsx:14-17 | Alert, Box, Button, InputAdornment, MenuItem, Select, Stack, TextField, Tooltip, Typography + x-date-pickers LocalizationProvider/DatePicker/AdapterDayjs | Alert, Button, TextField (=Input), Select, Tooltip, DatePicker | Tooltip L146; Button L173, L257; TextField L193; Select L209; DatePicker L220, L228; Alert L241 | Medium — remove LocalizationProvider/AdapterDayjs (wrapper owns them), rewire TextField slotProps/InputAdornment to startIcon, keep Box/Stack/Typography/MenuItem/InputAdornment direct |
| 2 | app/console/src/modules/roles/role-list-view.tsx:3 | Alert, Box, InputAdornment, Stack, TextField, Typography | Alert, TextField (=Input) | TextField L122; Alert L144 | Easy — swap TextField label/helper API and use Input startIcon for the search glyph; keep Box/Stack/Typography direct |
| 3 | app/console/src/modules/users/user-list-view.tsx:3 | Alert, Box, Button, InputAdornment, Stack, TextField, Typography | Alert, Button, TextField (=Input) | Button L136; TextField L146; Alert L169 | Easy — contained/small -> primary/sm; TextField startAdornment -> Input startIcon; Table/Pagination already use wrappers |
| 4 | app/uc/src/modules/profile/profile-view.tsx:3 | Alert, Avatar, Box, Button, Chip, Stack, Typography | Alert, Avatar, Button | Avatar L43; Button L50; Alert L91 | Easy — MUI outlined/small -> outline/sm; Avatar sx width/height/fontSize needs a size prop or className; Chip stays MUI |
| 5 | app/uc/src/modules/security/security-view.tsx:3 | Alert, Box, Button, Chip, Divider, Stack, Typography | Alert, Button | Button L89, L92; Alert L107, L118 | Easy — contained/outlined -> primary/outline; wrapper components do not accept sx, so sx props move to className/CSS module |
| 6 | app/uc/src/modules/authorizations/authorizations-view.tsx:3 | Alert, Box, Button, Stack, Typography | Alert, Button | Button L115, L140; Alert L150 | Easy — text/contained -> ghost/primary; sx mb/mt -> className |
| 7 | app/console/src/components/app-frame.tsx:2 | Box | none (no layout primitive yet) | Box L11 | Keep direct; add a Box/Stack primitive to the library later |
| 8 | app/setup/src/components/app-frame.tsx:2 | Box | none | Box L11 | Keep direct |
| 9 | app/console/src/modules/settings/components/appearance-settings.tsx:2 | Box, Chip, Stack | none (Badge is display-only, Chip is not covered) | Box L88, Chip L76-85, Stack L68/L74 | Keep; consider a Chip wrapper for tag-like actions |
| 10 | app/console/src/modules/settings/components/captcha-settings.tsx:2 | Box, Typography | none | Box L74, Typography L93 | Keep direct |
| 11 | app/console/src/modules/overview/overview-view.tsx:26 | @mui/lab Timeline, TimelineItem, TimelineOppositeContent, TimelineSeparator, TimelineDot, TimelineConnector, TimelineContent | none (no Timeline wrapper) | L302-L330 | Keep direct; add a Timeline wrapper if the activity feed is reused |

Summary: **9 import statements in 6 files (23 JSX call sites)** are wrapper-replaceable. Start with Alert and Button (same semantic API, mechanical), then TextField/Select/DatePicker (API adaptation + InputAdornment/startIcon rewiring). Alert/Button migrations also remove 6 of the 14 direct MUI import statements.

---

## 5. Token gaps that block full remediation

**Post-audit update:** these gaps were identified at scan time. While the audit was running the team landed most of them in `variables.css` (see section 8) — use the new CSS variables (--pd-z-*, --pd-duration-spin/skeleton/autofill, --pd-transition-*, --pd-icon-*, --pd-media-*/--pd-scrim-*/--pd-glass-*, --pd-border-width-*, --pd-backdrop-blur) for the Phase B replacements described here.

These are the missing tokens/variables that prevent the remaining literals from being replaced mechanically. `tokens.ts` (uncommitted working-tree changes) already adds several of them as **TS** exports, but `variables.css` has not been updated to expose them as CSS variables — so CSS modules still cannot use them.

| Gap | Evidence | Needed |
|---|---|---|
| z-index scale | 17 literals (auth 12, setup 1, component library 4); no --pd-z-* vars in variables.css | Generate --pd-z-base/sticky/dropdown/overlay/modal/popover/toast/tooltip from the uncommitted zIndex token; map auth layers and app-shell drawer/overlay |
| Motion: long/infinite durations | 220ms (animate-height), 280ms (tabs panel), 700ms (button spinner), 1000ms (icon spin), 1400ms (skeleton + shimmer), 2000ms (reduced-motion spin) | Add --pd-duration-spin, --pd-duration-skeleton and either --pd-duration-disclosure/panel or round to the 150/200/250/300 scale (motionDurations in tokens.ts proposes spin 900/skeleton 1400 — align with actual usage) |
| Type scale: sub-12px and display sizes | Literals with no token: 9px / 9.5px / 10px / 10.5px / 11px / 11.5px / 12.5px / 13.5px / 17px / 18px / 19px / 22px / 26px / 27px / 28px / 32px / 34px | Add --pd-text-2xs (10px?) and/or --pd-text-display-* (28/34px, clamp sizes), or round to nearest existing step (design sign-off) |
| Spacing: off-grid steps | Literals with no token (top): 2px (22), 6px (21), 18px (12), 7px (9), 10px (9), 22px (6), 14px (6), 28px (4), 5px (4), 3px (4), 9px (4), 30px (4), 26px (3), 96px (2), 80px (2), 112px (1), 100px (1), 34px (1), 1px (1) | Decide on 2/6/10/14/18/22/28/30px steps or round to the 4px grid; auth layout 80/96/112px stay documented local constants |
| Radius: off-scale values | Literals with no token or only approximate tokens: 7px / 9px / 10px / 14px / 50% / 90px / 999px (999px is visually the --pd-radius-full equivalent; 50% circles are acceptable) | --pd-radius values only cover 4/6/8/12/16/20/9999px; decide on 10px/14px/90px caps (auth card 20px maps to 2xl) |
| Elevation: floating surfaces | settings-panel sticky bar (0 12px 32px / 0 2px 8px) and auth bespoke shadows | Add --pd-shadow-float (and dark-mode counterpart) or reuse --pd-shadow-lg |
| Blur / gradients | 7 backdrop-filter declarations (blur 2px/12px) + 8 gradients (auth video overlays) | Add --pd-blur-sm/md tokens if glass surfaces are reusable; otherwise document auth video overlay as page-local theme constants |
| Control heights | --pd-control-* = 28/32/36/40/44 but Input/Select md=40px and lg=48px, OTP field 40px, pagination 32/28px | Align wrapper sizes to --pd-control-* (or add --pd-control-xl 48px); currently the library and the token scale disagree |
| Brand source of truth | DEFAULT_ACCENT_COLOR (#1976d2), 6 ACCENT_PRESETS, auth --login-brand | Export brandPalette/tokens from @pudans/components and import them in app-slice/accent UI; derive --login-brand from --pd-brand-primary |
| Layout dimensions (not in scope, but adjacent) | auth card 460x660/800x720, QR 168px, chart heights 270/280, avatar 96px, page max-width 640/920 | Introduce --pd-layout-* tokens only where reuse exists; keep one-off page constants local and documented |

**Coordination note:** the audit observed uncommitted changes in `web/packages/components/src/styles/tokens.ts` (brandSoft, borderWidths, fontWeights, motionDurations, motionEasing, zIndex, opacity, breakpoints, transitions, interaction). Those changes are not mirrored in `variables.css`, and this audit did not modify any source file. The Phase B sweep should land the CSS-variable generation and the literal replacements in the same PR so nothing is replaced with a variable that does not exist yet.

---

## 6. Exclusions, exemptions and third-party/config values

### 6.1 Excluded by task definition (not findings)

- Token definition files: `packages/components/src/styles/tokens.ts`, `variables.css`, `create-theme.ts`, `global.css`.
- `packages/components/src/styles/theme-provider.tsx` (theme wiring; contains the 1 excluded MUI import).
- `dist/`, `node_modules/`, `public/`, generated api-client files (outside the requested scope).
- Third-party/config values are listed separately below and are **not** counted as design-system bypasses.

### 6.2 Exempt literal occurrences (8)

| File:line | Value | Why exempt |
|---|---|---|
| packages/components/src/components/card/card.module.css:10 | box-shadow: none | Explicit reset of MUI elevation, not a value. Keep. |
| packages/components/src/components/toast/toast.module.css:4 | box-shadow: none | Explicit reset. |
| packages/components/src/components/tooltip/tooltip.module.css:7 | box-shadow: none | Explicit reset (tooltip uses neutral surface). |
| packages/components/src/components/graph/graph.module.css:110 | box-shadow: none | Explicit reset on the graph toolbar. |
| app/setup/src/modules/setup/setup-view.module.css:94 | box-shadow: none | Explicit reset on the setup card. |
| packages/components/src/components/table/table.module.css:11 | border-radius: 0 | Intentional reset for the plain table variant. |
| packages/components/src/layouts/app-shell/side-nav-item.module.css:51 | /* <Box sx={{ width: 20, mr: "14px" }}> */ | Comment documenting the legacy implementation, not a declaration. |
| packages/components/src/components/input/input.module.css:100 | transition: background-color 9999s ease-out 0s | Known -webkit-autofill workaround (infinite transition hack). Keep, with a comment if missing. |

### 6.3 Config / data values (documented, not styling bypasses)

- `web/vite.config.ts:39-40` — PWA manifest `theme_color: "#1976d2"` and `background_color: "#fafafa"`. These are third-party PWA manifest values that cannot import CSS variables; they currently equal brand-500/canvas. Keep them synchronized manually (or generate them from the token module in build config) — noted separately as requested.
- `web/app/setup/index.html:6` — `<meta name="theme-color" content="#f9fafb" />`. Static HTML meta before JS loads; it does not equal any canvas token (#fafafa) — align it or accept a documented mismatch.
- `app/console/src/modules/settings/components/appearance-settings.tsx:34` — 6 accent presets. Product data (user-selectable accent colors); the #1976d2 default should come from brandPalette[500]; the other five are curated options, not tokens.
- `src/store/app-slice.ts:8` — DEFAULT_ACCENT_COLOR = "#1976d2" (counted as a P2 duplication finding because it re-declares the brand value; the runtime value itself is legitimate config data).
- `packages/components/src/components/toast/toast.tsx:135` — `duration = 4000` (auto-dismiss UX timing, not a motion token; keep unless a `--pd-toast-duration` preference is introduced).
- `packages/components/src/components/chart/chart.tsx:20-27` — `CHART_COLORS` is already token-based (`rgb(var(--pd-chart-1..6))`); verified compliant.

### 6.4 Other observations

- 0 `hsl()/hsla()` literals and 0 named CSS colors (`white`, `black`, ...) were found — colors are hex/rgba or tokens.
- 0 `@mui/icons-material` imports in app code: icons consistently go through the Iconify-based `Icon` wrapper.
- All app CSS modules **except auth** are largely tokenized for colors (console 57 token refs, setup 14, uc 4); the problem is concentrated in auth's local theme, in spacing/type values, and in the component library's residual literals.

---

## 7. Method, confidence and limitations

- **Scope**: 207 files (`app/**` + `src/**` + `components/src/**`, `.ts/.tsx/.css`), excluding the token definition files and wiring noted above.
- **Classification**: every match was reviewed; matches using `var(--pd-*)` (including channel form `rgb(var(--pd-*) / a)`) were counted as compliant evidence rather than findings (489 references). `var() fallback literals` are counted as findings because they bypass the token if the variable is missing.
- **Line-based caveat**: the scanner is line-oriented, so 7 duration/shadow values inside multi-line declarations were added manually; multiline declarations that were already token-based were verified and excluded. CSS shorthand spacing was flattened into individual values for the mechanical/judgment split (e.g. `padding: 2px 8px` = 2 values).
- **Not audited (out of requested categories, visible in passing)**: letter-spacing (26 declarations, e.g. 0.18em tracking in auth), backdrop-filter blur (7), text-shadow (3), gradients (8), width/height/min-height/max-width layout literals (auth card 460/660/800/720, QR 168, avatar 96, chart 270/280, sidebar widths already tokens), border widths (1px/2px), opacity values (0.45/0.62/0.82...), icon `size={15/17/22}` props. These are recommended as a follow-up audit; several are captured in sections 3/5 as design-judgment items.
- **Verification of the numeric claims**: totals derive from the scan dataset (957 matches -> 489 token refs -> 468 raw -> 8 exempt -> 4 collapsed -> 37 import statements -> 419 non-import hits -> minus 1 autofill workaround -> minus 2 shadow channels merged -> plus 7 manual multi-line entries = 423 hardcoded-value findings; plus 14 app MUI imports = 437 flagged items). MUI sx numeric borderRadius values were verified against @mui/system borders transform (theme.shape.borderRadius = 12px multiplies numeric sx values: 2 -> 24px, 3 -> 36px).
- **Read-only guarantee**: this audit performed no writes outside `.dsh/migration/` and ran no mutating git commands.

---

## 8. Post-audit working-tree delta (read before applying any row)

This audit scanned the tree at **2026-11-16 ~10:44–10:46 +08:00**. While it was running, the working tree changed substantially (concurrent migration and token work by the team). A re-scan at **~10:58** shows:

| Metric | Audit snapshot | Current tree (re-scan) |
|---|---:|---:|
| In-scope files | 207 | 292 |
| Candidate design-value / import lines | 957 | 1,212 |
| Lines already using tokens | 489 | 828 |
| Raw (non-token) hits | 468 | 384 |
| App direct MUI import statements | 14 | 14 (unchanged) |
| New component directories | - | 28 added |

**What changed** (git status at re-scan): 19 app files modified (all auth CSS modules, console audit/roles/users/overview/settings, uc profile/authorizations/layouts, setup CSS), 30 component-library files modified, 28 new component directories added (accordion, breadcrumbs, captcha, code, confirm-dialog, description-list, empty-state, error-detail-dialog, error-state, file-drop-zone, file-type-icon, form-field, language-switcher, logo, motion, oauth-app-card, powered-by, progress, progress-steps, relative-time, responsive-tabs, search-input, select-dialog, size-input, split-pane, theme-switch, user, plus hooks), and `styles/variables.css` / `tokens.ts` / `create-theme.ts` / `global.css` were extended.

**Token gaps from section 5 are now largely closed in the working tree.** `variables.css` now defines `--pd-z-base/sticky/dropdown/overlay/modal/popover/toast/tooltip/raised/shell/shell-raised`, `--pd-duration-instant/skeleton (1400ms)/spin (900ms)/autofill (9999s)`, `--pd-transition-*`, `--pd-focus-ring-*`, `--pd-opacity-*`, `--pd-icon-xs..xl`, `--pd-border-width-*`, `--pd-backdrop-blur`, `--pd-media-*` / `--pd-scrim-*` / `--pd-glass-*`, `--pd-bg-backdrop`, `--pd-hover-lift`, `--pd-press-scale`. Use these for the Phase B replacements (z-index 17 literals, motion 220/280/700/1000/1400/2000ms, icon 18/20/24px, glass/blur values).

**Implications for this report:**

- Specific line numbers in sections 2–4 refer to the snapshot. Several audited files were rewritten during the audit — e.g. `app/auth/src/layouts/auth-layout.module.css` (raw findings dropped 74 -> 19) and `app/console/src/modules/audit/audit-list-view.tsx` — so re-verify those rows before applying them.
- The 28 new components were outside the audit snapshot and should get a follow-up pass. The re-scan shows the component package now carries most raw hits (426 color-fn, 142 font-size, 90 radius and 86 spacing candidate lines, including token-aware ones) — new components include quite a few literals of their own.
- Recommendation: once the current migration settles, re-run the scan (the method in section 7 is reproducible) and diff against this report. Sections 1–7 remain the baseline/priority map; this section is the delta map.
