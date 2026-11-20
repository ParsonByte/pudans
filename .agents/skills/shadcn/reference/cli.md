# shadcn CLI reference

All commands run from `web/`. Use `npx shadcn@latest` so the CLI is current.

## init

Initialize configuration and dependencies for an existing project, or create a new project with `--name`. The `init` command installs dependencies, adds the `cn` util and configures CSS variables. `create` is an alias.

```bash
npx shadcn@latest init
```

| Option | Meaning |
| --- | --- |
| `-t, --template <template>` | Template to use: `next`, `vite`, `start`, `react-router`, `laravel`, `astro` |
| `-b, --base <base>` | Component library to use: `base`, `radix`, `aria` |
| `-p, --preset [name]` | Use a preset configuration |
| `-y, --yes` | Skip the confirmation prompt (default: true) |
| `-d, --defaults` | Use defaults: `--template=next --preset=nova` |
| `-f, --force` | Force overwrite of an existing configuration |
| `-c, --cwd <cwd>` | Working directory |
| `-n, --name <name>` | Name for the new project |
| `-s, --silent` | Mute output |
| `--css-variables` / `--no-css-variables` | Toggle CSS-variable theming (default: true) |
| `--monorepo` / `--no-monorepo` | Toggle monorepo scaffolding |
| `--rtl` / `--no-rtl` | Toggle RTL support |
| `--pointer` / `--no-pointer` | Toggle pointer cursor for buttons |
| `--reinstall` / `--no-reinstall` | Toggle re-install of existing UI components |

## add

Add components and dependencies to the project.

```bash
npx shadcn@latest add [component]
```

| Option | Meaning |
| --- | --- |
| `-y, --yes` | Skip the confirmation prompt (default: false) |
| `-o, --overwrite` | Overwrite existing files (default: false) |
| `-c, --cwd <cwd>` | Working directory |
| `-a, --all` | Add all available components |
| `-p, --path <path>` | Path to add the component to |
| `-s, --silent` | Mute output |
| `--dry-run` | Preview changes without writing files |
| `--diff [path]` | Show the diff for a file |
| `--view [path]` | Show file contents |

## apply

Apply a preset to an existing project. `--only theme` or `--only font` applies one part only.

```bash
npx shadcn@latest apply a2r6bw
npx shadcn@latest apply a2r6bw --only theme
```

| Option | Meaning |
| --- | --- |
| `--preset <preset>` | Preset configuration to apply |
| `--only [parts]` | Apply only parts of a preset: `theme`, `font` |
| `-y, --yes` | Skip the confirmation prompt |
| `-c, --cwd <cwd>` | Working directory |
| `-s, --silent` | Mute output |

## preset

Inspect preset codes and resolve the preset of the current project.

```bash
npx shadcn@latest preset decode a2r6bw
npx shadcn@latest preset resolve
npx shadcn@latest preset url a2r6bw
npx shadcn@latest preset open a2r6bw
```

- `decode <code> [--json]` decodes a preset code.
- `resolve` (alias `info`) resolves the preset from the current project; `--json` for machine output; `-c, --cwd <cwd>`.
- `url <code>` prints the create URL for a preset code.
- `open <code>` opens a preset code in the browser.

## view

View items from the registry before installing them. Multiple items and namespaced registries are accepted.

```bash
npx shadcn@latest view button card dialog
npx shadcn@latest view @acme/auth @v0/dashboard
```

Options: `-c, --cwd <cwd>`.

## search

Search for items in registries. `list` is an alias.

```bash
npx shadcn@latest search @shadcn -q "button"
npx shadcn@latest search @shadcn @v0 @acme
```

| Option | Meaning |
| --- | --- |
| `-c, --cwd <cwd>` | Working directory |
| `-q, --query <query>` | Query string |
| `-l, --limit <number>` | Maximum items per registry (default: 100) |
| `-o, --offset <number>` | Items to skip (default: 0) |

## build

Read `registry.json` and generate registry JSON files, by default in `public/r`.

```bash
npx shadcn@latest build
npx shadcn@latest build --output ./public/registry
```

Options: `[registry]` path (default: `./registry.json`), `-o, --output <path>`, `-c, --cwd <cwd>`.

## docs

Fetch documentation and API references for components.

```bash
npx shadcn@latest docs [component]
```

Options: `-c, --cwd <cwd>`, `-b, --base <base>` (`base`, `radix`, `aria`; defaults to the project base), `--json`.

## info

Show information about the current project.

```bash
npx shadcn@latest info
```

Options: `-c, --cwd <cwd>`, `--json`.

## migrate

Run migrations on the project. `-l, --list` lists all migrations; `-y` skips confirmation; `-f, --from` and `-t, --to` name the source and target for base-color or icon migrations; an optional path or glob scopes the migration.

| Migration | Description |
| --- | --- |
| `cn` | Migrate `clsx` and `tailwind-merge` to `cn` |
| `icons` | Migrate UI components to a different icon library |
| `base-color` | Migrate the theme to a different base color |
| `radix` | Migrate `@radix-ui/react-*` imports to `radix-ui` |
| `rtl` | Transform components for right-to-left languages |

```bash
npx shadcn@latest migrate cn
npx shadcn@latest migrate icons --from lucide --to phosphor --yes
npx shadcn@latest migrate base-color --to zinc --yes
npx shadcn@latest migrate radix
npx shadcn@latest migrate rtl "src/components/ui/**"
```

`migrate cn` works without `components.json` in any JavaScript or TypeScript project on Tailwind CSS v4. Scoped migrations install `cn` and keep the old packages because files outside the scope may still reference them; unsupported imports are left unchanged and reported.

## eject

Inline `shadcn/tailwind.css` into the global CSS file and remove the `shadcn` dependency. This is irreversible: future CLI updates to `shadcn/tailwind.css` stop applying automatically.

```bash
npx shadcn@latest eject
```

Options: `-c, --cwd <cwd>`, `-y, --yes`, `-s, --silent`.