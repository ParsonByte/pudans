# Console UI rule

## Scope

Applies to the web console under `web/src/modules/_workspace/_console`, its shared components and any global UI used by console pages.

## Mandatory reading

Read this rule before changing a console page, table, form, modal, detail view, toolbar or shared console component.

## Component stack and placement

- Use Ant Design (`antd`) and `@ant-design/pro-components`. Do not build parallel primitives such as native selects, hand-written form validation or custom tables.
- App-wide reusable UI belongs in `web/src/components/ui`.
- Console-wide reusable UI belongs in `web/src/modules/_workspace/_console/components`.
- Feature-only shared UI belongs in the feature's `components` directory, never inline in a page file when it has more than one consumer.
- Reuse existing `BentoGrid`, `SectionCard`, `StatTile`, `MetricCard`, `ResultTag`, `EmptyValue`, `CopyableIdentifier` and `DescriptionGrid` before creating another wrapper.
- Export reusable constants and patterns from a shared module instead of duplicating regexes or validation messages.
- Mount `NavigationProgress` exactly once in the root `web/src/app/layout.tsx`, never in a page or a workspace-only shell, so every route transition gets feedback.

## Layout and responsiveness

- Pages and toolbars must fill the available width without trailing blank space and must not create horizontal page overflow.
- Filters and toolbars must stay on one row on desktop, share widths evenly and wrap without clipping on smaller screens.
- Verify every changed surface on desktop, tablet and mobile widths.
- Expanded rows use an even responsive grid with consistent label and value spacing. Do not mix uneven description columns.
- Actions in a panel or expanded row stay compact and aligned. Avoid oversized stacked buttons, full-width secondary actions and orphaned destructive actions.
- Use a primary action plus a compact overflow menu when a panel would otherwise grow tall.
- Empty user-facing account values display `未填写`, not `-` or `—`.

## Lists, selection and bulk actions

- Bulk actions belong in a dropdown in the table toolbar immediately after the export action.
- Do not render a selected-count banner or repeated `已选择 N 项` text. The table selection state is the source of truth.
- Keep row actions concise; the full user detail is an independent route.
- Long identifiers such as UID and provider user IDs stay on one line with ellipsis, a tooltip and a copy action.
- User-facing enums and provider identifiers display Chinese labels. Raw values are fallback text or tooltip content only.

## User provisioning

- Feishu owns `displayName`, `email`, `phone` and `avatar`. These fields are read-only and must be resolved from Feishu on the server; never ask an operator to type them in a create, batch-create or import flow.
- Single create, batch add and import use the Feishu-backed user endpoints. Do not use local username/password creation endpoints for Feishu provisioning.
- Batch add accepts one `union_id` or `open_id` per line plus an optional user group. It does not collect passwords, display names or email addresses.
- Import offers two explicit sources: the Feishu contact directory and a CSV file. The Feishu directory lists contacts with avatar, name, email, provider ID and an optional local login name; blank login names auto-generate. Contacts missing a name or provider ID stay visible, are marked and cannot be selected. Email is optional when Feishu does not return it.
- Feishu contacts already bound to a local account display `已导入` with the local login name, cannot be selected and do not count as importable. Refresh the directory after a partial batch so successful imports immediately become unavailable.
- CSV import accepts Feishu user IDs, optional usernames and optional user groups. The downloadable template must match those columns, and the modal previews the rows before submission.
- Batch and import flows report per-row failures and retain failed rows for retry.

## User detail

- The user detail is an independent page, not a drawer or an expanded table row.
- User detail uses the shared `BentoGrid` with a left profile column and a wider right information column.
- The left column stacks profile, roles and identity connections in that order. Account information and activity stay in the right column.
- Account information belongs in the wide right column. The left profile card stays compact and contains only avatar, name, login name, status, group and account actions.
- The profile card keeps the avatar and identity details on one horizontal line. The primary edit action is a separate full-width button below a divider, and secondary account actions stay in the card's top-right overflow menu.
- Roles and identity connections use compact vertical card lists suited to the narrow left column. Do not embed a wide ProTable in either card.

## Modals

- Build forms with `ModalForm` and `ProForm*` fields. Keep the title, one concise description and a clear footer hierarchy.
- The user editor starts with a compact Feishu identity summary, then separates local editable settings with a divider. Do not wrap the identity summary in a large filled card.
- The user editor exposes an optional local login name; a blank value restores the immutable resource name. Display `loginName` when present while routes and mutations continue to use `metadata.name`.
- Do not render one card per form row. Use a textarea, a compact table or a single form grid for batch input.
- Disable closing while a mutation is pending and preserve failed entries after a partial failure.
- Download-template actions in import dialogs are secondary links; cancel and submit are the only primary footer buttons.

## Verification

```bash
cd web
npx prettier --write <changed files>
npm run check
npm run build
```

Also run `git diff --check` before reporting the change as complete.
