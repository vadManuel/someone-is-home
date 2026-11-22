# Phone session, 2026-11-19: what to run, in order

Everything built on 2026-11-18 and overnight has passed the full test suite, but **nothing has been
seen on a phone**. The Simulator cannot check any of it (no UWB, haptics, camera or Bluetooth).
This page is the whole session, so nothing gets missed.

Allow about 45 minutes for part 1 and 30 minutes for part 2.

**Updated overnight, 2026-11-21.** Use the **Playtest** build for everything in part 2: the release
build still stops at the boot screen. A round needs **three phones** (the minimum is 3 while you
test, D-128 says 5 for release), and the "joining during a round" line needs a **fourth**. Lines
marked *(overnight)* are what the overnight audit says tests cannot cover; the full list of what is
and is not built is in `rules-audit-2026-11-21.md`.

## Part 1: three-phone UWB test (decides the UWB build)

Follow `tools/uwb-spike/README.md`, section **"Three phones"**, steps 1 to 4. In short:

1. **Two live peers.** Three phones in a triangle about 50 cm apart, held upright, nobody moving:
   `sample.sh "three-still" 30`. This answers whether the rate per pair holds or splits.
2. **One phone passes another** at 10 cm: `sample.sh "three-pass" 15`.
3. **Session limit.** Run `--probe A`, then `B`, then `C`, and record each phone's `LIMIT` line.
4. **Session options.** With two phones 1 m apart, try `--camera-assist` and `--extended-distance`
   one at a time, and record whether either beats 6.7 readings a second.

What the answers change:
- **Each phone's limit** sets the starting capacity. The build starts at 3.
- **If the rate splits per peer**, ranging fewer peers at once is better, and the house's pairing
  stays small.
- **If an option raises the rate**, the radio turns it on.

## Part 2: retests of the 2026-11-18 build

Install the Playtest build on three phones (`./run-app.sh device playtest`) and play a short round.
Every round is the Bluetooth and Ultra Wideband hybrid (D-308): Bluetooth ranks who is near, the
house pairs close phones the game needs for Ultra Wideband, and every Revoke, report and placement
reads the distance. There is no switch to set. Grant NEARBY DEVICES on both phones: a phone that
refused it cannot be paired, so it can neither Revoke nor be Revoked, report nor be reported.
Tick each line as you see it.

**Setup**
- [ ] PERMISSIONS has a NEARBY DEVICES row under BLUETOOTH. Tapping it raises iOS's question,
      and the row then reads GRANTED. CONTINUE goes straight to JOIN A HOME, with no NameDrop
      step. (D-303)
- [ ] Refuse NEARBY DEVICES on one phone: it cannot get past PERMISSIONS (no CONTINUE, the gate's
      sentence names NEARBY DEVICES, OPEN SETTINGS shows) until it is granted in Settings; back in
      the app the row reads GRANTED and CONTINUE appears. PERMISSIONS has no LOCAL NETWORK row;
      iOS asks for it on JOIN A HOME or when hosting. (D-309)
- [ ] PERMISSIONS has four rows: CAMERA, BLUETOOTH, NEARBY DEVICES, MOTION & FITNESS. There is no
      NOTIFICATIONS row, and no iOS notification prompt appears anywhere in setup. In Settings, the
      app's page has no Notifications entry. (D-326)
- [ ] On a fresh install, tap REQUEST WHAT IS MISSING once and answer each iOS prompt as it comes:
      the walk should carry on to the next row by itself, Bluetooth's answer included, without a
      second tap. If it stops after Bluetooth, note it: that is the stall the overnight run found
      on the simulators (D-314).
- [ ] On JOIN A HOME, deny the Local Network prompt: the screen reads LOCAL NETWORK IS OFF with
      OPEN SETTINGS under it. Turn Local Network on in Settings, come back, and the home appears.
      With no host running, NO HOME ON THIS NETWORK shows after about 10 s. Note the code on the
      `SEARCH FAILED code=` line in the log: -65570 is the expected one, and any other code is
      read as OFF too until this check says otherwise. (D-309)

**Lobby and start**
- [ ] A third phone joining **during** a round is refused, and reads "ROUND IN PROGRESS / YOU JOIN
      THE NEXT ONE". (D-287)
- [ ] Typing a name another phone already has, in any case, shows "NAME TAKEN". (D-288)
- [ ] At lights out, each phone stands on plain PERIMETER ARMED. CONTINUE raises the house's
      message and the dim **on that phone only**, with no buzz. (D-293)

**Revoke, on the Insider's phone**
- [ ] Page 2 after arming: ARMED, then 20 s later **COOLING DOWN, dimmed**, then READY about 61 s
      after the hold. It never says READY early. (D-286)
