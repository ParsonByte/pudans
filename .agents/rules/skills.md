# Skill authoring rule

## Layout

The directory is fixed at `.agents/skills/<name>/`, where `<name>` is kebab-case (for example `openapi-sync`, `web-release-check`):

```
skills/
└── <name>/
    ├── SKILL.md
    ├── reference/
    └── templates/
```

- `SKILL.md` is mandatory.
- `reference/` holds reference material, `templates/` holds template files; create them only when needed.
- No unrelated files: no sample output, temporary files, caches or personal notes inside a skill.

## SKILL.md content

`SKILL.md` must state:

1. Purpose: what problem the skill solves and what it produces.
2. Trigger conditions: which requests or situations use this skill.
3. Steps: ordered execution steps, pointing to reference material and templates where relevant.
4. Inputs and outputs: required inputs, conventions and the final artefacts.
5. Constraints: boundaries, prohibitions and how to handle failure.

```markdown
# <skill-name>

## Purpose

## Triggers

## Steps

## Inputs and outputs

## Constraints
```
