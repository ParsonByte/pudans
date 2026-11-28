---
name: skill-authoring
description: Create, edit or validate a skill under .agents/skills in pudans, including its frontmatter, sections, directory layout and AGENTS.md index entry. Use when adding or restructuring a skill.
---

# skill-authoring

## Purpose

Keep every skill under `.agents/skills/<name>/SKILL.md` discoverable, complete and English, with
executable steps and no unrelated files.

## Triggers

- Adding, renaming, splitting or deleting a skill.
- A `SKILL.md` is missing frontmatter or a required section.
- A procedure should be reusable in later sessions.

## Steps

1. Choose a kebab-case name that names the action or object. Check for collisions:
   ```bash
   find .agents/skills -maxdepth 1 -mindepth 1 -type d | sort
   ```
2. Create only the directory the skill needs:
   ```bash
   mkdir -p .agents/skills/<name>
   ```
3. Write `SKILL.md` from `.agents/skills/skill-authoring/templates/SKILL.md`. Keep YAML
   frontmatter with `name` and `description`, then Purpose, Triggers, Steps, Inputs and outputs, and
   Constraints.
4. Make each step executable: exact paths, commands and expected results. Link to the authoritative
   rule instead of copying it.
5. Add the skill and its trigger to the skills table in `AGENTS.md`.
6. Validate:
   ```bash
   find .agents/skills/<name> -type f | sort
   grep -n '^## ' .agents/skills/<name>/SKILL.md
   echo '<name>' | grep -Eq '^[a-z0-9]+(-[a-z0-9]+)*$'
   ```
7. Report the new skill, its triggers and its AGENTS.md entry.

## Inputs and outputs

- Input: a reusable procedure and its trigger situations.
- Output: `.agents/skills/<name>/SKILL.md`, optional templates and an AGENTS.md index update.

## Constraints

- Directory names are unique kebab-case.
- Frontmatter is mandatory; quote a description containing `: `.
- Keep the content English.
- No nested skills, no skills outside `.agents/skills`, no secrets and no unrelated files.
- Do not duplicate a rule; link to it and add only the procedure.