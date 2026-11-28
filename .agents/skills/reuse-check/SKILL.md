---
name: reuse-check
description: Search pudans contracts, shared libraries, kernels and applications for an existing implementation before adding a class, endpoint, DTO, provider, configuration key or data-access method. Use before any new implementation.
---

# reuse-check

## Purpose

Execute `.agents/rules/development.md` and `.agents/rules/directory-structure.md` by searching for
an existing capability before adding another one. The output is a reuse, extension, extraction or
documented creation decision.

## Triggers

- Before adding a class, endpoint, DTO, Feign client, provider, configuration key or repository.
- Before adding a second implementation of a capability that may already exist.
- When a review asks whether an existing implementation could have been reused.

## Steps

1. State the responsibility in two or three keywords, not the proposed class name.
2. Search by responsibility:
   ```bash
   rg -n -i '<keyword>' pudans-contract pudans-communal pudans-kernel-modules pudans-*
   ```
3. Search by naming and package:
   ```bash
   rg -n 'class .*<Keyword>|interface .*<Keyword>|record .*<Keyword>|enum .*<Keyword>' \
     --glob '*.java' --glob '!build/**'
   find . -path '*<area>*' -not -path '*/build/*'
   ```
4. Search by callers and configuration:
   ```bash
   rg -n '<existing symbol>|<config prefix>|<provider name>' \
     --glob '*.java' --glob '*.yml' --glob '*.yaml' --glob '!build/**'
   ```
5. Check the contract and provider boundaries:
   - Is there an existing `pudans-contract-*` client or DTO that fits?
   - Is there a shared implementation in `pudans-communal`?
   - Is the capability already owned by a kernel or service?
   - Would extending an existing interface or configuration property be smaller than a new type?
6. Decide:
   - Same responsibility and compatible contract: reuse.
   - Same responsibility, contract too narrow: extend with an overload, parameter object, strategy or
     default method.
   - Shared part only: extract the shared part and use it from both call sites.
   - No candidate after all searches: create the smallest implementation and record why.
7. Record the commands, candidate files, decision and reason in the task summary or pull request copy.

## Inputs and outputs

- Input: the responsibility, the affected module and the current repository.
- Output: a reuse decision with evidence and, when needed, a minimal new implementation.

## Constraints

- Do not create an implementation before completing the searches.
- Do not add a parallel provider, sender, adapter, Feign client, DTO or configuration key when an
  existing one can be reused or extended.
- Do not leave two implementations of one capability.
- Do not use mock, demo, seeded or stub product data as a substitute for a real implementation.
- If a candidate is dead code, decide to delete it or adopt it; do not keep both.