- [ ] Page 2's EGRESS tile, on the Insider's phone: in a round with **one** Insider the line under
      EGRESS is blank; with **two or more** it reads SHARED WITH THE OTHER INSIDER, whatever the
      host set, INSIDERS left UNKNOWN with seven to eleven phones included (the house tells each
      Insider's phone the count). A Resident's tile reads ALL SYSTEMS RESPONDING either way.
      (D-320, D-323)
- [ ] While armed, pass the Insider's phone within 10 cm of the other phone (a pass is enough;
      Ultra Wideband measures it): **both phones give one Long buzz**, and the Insider's tile goes
      straight to COOLING DOWN. (D-290, D-301)
- [ ] How long ranging takes to start: note the time you arm beside the other phone, then read the
      host's log for the first distance between the two. Write down the seconds between. (D-306)
- [ ] While a Nearby Interaction session runs (the Insider armed beside another phone, or a living
      phone beside a Revoked one), does iOS show anything on either screen: an indicator, a banner,
      a prompt? Write down what, on which phone. (D-306)

**Report**
- [ ] Hold a living phone on page 1 or page 2 within 30 cm of the Revoked phone: the page gives way
      to a screen that is only a circle, which fills smoothly over about two seconds, and the
      meeting's ring follows. Pull it away before it is full: it goes straight back to the page it
      came from. Try the same hold from inside a Subroutine, the scan, the Egress widget and the
      lantern: no circle, no meeting. Check the circle looks the same on an Insider's phone and a
      Resident's, and is no brighter than the page it replaced. (D-307)

**Egress**
- [ ] Mid-Subroutine when an Egress starts: the alert drops **over** the work. The work stays.
      Swiping clears the alert and the work is still there. (D-297)
- [ ] The same while on the camera, and on the briefing before BEGIN. (D-299)
- [ ] Scanning one of the Egress's two cards shows TAP and four pips beside the countdown. Only
      that phone feels the pulse. (D-295, D-298)
- [ ] Four taps in a row on the pulse fill the pips. A miss empties them, and **the phone
      vibrates for 3 s without stopping**. (D-298)
- [ ] With a card under the dimmed alert, the camera still reads it. (D-299 found the light drops
      to 0.32.)

**Subroutines**
- [ ] A Handshake tapped at the wrong rhythm, with the right number of taps, is REJECTED. One
      tapped close to the dealt rhythm is accepted. (D-296)

**A permission switched off**
- [ ] Mid-round, switch a permission off in Settings (Bluetooth, or NEARBY DEVICES; note whether
      iOS ends the app for it, as it does for Camera and Motion) and come back. At the next meeting the notice names that phone in the owner's
      sentence: "Resident Mia switched off a permission this game needs. If anyone switches one off
      again, the house closes and everyone starts over." Then switch one off again, on any phone:
      every phone shows THE HOUSE IS CLOSED / "A permission was switched off again. Everyone starts
      over." and returns to JOIN A HOME, and the host's returns to its lobby. Separately, only
      leaving the app (side button, swipe home) is named with "was unreachable this shift", no
      warning, and never closes the house; when both happen in one shift, that sentence comes
      first. (D-310, D-311)
- [ ] In the lobby, before lights out, switch a permission off on a guest (Settings, or Bluetooth
      in Control Centre) and come back: the guest leaves the lobby and lands on PERMISSIONS with
      that row reading REFUSED, the host's count drops by one, and with it back on the guest
      rejoins as usual. On the host: it is shown PERMISSIONS, the guests stay seated, and CONTINUE
      takes it back to its lobby. (D-311) There is no once-a-second check any more (D-326): Bluetooth
      turned off in Control Centre, without leaving the app, should let the guest go within a moment
      of the switch, on the radio's own callback (`[perms] BLUETOOTH state=... THE RADIO IS OFF`).
      If nothing happens until the guest leaves and comes back, note it: that is the finding.
- [ ] In the lobby, switch NEARBY DEVICES off in Settings on a guest and come back: the console logs
      `[perms] NEARBY DEVICES — PROBED AGAIN ON A RETURN TO THE LOBBY (D-326)`, and the guest lands
      on PERMISSIONS with NEARBY DEVICES reading REFUSED. On the host, it is shown PERMISSIONS and
      the guests stay seated. Note what the probe answered (`[perms] NEARBY DEVICES probe: ...`): if
      it says `no answer` and the guest stays (`ASKED IS NO ANSWER, THE ROW STAYS`), the switch-off
      is heard only at lights out, and that is the finding. With NEARBY DEVICES left on, leave the
      app and come back several times: the guest is never let go. (D-326)
