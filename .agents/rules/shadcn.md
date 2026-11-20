# shadcn/ui rule

## Mandatory reading

Read this rule and `.agents/skills/shadcn/SKILL.md` before adding, changing or animating any frontend component. The CLI command reference lives in `.agents/skills/shadcn/reference/cli.md`.

## Registry first (hard)

- Before writing any UI markup, search the registry for an existing item:
  ```bash
  cd web && npx shadcn@latest search @shadcn -q "<keyword>"
  ```
- Inspect a candidate before installing it:
  ```bash
  cd web && npx shadcn@latest view <item>
  ```
- When an item exists, add it with the CLI and let the CLI install its dependencies:
  ```bash
  cd web && npx shadcn@latest add <item>
  ```
- Never create, copy or hand-write a file under `web/src/components/ui`. Pages, layouts and feature components compose the CLI-generated primitives.
- `web/src/components/ui` is immutable: the CLI is the only writer. Adding or re-importing through the CLI is allowed; editing, patching, reformatting or manually restoring a generated file is prohibited. Differences in behaviour or appearance are composed in feature components or expressed with theme tokens in `web/src/styles/_global.css`. The directory is excluded in `web/.prettierignore`; never run the formatter over it.
- Do not import Radix primitives outside `web/src/components/ui`.
- shadcn/ui stays the only component library; do not add a second one.

## Preset (hard)

The project preset is `b1oVxsw5`:

- style `sera`, base color `neutral`, theme `neutral`, chart color `neutral`
- font `noto-sans`; heading font inherits the body font
- radius `default`, icon library `lucide`, menu color `inverted`, menu accent `subtle`

- New frontend projects are created with `npx shadcn@latest init --preset b1oVxsw5 --template next`.
- An existing project is aligned with `npx shadcn@latest apply b1oVxsw5`; theme-only or font-only updates use `--only theme` or `--only font`.
- `web/components.json` and `web/src/styles/_global.css` are the single source of design tokens and must match the preset. Do not override tokens inline, in feature components or with another theme.
- Audit the active preset with `npx shadcn@latest preset resolve`; decode a code with `npx shadcn@latest preset decode <code>`.

## Motion (hard)

- Every shadcn/ui component the application renders has motion. A component that appears without a transition is a defect.
- Motion is owned by the centralized layer: `MotionBridge` gives every `[data-slot]` the shared entrance animation and every interactive slot hover/press motion, while overlays, collapsible content, skeletons and charts keep the motion their primitives already define. Feature code must never import Motion or GSAP, add entrance/exit utilities (`animate-in`, `animate-out`, `fade-in-*`, `zoom-in-*`, `slide-in-*`) or define keyframes; a non-slot component that needs the shared entrance declares `data-motion="enter"`.
- The motion layer is enforced by `scripts/check-motion.mjs` through `npm run lint`; adding a ui component through the CLI is covered automatically, and any per-usage animation fails the gate.
- Motion (`motion/react`) is the default layer: enter and exit with `AnimatePresence`, hover and tap micro-interactions, layout transitions with `layout` and `layoutId`, list stagger.
- GSAP handles timeline choreography, scroll-driven sequences and multi-element effects that Motion cannot express cleanly.
- Motion and GSAP are the only animation libraries. Do not add a third.
- Shared durations, easings, variants and timelines live in one motion module under `web/src/lib`; do not duplicate transitions inline across components.
- Respect `prefers-reduced-motion`: use Motion's `useReducedMotion` and GSAP `matchMedia`, and fall back to the static state.
- Animate a CLI-generated component by composing it in feature code; never edit the generated file and never fork a second component with the same responsibility.

## Styling (hard)

- Every component styles itself with the shadcn theme tokens declared in `web/src/styles/_global.css` (`background`, `foreground`, `card`, `popover`, `primary`, `secondary`, `muted`, `accent`, `destructive`, `border`, `input`, `ring`, `sidebar-*`, `chart-*`).
- Hard-coded palette classes (`bg-blue-500`, `text-white`, `border-zinc-200`), raw colors (`#fff`, `rgb()`, `oklch()` in component files) and per-component theme overrides are prohibited.
- The project shape is square: `--radius` is `0rem` and non-ui components never add `rounded-*` utilities. Circular rendering is allowed only where a shadcn primitive itself defines it (avatar, radio, switch thumb). No pill shapes, no rounded cards, buttons, inputs, dialogs or progress bars.
- A design need the core tokens do not cover is expressed as a named token in `web/src/styles/_global.css` and consumed through the matching Tailwind utility; the token file is the only place raw colors may appear.

## Client boundaries

- shadcn/ui components, Motion and GSAP run on the client. In the Next.js App Router, a file that uses hooks, Motion, GSAP or browser APIs starts with `"use client"`.