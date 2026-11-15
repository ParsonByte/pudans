---
name: skill-authoring
description: Create, edit or validate a skill under .agents/skills. Use when asked to add a skill, restructure a skill directory or check that a SKILL.md satisfies the skill authoring rule.
---

# skill-authoring

## Purpose

Add or maintain skills that follow `.agents/rules/skills.md`: fixed layout, kebab-case naming, a complete `SKILL.md` and no unrelated files.

## Triggers

- The user asks to create, rename, split or delete a skill.
- An existing skill is missing sections, has stray files or a non-kebab-case name.
- Another task needs a reusable procedure packaged for later sessions.

## Steps

1. Choose a kebab-case name that names the action or object, for example `openapi-sync` or `web-release-check`. Check for collisions:
   ```bash
   ls .agents/skills
   ```
2. Create the directory, adding `reference/` and `templates/` only when the skill needs them:
   ```bash
   mkdir -p .agents/skills/<name>
   ```
3. Write `SKILL.md` from `templates/SKILL.md`, keeping every section: frontmatter, Purpose, Triggers, Steps, Inputs and outputs, Constraints.
4. Make the steps executable: exact commands, paths and expected results instead of vague advice. Put long reference material in `reference/` and copyable files in `templates/`, then link them from the steps.
5. Point to the authoritative rule instead of copying whole rule files, for example "follows `.agents/rules/commit.md`".
6. Validate the result:
   ```bash
   find .agents/skills/<name> -type f | sort
   grep -n '^## ' .agents/skills/<name>/SKILL.md
   echo <name> | grep -Eq '^[a-z0-9]+(-[a-z0-9]+)*$' && echo "name ok"
   ```
7. Add or update the skill row in the skills table of `AGENTS.md`.

## Inputs and outputs

- Input: the procedure to package, its trigger situations and any reference or template files.
- Output: `.agents/skills/<name>/SKILL.md` (plus optional `reference/` and `templates/`) and an updated `AGENTS.md`.

## Constraints

- `SKILL.md` is mandatory and must start with YAML frontmatter holding `name` and `description`; quote the description when it contains `: ` so the YAML stays valid.
- The directory name must stay kebab-case and unique.
- No unrelated files: no sample output, temporary files, caches or personal notes.
- No nested skills and no skills outside `.agents/skills/`.
- No secrets, tokens or environment-specific values inside a skill.
- Do not duplicate an entire rule file; link to it and add only the procedure.