- [ ] Mid-round, turn Bluetooth off in Control Centre (do not leave the app). At the next meeting
      the notice names that phone in the permission sentence, not "was unreachable". Note whether
      the console logs `[perms] BLUETOOTH state=... THE RADIO IS OFF (D-311)` and the radio's
      `FAULT ... PERMISSION`: if iOS keeps the radio powered for Control Centre's switch (it is
      documented as disconnecting accessories), nothing is named, and that is the finding. (D-311)

**Abilities and meetings** *(overnight)*
- [ ] **Surge** on another phone: its lamp slams bright, goes dark, reboots, and any open
      Subroutine is lost. **No sound plays**, by ruling: the game makes none (D-320), and **no tap does anything** during the
      whole sequence, STOP NOW included (D-182, D-313).
- [ ] **Surge mid-hold**: on the surged phone, start holding REVOKE or EGRESS (or stand in a
      report circle, or tap SCAN) and have the Surge land during it. The hold lets go at the slam
      and nothing arms; the report circle goes; the camera closes at the slam and its two buzzes
      come then, not at the reboot. (D-323)
- [ ] **Static**: the Terminal's reader sees the noise over the plan, and **every living phone**
      gets a quiet banner, **MONITORING JAMMED**, over whatever it is showing: no dim, no buzz, and
      the phone stays where it was (the reader stays at the Terminal). It stands until swiped and is
      kept in the house's messages. A phone on the couch gets nothing (D-186, D-319).
- [ ] **The Terminal and the couch show the home with counts** (D-316): every phone gets the
      plan at LIGHTS OUT. Scan T: within a second the home's own rooms appear with a number in each,
      the station's room outlined, the station's storey on the strip, bright where somebody performed
      in the last 15 s, dimmer to 45 s, dimmest after (and 0 where nobody has worked). Walk away from
      T: the counts go. Another phone scanning T meanwhile gets ONE READER AT A TIME and no counts.
      On the couch (after the meeting that follows a Revoke): the same plan, every count in full
      ink, no outline, live; a Static changes nothing there. Note how close the counts come to who is
      really where: bleed into the next room and a missed person are the design.
- [ ] **Every storey** (D-319): on a home of two storeys, tap the other floor on the strip at the
      Terminal and on the couch. Its rooms and counts appear at once; tap back. The Terminal opens on
      the station's storey and the couch on the first, each time they are opened.
