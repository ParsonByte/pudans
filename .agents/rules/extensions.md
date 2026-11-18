# Extension resource rule

## Scope

Applies to any change to roles, permissions, workspaces, menus, button/table permissions or extension resources. Any new feature that adds a frontend menu, button, table, permission or page must create or modify the matching file under `appkernel/src/main/resources/extensions/`.

## Layout

- `appkernel/src/main/resources/extensions/config/` - configuration resources such as `AuthProvider` and `ConfigMap`.
- `appkernel/src/main/resources/extensions/role/` - role permission templates, split by kind:
  - `api/role-api-template-<name>.yaml` - API rules.
  - `button/role-button-template-<menu>.yaml` - button permissions for a menu.
  - `table/role-table-template-<menu>.yaml` - table permissions for a menu.
- `appkernel/src/main/resources/extensions/builtin/` - built-in roles and user groups:
  - `builtin-role-template-roles.yaml` - built-in roles. Their permissions are expressed as dependencies on role templates, not as a duplicated UI permission list.
  - `builtin-usergroup-template-usergroups.yaml` - built-in user groups.
- `appkernel/src/main/resources/extensions/workspace/<workspace>/` - one directory per workspace:
  - `workspace-<workspace>-workspace-template-<workspace>.yaml` - workspace metadata.
  - `workspace-<workspace>-menu-template-<menu>.yaml` - menu access templates and the menu's `ui-component`.

## Naming

Every template file follows `<directory>-<scope>-<type>-template-<name>.yaml`:

- workspace: `workspace-<workspace>-<workspace|menu>-template-<name>.yaml`
- role: `role-<api|button|table>-template-<name>.yaml`
- builtin: `builtin-role-template-<name>.yaml` and `builtin-usergroup-template-<name>.yaml`
- config: `config-<type>-template-<name>.yaml`

## Required

- A navigable menu is defined only in `extensions/workspace/<workspace>/` with `ui-type: "menu"`, `ui-menu`, `ui-workspace`, `ui-path`, `ui-label`, `ui-component` and `ui-permissions`.
- Button and table permissions are role permission templates under `extensions/role/button/` and `extensions/role/table/`, carrying `ui-type`, `ui-menu`, `ui-operation`, `ui-permissions` and `rules`/`dependencies`. Do not put button or table permission templates in `workspace/`.
- API permission templates live under `extensions/role/api/`.
- Built-in roles and user groups live only under `extensions/builtin/`. A built-in role must reference role templates through `dependencies`; do not repeat its full permission list in Java or in a long `ui-permissions` annotation.
- Every console menu that grants access and every console button/table permission template declares its permissions in `ui-permissions`, together with `rules` or `dependencies`. Omit `ui-permissions` entirely when it would be empty; never write `ui-permissions: "[]"`.
- The backend loads every YAML under `extensions/**/*.yaml`; no Java or TypeScript file may hardcode a menu, permission key, permission rule or workspace list.
- `metadata.name` of existing templates is a contract. Rename a template name only together with every dependency, annotation and test that references it.

## Frontend

- The web application reads `/navigation` and `/permission-catalog` JSON. Navigation menus carry `ui-component`; the single dynamic route `_authenticated/_workspace/$` selects the registered page component.
- A new page component must be registered in `web/src/modules/workspace/menu-view.tsx`. Do not add a static route file for a menu.
- Button/table gating uses `ui-operation` ids through `useAllowedOperations()`; do not hardcode permission keys in a page.
- A new menu must not require changes to the frontend navigation or route table beyond registering a new page component.

## Adding a feature

1. Add the menu file under `extensions/workspace/<workspace>/`.
2. Add its button/table permission files under `extensions/role/button/` and `extensions/role/table/`.
3. Add a `ui-component` value and register the page component in `web/src/modules/workspace/menu-view.tsx`.
4. If the feature introduces a new workspace or config resource, add it under the matching directory.
5. If the feature changes a built-in role, express the change as a dependency on role templates in `extensions/builtin/`.
6. Run `./gradlew :appkernel:test`, `npm run typecheck`, `npm run lint` and `npm run format:check`.

## Verification

```bash
./gradlew :appkernel:test
cd web && npm run typecheck && npm run lint && npm run format:check
rg -n 'extensions/(roles|role-template|user-template|config-template)' appkernel/src web/src
```

The last search must return no result.
