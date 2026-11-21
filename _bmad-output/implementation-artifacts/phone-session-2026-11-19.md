# Phone session, 2026-11-19: what to run, in order

Everything built on 2026-11-18 and overnight has passed the full test suite, but **nothing has been
seen on a phone**. The Simulator cannot check any of it (no UWB, haptics, camera or Bluetooth).
This page is the whole session, so nothing gets missed.

Allow about 45 minutes for part 1 and 30 minutes for part 2.

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

Install the Playtest build on two phones (`./run-app.sh device playtest`) and play a short round.
Every round is the Bluetooth and Ultra Wideband hybrid (D-308): Bluetooth ranks who is near, the
house pairs close phones the game needs for Ultra Wideband, and every Revoke, report and placement
reads the distance. There is no switch to set. Grant NEARBY DEVICES on both phones: a phone that
refused it cannot be paired, so it can neither Revoke nor be Revoked, report nor be reported.
Tick each line as you see it.

**Setup**
- [ ] PERMISSIONS has a NEARBY DEVICES row under BLUETOOTH. Tapping it raises iOS's question,
      and the row then reads GRANTED. CONTINUE goes straight to JOIN A HOME, with no NameDrop
      step. (D-303)

**Lobby and start**
- [ ] A third phone joining **during** a round is refused, and reads "ROUND IN PROGRESS / YOU JOIN
      THE NEXT ONE". (D-287)
- [ ] Typing a name another phone already has, in any case, shows "NAME TAKEN". (D-288)
- [ ] At lights out, each phone stands on plain PERIMETER ARMED. CONTINUE raises the house's
      message and the dim **on that phone only**, with no buzz. (D-293)

**Revoke, on the Insider's phone**
- [ ] Page 2 after arming: ARMED, then 20 s later **COOLING DOWN, dimmed**, then READY about 61 s
      after the hold. It never says READY early. (D-286)
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

**Release build**
- [ ] `./run-app.sh device release` on a UWB phone launches to the boot screen. It must not stop
      at launch, now that it asks iOS about UWB (D-292) and carries the Nearby Interaction line
      (D-294).

Write anything that looks or feels wrong next to its line. The decision number shows where it was
built.
