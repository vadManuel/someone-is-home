# Someone's Home

A co-located social deduction game played in the host's real house, in the dark, in enforced silence, with phones as the only light. Kotlin Multiplatform + Compose Multiplatform, iPhone first, Android on the roadmap.

## Read this first

**`_bmad-output/project-context.md`** — the non-negotiable implementation rules. Read it before writing any code. It is short on purpose.

The three that catch people out:

1. **Never early-return on invalid in a client-visible path.** The absent effect *is* the leak.
2. **The redaction schema fails closed.** A new `Effect` with no schema entry ships to nobody.
3. **The iOS Simulator cannot run this game** — no BLE, torch, camera or haptics. Never propose simulator verification.

## Vocabulary (enforced by lint in `model`, `core`, `ui`)

**Resident** / **Insider** are the only role words. Everyone is a Resident; some are *also* Insiders.
**Revoke** = the Insider's ability. **Restrain** = the group's action at a meeting.
**These are not synonyms and must never be collapsed** — one is system power lent by the house, the other is a physical act the house cannot prevent.
**Subroutine** = the unit of assigned work. **System Integrity** = the collective progress meter. **Egress** = the Insider-triggered house crisis. **Override** = the Insider-only route between rooms. **Passage** = a map-editor shape tag, alongside room and stairs.

That list is the whole vocabulary. If a word you want isn't on it, it's wrong — the lint holds the rejected forms and will tell you.

## Where things are

| | |
|---|---|
| Implementation rules | `_bmad-output/project-context.md` |
| Architecture | `_bmad-output/planning-artifacts/game-architecture.md` |
| Design intent | `_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/gdd.md` |
| Stories | `.../epics.md` |
| Why anything is the way it is | `.../decision-log.md` |

⚠️ **The vocabulary above is closed.** Use those words and no synonyms — in code, docs, comments, commit messages and filenames alike. If you need a term the list doesn't have, raise it rather than inpassage use one. A `pre-commit` hook checks, but it does not travel with a clone, so the rule stands on its own.

*(The game is **Someone's Home**. Every path and identifier — repo, directories, `project_name` — uses `someone-is-home`; the apostrophe cannot appear in a directory or repo name. Prose always keeps it.)*
