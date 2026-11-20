---
name: shadcn
description: "Find, add and animate shadcn/ui components with the shadcn CLI under the project preset b1oVxsw5. Use before building, changing or animating any UI component."
---

# shadcn

## Purpose

Keep every UI component sourced from the shadcn registry, installed through the CLI, aligned with the project preset and animated with Motion and GSAP. The output is a CLI-added component plus an animated composition that passes the frontend checks.

## Triggers

- A page or feature needs a control, primitive or block that shadcn/ui may already provide.
- An existing shadcn/ui component needs different behaviour, styling or animation.
- The project preset, theme tokens, fonts or icon library need alignment.

## Steps

1. Confirm the project state from `web/`:
   ```bash
   npx shadcn@latest info
   npx shadcn@latest preset resolve
   ```
2. Search the registry before writing markup:
   ```bash
   npx shadcn@latest search @shadcn -q "<keyword>"
   ```
3. Inspect the candidate:
   ```bash
   npx shadcn@latest view <item>
   ```
4. Add it with the CLI, previewing first when the change is not trivial:
   ```bash
   npx shadcn@latest add <item> --dry-run
   npx shadcn@latest add <item>
   ```
5. Align theme and fonts with the preset instead of editing tokens by hand:
   ```bash
   npx shadcn@latest apply b1oVxsw5 --only theme
   npx shadcn@latest apply b1oVxsw5 --only font
   ```
6. Animate the component per `.agents/rules/shadcn.md`: Motion for enter and exit, hover and tap, and layout; GSAP for timelines and scroll effects; honour `prefers-reduced-motion`.
7. Run the frontend checks:
   ```bash
   npm run lint
   npm run format:check
   npm run typecheck
   npm run build
   ```

## Inputs and outputs

- Input: the UI need, its interaction states and the registry item that satisfies it.
- Output: a CLI-generated component under `web/src/components/ui`, its animated composition, and passing lint, format, typecheck and build.

## Constraints

- Follow `.agents/rules/shadcn.md` for the preset and motion policy.
- Never hand-write or edit files under `web/src/components/ui`; the CLI is the only source and the only writer. Re-import with `add` instead of patching.
- Do not add a second component library or import Radix primitives outside `web/src/components/ui`.
- Do not add a third animation library; Motion and GSAP are the only ones.
- The style guard `node scripts/check-styles.mjs`, run by `npm run lint`, rejects radius utilities and palette classes outside `web/src/components/ui`.
- The motion guard `node scripts/check-motion.mjs`, run by `npm run lint`, rejects per-usage animation utilities, Motion/GSAP imports outside the centralized layer and custom keyframes.
- Use `--overwrite` only when overwriting is intended, and review the diff first.
- The full command reference is in `reference/cli.md`.