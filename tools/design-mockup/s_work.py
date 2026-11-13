from kit import *

D = DARK
PAL = {"bright": DARK, "medium": MEDIUM, "dark": CONCEAL}


def build():
    o = "\n<!-- ============ WORK ============ -->\n\n"

    # WORK ORDER (D-114): a menu, not a queue. Only the current row is tappable.
    o += screen("sWork", f'''
  {hdr("SUBROUTINES", D, back="goHome", back_label="Back to home", meta="3 OF 7 DONE")}
  <div style="display:flex;flex-direction:column;gap:6px">
    <sc-for list="{{{{ workRows }}}}" as="w" hint-placeholder-count="7">
      <sc-if value="{{{{ w.isNext }}}}" hint-placeholder-val="{{{{ false }}}}">
        <button type="button" onClick="{{{{ goScan }}}}" style="box-sizing:border-box;min-height:60px;border:none;background:#FFC759;color:#000;padding:0 12px;display:flex;align-items:center;gap:12px;text-align:left;font-family:{FONT};cursor:pointer">
          <span aria-hidden="true" style="flex:0 0 auto;box-sizing:border-box;width:22px;height:22px;border:2px solid #000"></span>
          <span style="flex:1;font-size:28px;line-height:1">{{{{ w.name }}}}</span>
          <span style="display:flex;align-items:center;gap:8px;font-size:22px;line-height:1">{{{{ w.where }}}}{shape("w.d", "w.fill", 16, label_hole="w.shapeName")}{lightmark("w", "#000")}</span>
        </button>
      </sc-if>
      <sc-if value="{{{{ w.isRow }}}}" hint-placeholder-val="{{{{ true }}}}">
        <div style="box-sizing:border-box;min-height:60px;border:1px {{{{ w.edgeStyle }}}} {{{{ w.edge }}}};background:{{{{ w.bg }}}};padding:0 12px;display:flex;align-items:center;gap:12px;color:{{{{ w.ink }}}}">
          <span aria-hidden="true" style="flex:0 0 auto;box-sizing:border-box;width:22px;height:22px;border:1px {{{{ w.edgeStyle }}}} {{{{ w.boxEdge }}}};background:{{{{ w.boxFill }}}}"></span>
          <span style="flex:1;font-size:28px;line-height:1;text-decoration:{{{{ w.deco }}}}">{{{{ w.name }}}}</span>
          <span style="display:flex;align-items:center;gap:8px;font-size:22px;line-height:1;color:{{{{ w.whereInk }}}}">{{{{ w.where }}}}<sc-if value="{{{{ w.hasShape }}}}" hint-placeholder-val="{{{{ true }}}}">{shape("w.d", "w.fill", 16, label_hole="w.shapeName")}{lightmark("w")}</sc-if></span>
        </div>
      </sc-if>
    </sc-for>
  </div>
  <div style="margin-top:auto;display:flex;align-items:center;gap:6px;font-size:18px;line-height:1;color:#C08A2E"><span style="{SILK};margin-right:auto">LIGHT</span>DARK{lightmark("keyDark")}MEDIUM{lightmark("keyMedium")}BRIGHT{lightmark("keyBright")}</div>''', D, "WORK ORDER. The light mark is the promise of how much light you stand in (D-106, D-113).")

    # SCAN (D-159): a ten-second window from the tap. No pulse: the ring is still (D-093).
    o += f'''<!-- SCAN. The screen is the lamp, edge to edge. The ten-second window starts at the SCAN tap (D-159).
     The ring does not pulse: a local animation the core never emitted (D-093). -->
<sc-if value="{{{{ sScan }}}}" hint-placeholder-val="{{{{ true }}}}">
<div style="position:absolute;inset:0;background:#FFC759;color:#000;display:flex;flex-direction:column;padding:36px 20px 20px;gap:16px;font-family:{FONT}">
  <div style="flex:0 0 auto;height:34px;box-sizing:border-box;display:flex;align-items:center;gap:8px;padding-bottom:4px;border-bottom:1px solid #000;{SILK};letter-spacing:.04em">
    <div aria-hidden="true" style="flex:0 0 auto;display:flex;align-items:flex-end;gap:2px;height:12px"><div style="width:3px;height:5px;background:#5A3D0E"></div><div style="width:3px;height:8px;background:#5A3D0E"></div><div style="width:3px;height:12px;background:#5A3D0E"></div></div>
    <span style="flex:1;min-width:0">SCANNING</span>
    <span style="flex:0 0 auto;display:flex;align-items:center;gap:4px;border:1px solid #000;padding:3px 4px"><span aria-hidden="true" style="box-sizing:border-box;width:9px;height:9px;border:1px solid #000;border-radius:50%;display:flex;align-items:center;justify-content:center"><span style="width:3px;height:3px;background:#000"></span></span>ARMED</span>
  </div>
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;text-align:center">
    <div aria-hidden="true" style="position:relative;width:96px;height:96px"><div style="position:absolute;inset:0;border:3px solid #000;border-radius:50%"></div><div style="position:absolute;inset:22px;border:2px solid #5A3D0E;border-radius:50%"></div><div style="position:absolute;left:42px;top:42px;width:12px;height:12px;background:#000;border-radius:50%"></div></div>
    <div style="font-size:44px;line-height:.9">TURN THE SCREEN<br>TO THE MARKER</div>
  </div>
  {meter("scanCells", 12)}
  <button type="button" onClick="{{{{ goHome }}}}" style="flex:0 0 auto;box-sizing:border-box;height:64px;border:2px solid #000;background:none;color:#000;font:400 30px/1 {FONT};letter-spacing:.08em;cursor:pointer">STOP NOW</button>
</div>
</sc-if>

'''

    # CAUGHT (D-188, D-194, D-196): the briefing is read here, at the card. No flavour headline.
    o += screen("sScanCaught", f'''
  {hdr("CAUGHT", D)}
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;text-align:center">
    <svg role="img" aria-label="triangle marker" viewBox="0 0 16 16" width="88" height="88" fill="none" stroke="#FFC759" strokeWidth="1.1" strokeLinejoin="miter"><path d="M8 3l5 10H3z"></path></svg>
    <div style="display:flex;align-items:center;gap:10px;font-size:26px;line-height:1;color:#E39A22">GARAGE . SUBROUTINE 4 OF 7{lightmark("nextLight")}</div>
  </div>
  <div style="flex:0 0 auto;border-top:1px solid #5A3D0E;border-bottom:1px solid #5A3D0E;padding:16px 0;text-align:center;font-size:24px;line-height:1.15">THE PHONE WILL BUZZ A RHYTHM.<br>TAP IT BACK BY FEEL.</div>
  {primary("BEGIN", "goSubHandshake", D, 32)}
  {quiet("NOT THIS ONE", "goWork", D)}''', D, "CAUGHT. The briefing is read here, at the card, where the scan has already lit you (D-188).")

    def refusal(key, head, text, comment):
        return screen(key, f'''
  {hdr("SCAN", D)}
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;text-align:center;padding-bottom:40px">
    <svg aria-hidden="true" viewBox="0 0 64 64" width="72" height="72" fill="none" stroke="#E39A22" strokeWidth="3" strokeLinecap="square"><circle cx="32" cy="32" r="26"></circle><path d="M14 50L50 14"></path></svg>
    <div style="font-size:52px;line-height:.85">{head}</div>
  </div>
  {secondary("SCAN ANOTHER", "goScan", D, 30, 64, "border:2px solid #FFC759")}
  {quiet("SEE MY SUBROUTINES", "goWork", D)}''', D, comment)

    o += refusal("sScanBad", "NOTHING FOR<br>YOU HERE", None, "NOTHING FOR YOU HERE. A real marker, not on your list (D-124).")
    o += refusal("sScanUnknown", "NOT A MARKER", None, "NOT A MARKER")

    o += screen("sFiles", f'''
  {hdr("FILES", D, back="goHome", back_label="Back to home", meta="1 ITEM")}
  <div style="border:2px solid #FFC759;padding:14px 16px;display:flex;flex-direction:column;gap:8px">
    <div style="font-size:40px;line-height:.85">DISK 01</div>
    <div style="font-size:22px;line-height:1.1;color:#E39A22">DEGRADED . IN HAND<br>DESTINATION . RACK</div>
  </div>
  {cap("NOTHING ELSE OPENS UNTIL IT IS DELIVERED", D, size=24)}''', D, "CARRYING. One item, and every other app refuses.", gap=14)

    o += screen("sNotes", f'''
  {hdr("NOTES", D, back="goHome", back_label="Back to home", meta="UNTITLED")}
  <div style="border-top:1px solid #5A3D0E;padding-top:18px;font-size:34px;line-height:1.1">{{{{ noteLine }}}}</div>
  {cap("THESE ARE NOT YOUR NOTES", D, center=True, extra="margin-top:auto")}''', D, "NOTES. Read only. It writes to you.", gap=14)

    o += refusal("sTermBusy", "ONE READER<br>AT A TIME", None,
                 "THE TERMINAL IS TAKEN. A scan of the T card while someone else is reading (M11, D-199).")

    def live(key, comment, title, counts, button, static=False):
        return screen(key, f'''
  {hdr(title, D, back="goHome" if key != "sTimelapse" else "goTermLive", back_label="Back")}
  {floor_strip(D)}
  <div style="flex:1;min-height:0;position:relative">{plan("mapCells", "mapRooms", counts=counts, static=static)}</div>
  {button}''', D, comment, gap=10)

    BUDGET = ('<div style="flex:0 0 auto;display:flex;align-items:center;min-height:40px;border-top:1px solid #5A3D0E;border-bottom:1px solid #5A3D0E;'
              'font-size:22px;line-height:1;color:#C08A2E"><span style="flex:1">MOTION BUDGET</span>HOLDING</div>')
    o += live("sTermLive", "TERMINAL LIVE. Opened only by scanning the T card (D-199). Counts per room, never dots, dimming through fresh, stale and cold. Reading is an uncompletable task: moving too much closes it.", "TERMINAL . LIVE", "termRooms",
              BUDGET + "\n" + secondary("TIMELAPSE . 2:00 AT 6X", "goTimelapse", D, extra="color:#FFC759"), static=True)
    o += live("sTermStatic", "TERMINAL JAMMED. Static drowns the live view for ten seconds, real counts faintly beneath (D-186). Underneath, nothing changes.", "TERMINAL . LIVE", "termRooms",
              BUDGET + "\n" + secondary("TIMELAPSE . 2:00 AT 6X", "goTimelapse", D, extra="color:#FFC759"), static=True)
    o += live("sTimelapse", "TIMELAPSE. Two minutes at 6x, oldest to newest, plays once.", "TERMINAL . -1:12", "lapseRooms",
              '<div aria-hidden="true" style="flex:0 0 auto;display:flex;height:8px;box-sizing:border-box;border:1px solid #5A3D0E"><div style="width:44%;background:#E39A22"></div></div>\n'
              + BUDGET + "\n" + secondary("STOP THE PLAYBACK", "goTermLive", D, extra="color:#FFC759"), static=True)

    o += "<!-- ============ SUBROUTINES. One screen each. Instructions live on the card, never here (D-188). ============ -->\n\n"

    def sub(key, title, level, task, submit=None, comment=""):
        P = PAL[level]
        sb = ""
        if submit:
            sb = secondary(submit, None, P, 28, 60, f"border:2px solid {P['ink']};color:{P['ink']}")
        return screen(key, f'''
  <div style="flex:0 0 auto;display:flex;align-items:center;gap:12px;min-height:56px"><span style="flex:1;font-size:32px;line-height:1">{title}</span>{lightmark("light_" + level)}</div>
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;justify-content:center;gap:16px">{task}</div>
  <div aria-live="polite" style="flex:0 0 auto;min-height:24px;text-align:center;font-size:22px;line-height:1;color:{P["dim"]}"></div>
  <div style="flex:0 0 auto;display:flex;align-items:center;min-height:44px;border-top:1px solid {P["faint"]};border-bottom:1px solid {P["faint"]};font-size:22px;line-height:1;color:{P["dim"]}"><span style="flex:1">MOTION BUDGET</span>HOLDING</div>
  {sb}
  {quiet("STOP NOW", "goWork", P, extra=f"color:{P['dim']}")}''', P, comment or f"{title}. {level.upper()}.", gap=10)

    C, M = CONCEAL, MEDIUM
    # Replay (bright): dots in two columns, then the sequence so far
    replay = ('<div style="display:grid;grid-template-columns:repeat(2, 72px);gap:22px 64px;justify-content:center">'
              + "".join(f'<button type="button" aria-label="Dot {i+1}" style="box-sizing:border-box;width:72px;height:72px;border:2px solid #FFC759;border-radius:50%;background:{"#FFC759" if i in (1, 4) else "none"};cursor:pointer"></button>' for i in range(6))
              + '</div><div aria-hidden="true" style="display:flex;gap:6px;justify-content:center">'
              + "".join(f'<div style="box-sizing:border-box;width:28px;height:12px;border:1px solid #FFC759;background:{"#FFC759" if i < 2 else "none"}"></div>' for i in range(5)) + '</div>')
    o += sub("sSubReplay", "REPLAY", "bright", replay)

    interrupt = ('<button type="button" aria-label="Tap while the sweep is inside the band" style="position:relative;box-sizing:border-box;height:200px;border:1px solid #8A5C12;background:none;cursor:pointer">'
                 '<span aria-hidden="true" style="position:absolute;top:0;bottom:0;left:38%;width:24%;border-left:2px dashed #E39A22;border-right:2px dashed #E39A22"></span>'
                 '<span aria-hidden="true" style="position:absolute;top:12px;bottom:12px;left:22%;width:6px;background:#E39A22"></span></button>')
    o += sub("sSubInterrupt", "INTERRUPT", "medium", interrupt)

    o += sub("sSubParity", "PARITY CHECK", "bright",
             f'<div style="aspect-ratio:1 / 1;width:100%;display:grid;grid-template-columns:repeat(6, minmax(0, 1fr));gap:4px"><sc-for list="{{{{ parityCells }}}}" as="p" hint-placeholder-count="36"><button type="button" aria-label="Block" style="box-sizing:border-box;background:{{{{ p.f }}}};border:{{{{ p.b }}}};padding:0;cursor:pointer"></button></sc-for></div>',
             "SUBMIT", "PARITY CHECK. BRIGHT. One block breaks the pattern. The ring is your choice, which you can change until you submit.")

    sniff = ('<div style="display:flex;gap:10px;height:260px">'
             '<button type="button" aria-label="Left buzzed more" style="flex:1;border:1px solid #5A3D0E;background:#000;color:#C08A2E;font:400 28px/1 ' + FONT + ';cursor:pointer">LEFT</button>'
             '<button type="button" aria-label="Right buzzed more" style="flex:1;border:1px solid #5A3D0E;background:#000;color:#C08A2E;font:400 28px/1 ' + FONT + ';cursor:pointer">RIGHT</button></div>')
    o += sub("sSubSniff", "SNIFF", "dark", sniff, "SUBMIT", "SNIFF. DARK. Everything stays black until answered (D-137).")

    heights = [5, 2, 4, 3, 6]
    dealloc = ('<div style="display:flex;gap:14px;justify-content:center;align-items:flex-end;height:260px">'
               + "".join('<button type="button" aria-label="Column ' + str(i+1) + '" style="display:flex;flex-direction:column-reverse;gap:6px;border:none;background:none;padding:0;cursor:pointer">'
                         + "".join('<span style="width:40px;height:30px;background:#FFC759"></span>' for _ in range(h)) + '</button>' for i, h in enumerate(heights)) + '</div>')
    o += sub("sSubDeallocate", "DEALLOCATE", "bright", dealloc, "SUBMIT", "DEALLOCATE. BRIGHT. Tap the columns down to the shortest (D-138).")

    drift = ('<button type="button" aria-label="Tap where the dot is" style="position:relative;box-sizing:border-box;height:120px;border:1px solid #8A5C12;background:none;cursor:pointer">'
             '<span aria-hidden="true" style="position:absolute;top:0;bottom:0;left:36%;width:40%;background:' + HATCH + ';border-left:1px solid #8A5C12;border-right:1px solid #8A5C12"></span></button>')
    o += sub("sSubDrift", "DRIFT", "medium", drift, None, "DRIFT. MEDIUM. The dot moves, then hides. When the phone buzzes, tap where it is (D-140).")

    def cellrow(lbl, n, of=5):
        return (f'<div style="display:flex;align-items:center;gap:12px"><span style="width:64px;{SILK};color:#C08A2E">{lbl}</span>'
                + "".join(f'<span style="box-sizing:border-box;flex:1;height:22px;border:1px solid #5A3D0E;background:{"#8A5C12" if i < n else "none"}"></span>' for i in range(of)) + '</div>')
    short = (cellrow("ASKS", 3) + cellrow("DOWN", 1)
             + '<div role="img" aria-label="Hold your fingers here" style="box-sizing:border-box;height:180px;border:1px solid #5A3D0E"></div>')
    o += sub("sSubShort", "SHORT", "dark", short, None, "SHORT. DARK. No numerals and no progress bar: the cells are the count.")

    trace = ('<svg role="img" aria-label="Node graph" viewBox="0 0 320 280" width="100%" style="display:block">'
             '<g stroke="#8A5C12" strokeWidth="2" fill="none"><path d="M40 40L140 70L250 40M140 70L120 160L220 150L280 240M120 160L60 240L170 250L220 150M250 40L220 150"></path></g>'
             '<path d="M40 40L140 70L120 160" stroke="#E39A22" strokeWidth="4" fill="none"></path>'
             + "".join(f'<rect x="{x-12}" y="{y-12}" width="24" height="24" fill="{f}" stroke="#E39A22" strokeWidth="2"></rect>'
                       for x, y, f in [(40, 40, "#E39A22"), (140, 70, "#000"), (250, 40, "#000"), (120, 160, "#000"), (220, 150, "#000"), (60, 240, "#000"), (170, 250, "#000"), (280, 240, "#E39A22")])
             + '</svg>')
    o += sub("sSubTrace", "SIGNAL TRACE", "medium", trace, "SUBMIT", "SIGNAL TRACE. MEDIUM. Two ends are marked. Tap node to node.")

    jam = ('<div role="img" aria-label="Two rings, apart" style="position:relative;height:220px">'
           '<div style="position:absolute;left:50%;top:50%;width:140px;height:140px;transform:translate(-62%,-50%);border:3px solid #E39A22;border-radius:50%"></div>'
           '<div style="position:absolute;left:50%;top:50%;width:140px;height:140px;transform:translate(-30%,-50%);border:3px solid #8A5C12;border-radius:50%"></div></div>'
           '<div style="display:flex;gap:10px"><button type="button" aria-label="Move apart" style="flex:1;height:56px;border:1px solid #8A5C12;background:none;color:#E39A22;font:400 36px/1 ' + FONT + ';cursor:pointer">-</button>'
           '<button type="button" aria-label="Move together" style="flex:1;height:56px;border:1px solid #8A5C12;background:none;color:#E39A22;font:400 36px/1 ' + FONT + ';cursor:pointer">+</button></div>')
    o += sub("sSubJam", "JAM", "medium", jam, "SUBMIT", "JAM. MEDIUM. Tap until the rings sit on one another.")

    handshake = ('<div aria-hidden="true" style="display:flex;gap:6px;justify-content:center">'
                 + "".join(f'<div style="box-sizing:border-box;width:30px;height:14px;border:1px solid {"#8A5C12" if i < 3 else "#5A3D0E"};background:{"#8A5C12" if i < 3 else "none"}"></div>' for i in range(5))
                 + '</div><button type="button" style="box-sizing:border-box;height:180px;border:1px solid #5A3D0E;background:none;color:#C08A2E;font:400 40px/1 ' + FONT + ';letter-spacing:.2em;cursor:pointer">TAP</button>')
    o += sub("sSubHandshake", "HANDSHAKE", "dark", handshake, None, "HANDSHAKE. DARK. Near-black and haptic: the rhythm buzzes when the screen opens.")

    return o
