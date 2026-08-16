# Someone's Home

A co-located social deduction game played in the host's real house, in the dark, in enforced silence, with phones as the only light. Kotlin Multiplatform + Compose Multiplatform, iPhone first, Android on the roadmap.

## Read this first

**`_bmad-output/project-context.md`** — the non-negotiable implementation rules. Read it before writing any code. It is short on purpose.

The three that catch people out:

1. **Never early-return on invalid in a client-visible path.** The absent effect *is* the leak.
2. **The redaction schema fails closed.** A new `Effect` with no schema entry ships to nobody.
3. **The iOS Simulator cannot run this game** — no BLE, torch, camera or haptics. Never propose simulator verification.

## Vocabulary (enforced by lint in `model`, `core`, `ui`)

**Resident** / **Insider** — never resident, insider, traitor, guest.
**Revoke** = the Insider's ability. **Restrain** = the group's action at a meeting.
**These are not synonyms and must never be collapsed** — one is system power lent by the house, the other is a physical act the house cannot prevent.

## Where things are

| | |
|---|---|
| Implementation rules | `_bmad-output/project-context.md` |
| Architecture | `_bmad-output/planning-artifacts/game-architecture.md` |
| Design intent | `_bmad-output/planning-artifacts/gdds/gdd-GuestNetwork-2026-08-16/gdd.md` |
| Stories | `.../epics.md` |
| Why anything is the way it is | `.../decision-log.md` |

⚠️ **`design-record.md` at the repo root is SUPERSEDED.** It carries a warning header with a was/is table. Do not act on it without checking the GDD.

*(The `gdd-GuestNetwork-*` path is historical — the game was renamed from Guest Network. Rename at repo init.)*
