# Extension resource rule

## Scope

Applies to any change to roles, permissions, workspaces, menus, button/table permissions or extension resources. Any new feature that adds a menu, button, table or permission must create or modify the matching file under `appkernel/src/main/resources/extensions/`.

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
  - `workspace-<workspace>-menu-template-<menu>.yaml` - menu access templates and the menu's `ui-component` identifier.

## Naming

Every template file follows `<directory>-<scope>-<type>-template-<name>.yaml`:

- workspace: `workspace-<workspace>-<workspace|menu>-template-<name>.yaml`
- role: `role-<api|button|table>-template-<name>.yaml`
- builtin: `builtin-role-template-<name>.yaml` and `builtin-usergroup-template-<name>.yaml`
- config: `config-<type>-template-<name>.yaml`

## Required

- A navigable menu is defined only in `extensions/workspace/<workspace>/` with `ui-type: "menu"`, `ui-menu`, `ui-workspace`, `ui-path`, `ui-label`, `ui-component` and `ui-permissions`.
- A second-level menu adds `ui-parent` (the parent menu label) and optionally `ui-parent-icon` to its template. The parent is a navigation container only; access is still controlled by the child menus and their permissions.
- Button and table permissions are role permission templates under `extensions/role/button/` and `extensions/role/table/`, carrying `ui-type`, `ui-menu`, `ui-operation`, `ui-permissions` and `rules`/`dependencies`. Do not put button or table permission templates in `workspace/`.
- API permission templates live under `extensions/role/api/`.
- Built-in roles and user groups live only under `extensions/builtin/`. A built-in role must reference role templates through `dependencies`; do not repeat its full permission list in Java or in a long `ui-permissions` annotation.
- Every console menu that grants access and every console button/table permission template declares its permissions in `ui-permissions`, together with `rules` or `dependencies`. The `ui-*` annotations are backend navigation metadata exposed through `/navigation`; the backend does not render them. Omit `ui-permissions` entirely when it would be empty; never write `ui-permissions: "[]"`.
- The backend loads every YAML under `extensions/**/*.yaml`; no Java file may hardcode a menu, permission key, permission rule or workspace list.
- `metadata.name` of existing templates is a contract. Rename a template name only together with every dependency, annotation and test that references it.

## Adding a feature

1. Add the menu file under `extensions/workspace/<workspace>/`.
2. Add its button/table permission files under `extensions/role/button/` and `extensions/role/table/`.
3. Add a `ui-component` identifier for the menu metadata.
4. If the feature introduces a new workspace or config resource, add it under the matching directory.
5. If the feature changes a built-in role, express the change as a dependency on role templates in `extensions/builtin/`.
6. Run `./gradlew :appkernel:test`.

## Verification

```bash
./gradlew :appkernel:test
rg -n 'extensions/(roles|role-template|user-template|config-template)' appkernel/src
```

The last search must return no result.