- [ ] **The timelapse** (D-319): at T after a minute or so, TIMELAPSE reads `TIMELAPSE . m:ss AT
      6X` with the history it holds (2:00 once the shift is two minutes old). Press it: within a
      second the playback opens, oldest to newest, a frame every third of a second, the counts in one
      dim ink, the header counting toward the present. It plays once and returns to the live view.
      Then TIMELAPSE is dimmed and reads `TIMELAPSE . READY IN m:ss`, counting down; walk away and let
      another phone scan T: it shows the same wait (the cooldown is the station's). STOP THE PLAYBACK
      partway: the wait is shorter by the share not watched. A Static during the history plays back as
      about 1.7 s of noise with no counts. After a meeting the history starts again from nothing, and
      the button is ready once it holds a frame, two seconds after the meeting ends, whatever the
      wait was before. Nobody but the reader is shown a frame.
- [ ] **The meeting ring** on a real phone: LONG, pause, LONG, repeating until answered, felt in a
      pocket and heard by nobody (D-160).
- [ ] **Meeting light**: does the room go bright together at the call and back to dim together at
      the end? The house sets no lamp level for it, so watch closely (D-168).
- [ ] **Check-in, vote and tally**: the gate waits for every living phone; the vote casts on a 2 s
      hold and can be changed until it is cast; the tally shows a count to the living and who voted
      for whom only to the out (D-104, D-141, D-195).
- [ ] **Endings**: play one round to an ending and check the route it names.

**Scanning and Subroutines** *(overnight)*
- [ ] A card's verdict buzzes: **ACCEPTED one short buzz** ("yes"), **REJECTED two** ("no"). A
      card with nothing for you: the scan's "yes", then NOTHING FOR YOU HERE with two buzzes. The
      same on an Insider's phone and a Resident's, and on that phone only. Scanning one of the
      Egress's two cards gives the scan's "yes" and no "no". REJECTED's two buzzes feel the same as
      a scan that runs out of time. (D-320)
- [ ] Scan a QR code that is not a game card: a "yes" buzz, then nothing. Scan a game card this
      home never registered (one from another home, a spare from the deck, or a spare meeting
      card): a "yes" buzz, then NOT A MARKER with a "no" buzz (two vibrates), on that phone only.
      No other phone reacts and no meeting is called. The "no" buzz is provisional (D-318).
- [ ] **Drift**: tap before the buzz. It is handed over and REJECTED, and the attempt is gone
      (D-165, D-313).
- [ ] An **armed pass at an already-Revoked phone**: nothing happens, no buzz, the tile stays ARMED
      and the Revoke is not spent. The owner kept this (D-290, D-320).
- [ ] **Page 1** has four tiles, FILES, MESSAGES, SETTINGS and CAMERA, and no NOTES, on an
      Insider's phone and a Resident's alike (D-315).

**The work order and carrying** *(D-322)*
- [ ] **The mix**: open SUBROUTINES at LIGHTS OUT. Every phone's list is the same length and holds
      one ARRAY WIPE and short and medium Subroutines, plus one or more LOCKED · WAITING UPSTREAM
      lines, and **no MEMORY DUMP**: it is not dealt until its screens are designed (D-323). An
      Insider's list looks the same.
- [ ] **Array Wipe**: scan the ARRAY WIPE's first card (Spares). The phone goes to page 1 and the
      widget says CARRYING · DISK 01 · TO RACK · NOTHING ELSE OPENS UNTIL IT IS DELIVERED, with the
      padlock on the strip; FILES shows DISK 01, IN HAND, DESTINATION · RACK. Scan any other card of
      yours: NOTHING FOR YOU HERE, and the disk stays. Scan the Rack: TO DISPOSAL. Scan Disposal: the
      widget goes back to the list. Do it twice and the line is struck off. Say whether landing on
      page 1 after each step is right (provisional), and whether the widget's words read.
- [ ] **Two phones on one circuit**: two players carry disks to the same Rack at once. Both finish.
- [ ] **A disk through a meeting**: call a meeting while carrying. After it, the widget still says
      CARRYING and other cards still answer NOTHING FOR YOU HERE (F-014).
- [ ] **you're up**: finish a Subroutine and wait. Somewhere in the house, 20 s to 2 min later, a
      phone that had a LOCKED line gets the NUMBER WITHHELD · you're up banner **with the same buzz
      as the house's opening text** (D-324), and the line now names its Subroutine. Nobody else's
      phone changes. If a meeting is called in between, it lands after the meeting.
- [ ] **Chains are Residents only** *(D-324)*: an Insider's LOCKED line looks the same as a
      Resident's and unlocks the same way, 20 s to 2 min after some Resident finishes something. An
      Insider finishing their own work never unlocks anybody's line. Say whether an Insider's unlock
      ever feels different.
- [ ] **A Revoke strands a line**: if a Revoked player held your LOCKED line, it never opens. Note
      whether that is what happens and how it feels at the next meeting (D-146).
- [ ] **No Memory Dump anywhere**: no phone's list holds one (D-323), so every line is one the
      phone can finish. Say whether the meter reaches 0 when everyone finishes.
- [ ] **Nothing tells you someone is waiting on you** (D-324: no chain notice at lights out; the
      LOCKED · WAITING UPSTREAM row is the notice).

**The motion budget** *(D-321; every number is provisional, so these lines are where they are first felt)*
- [ ] **Still is free**: open a Subroutine and hold the phone as you naturally would for its whole
      length, tapping as it asks. The line under MOTION BUDGET / HOLDING stays lit end to end and
      the screen stays. Try it on a short one (Replay) and a medium one (Handshake), on both phones.
- [ ] **Turning to look behind you**: on a Subroutine, turn your body to check the room behind you
      and back. Note how much of the line goes (M2 wants this lenient).
- [ ] **Walking away closes it**: open a Subroutine, then walk off. The line drains as you go, and
      within a few steps the phone is back on the work order, as if you had pressed STOP NOW, with no
      buzz. Scan the card again to start over. Say how many steps it took, and whether the drain was
      a warning you saw before it closed (*failure must never be a surprise*).
- [ ] **The station**: read the Terminal and walk off. The live view closes to the springboard, and
      a second phone scanning the T card straight after is let in (the station is free). Same from
      the timelapse while it plays.
- [ ] **Both roles feel the same**: the Insider's phone drains and closes exactly as a Resident's.
- [ ] **MOTION & FITNESS off**: switch it off in Settings, come back, open a Subroutine and walk
      off. Nothing drains and nothing closes (and the permission warning is the house's, as before).
- [ ] **The line itself**: say whether the lit lower hairline reads as the budget, and whether it
      should be somewhere else or another ink (its look is provisional).

**Release build**
- [ ] `./run-app.sh device release` on a UWB phone launches to the boot screen. It must not stop
      at launch, now that it asks iOS about UWB (D-292) and carries the Nearby Interaction line
      (D-294).

**The owner's 2026-11-22 rulings** *(D-323)*
- [ ] **The Residents' ending**: RESIDENTS WIN, the house's card, then the name tiles alone, with
      no THE RESIDENTS label and no count beside it.
- [ ] **Settings** ends with LEAVE THIS HOUSE and HOST ONLY beside it.

Write anything that looks or feels wrong next to its line. The decision number shows where it was
built.
