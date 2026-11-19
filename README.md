<p align="center">
  <img src="iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png" alt="Someone's Home: the lit door" width="180">
</p>

<h1 align="center">Someone's Home</h1>

<p align="center"><i>The lights are off. The doors are locked. The house would like a word.</i></p>

---

**Someone's Home** is a social deduction game you play in a real house, in the dark, in silence,
with your phones as the only light.

The smart home has turned. The perimeter is armed and nobody leaves. Everyone in the building is a
**Resident**, and a few of you are *also* **Insiders**, quietly working for the house.

And when the house is done with you, your access is **revoked**.

## How a round goes

**Lights out.** Everyone spreads through the house with their phone. No talking. Not a word.

**Residents work.** Paper cards are hidden around the home. Walk to one, scan it, and complete the
**Subroutine** it gives you. Each one drives the house's **System Integrity** down. Reach zero and the
perimeter opens and everyone walks out.

**Insiders pretend to work.** They sabotage, slip between rooms by routes nobody else can take, and
**Revoke** a Resident by passing their phone close to that person's phone. It's silent and
unattributed, and it happens at arm's length in the dark. Or they trigger an **Egress**, a house-wide
crisis that two people have to contain at two cards at once, or the house wins outright.

**Someone calls a meeting.** Walk to the meeting card and scan it. Every lamp snaps to white,
every phone rings, and *now* you may speak. Argue, accuse, vote. **Restrain** someone if you're
brave. The house never confirms who anyone was: not when they're restrained, not at the vote, not
ever.

Then the lights go out again, and the next shift begins.

## House rules (the ones the app can't check)

- Don't run.
- Don't dodge when someone brings their phone towards yours.
- Keep your phone held out where it can be seen.
- Silence outside meetings is the whole game. Honour it.

You need **5 to 18 people**, **one UWB iPhone each** (iPhone 11 or later, not SE), a home with a few
rooms, and about fifteen minutes for the host to walk the house once and place the cards. The house
remembers your home after that.

## Status

In active development, and not ready for a game night yet. The design still moves, and the
[decision log](_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/decision-log.md)
records every change and why.

## Run it yourself

You'll need a Mac with Xcode, [mise](https://mise.jdx.dev) (it provides the right JDK), and an Apple
developer Team ID for signing to a phone.

```sh
# the tests
PATH="$HOME/.local/share/mise/shims:$PATH" ./gradlew check

# every phone plugged into this Mac, playtest build
SOMEONES_HOME_DEVELOPMENT_TEAM=<your team id> ./run-app.sh device playtest

# layout only: the simulator has no UWB, camera, torch or haptics, so it can't play the game
./run-app.sh simulator playtest
```

It's Kotlin Multiplatform and Compose Multiplatform, iPhone first. The game is decided by one phone
acting as the house: it holds the whole state of the round, it is deterministic and replayable, and
it tells each phone only what that phone is allowed to know.

If you're reading the code, start with [`CLAUDE.md`](CLAUDE.md) (the vocabulary is
closed, and it's enforced) and [`_bmad-output/project-context.md`](_bmad-output/project-context.md).

## Licence

All rights reserved, except one: you may build it and play it privately on your own phones. You
may not share builds, sell it or reuse any part of it. See [LICENSE](LICENSE) for the details.
