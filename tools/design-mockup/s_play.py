from kit import *

D = DARK


def dock():
    def item(label, go, d, lit=False):
        bg, ink, size = ("#FFC759", "#000", 24) if lit else ("none", "#FFC759", 20)
        return (f'<button type="button" onClick="{{{{ {go} }}}}" style="box-sizing:border-box;height:64px;border:none;background:{bg};'
                f'color:{ink};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;'
                f'font:400 {size}px/1 {FONT};letter-spacing:{".08em" if lit else "0"};cursor:pointer">'
                f'<svg aria-hidden="true" viewBox="0 0 28 28" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2.5" '
                f'strokeLinejoin="miter" strokeLinecap="square"><path d="{d}"></path></svg>{label}</button>')
    return (f'<div style="flex:0 0 auto;display:grid;grid-template-columns:repeat(3, minmax(0, 1fr));gap:8px;align-items:center;'
            f'border-top:1px solid #5A3D0E;padding-top:12px">'
            + item("SUBROUTINES", "goWork", "M5 5h18v18H5z M9 11h10 M9 16h6")
            + item("SCAN", "goScan", "M4 10V4h6 M18 4h6v6 M24 18v6h-6 M10 24H4v-6 M9 14h10", True)
            + item("LOCK SCREEN", "goLock", "M7 13h14v11H7z M10 13V7h8v6") + '</div>')


def pagebar(page, go, label):
    j = "flex-start" if page == 1 else "flex-end"
    return (f'<button type="button" onClick="{{{{ {go} }}}}" aria-label="{label}" style="margin-top:auto;align-self:center;height:28px;'
            f'padding:0 24px;border:none;background:none;display:flex;align-items:center;cursor:pointer">'
            f'<span style="display:flex;justify-content:{j};width:56px;height:4px;background:#5A3D0E"><span style="width:50%;background:#FFC759"></span></span></button>')


def build():
    o = "\n<!-- ============ ARMING ============ -->\n\n"

    o += screen("sArmed", f'''
  <div style="flex:1;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:18px;padding-bottom:60px;text-align:center">
    <div style="font-size:72px;line-height:.85;letter-spacing:.04em">PERIMETER<br>ARMED</div>
    <div aria-hidden="true" style="width:48px;height:2px;background:#8A5C12"></div>
    <div style="font-size:26px;line-height:1.1;color:#C08A2E">6 RESIDENTS<br>ACCOUNTED FOR</div>
    {secondary("PROCEED", "goHome", D, extra="margin-top:20px;min-width:200px;color:#E39A22")}
  </div>''', D, "PERIMETER ARMED. The lights have just gone out: outline only, nothing that flares.")

    # MESSAGES (D-105: no read state, so no unread count and no NEW tag)
    o += screen("sReveal", f'''
  {hdr("MESSAGES", D, back="goHome", back_label="Back to home", meta="{{ inboxCount }} MESSAGES")}
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;border-top:1px solid #5A3D0E;overflow:hidden">
    <sc-for list="{{{{ inbox }}}}" as="m" hint-placeholder-count="6"><button type="button" onClick="{{{{ goRevealThread }}}}" style="flex:0 0 auto;display:flex;flex-direction:column;justify-content:center;gap:6px;min-height:60px;border:none;border-bottom:1px solid #5A3D0E;background:none;padding:6px 2px;font-family:{FONT};text-align:left;cursor:pointer"><span style="display:flex;justify-content:space-between;{SILK};color:{{{{ m.fromInk }}}}">{{{{ m.from }}}}<span style="color:#C08A2E">{{{{ m.at }}}}</span></span><span style="font-size:22px;line-height:1;color:{{{{ m.ink }}}};overflow:hidden;white-space:nowrap;text-overflow:ellipsis">{{{{ m.preview }}}}</span></button></sc-for>
  </div>
  <div style="flex:0 0 auto;border:1px dashed #5A3D0E;min-height:48px;display:flex;align-items:center;justify-content:center;font-size:22px;line-height:1;color:#C08A2E">REPLY UNAVAILABLE</div>''',
        D, "MESSAGES. One row per conversation, so the house is one row. The header counts messages (D-105).", gap=8)

    o += screen("sRevealThread", f'''
  {hdr("HOUSE", D, back="goReveal", back_label="Back to messages", meta="4 MESSAGES")}
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;gap:12px;overflow:hidden">
    <sc-for list="{{{{ thread }}}}" as="t" hint-placeholder-count="3"><div style="display:flex;flex-direction:column;gap:6px;border:1px solid #5A3D0E;padding:12px 14px;margin-right:28px"><span style="{SILK};color:#C08A2E">{{{{ t.at }}}}</span><span style="font-size:24px;line-height:1.1;color:#E39A22">{{{{ t.text }}}}</span></div></sc-for>
  </div>
  <div style="flex:0 0 auto;border:1px dashed #5A3D0E;min-height:48px;display:flex;align-items:center;justify-content:center;font-size:22px;line-height:1;color:#C08A2E">REPLY UNAVAILABLE</div>''',
        D, "THE HOUSE THREAD. Only the newest message differs by role.")

    o += "<!-- ============ THE SPRINGBOARD. Identical for both roles. ============ -->\n\n"

    # PAGE 1, shared by the banners and the Egress widget, which sit over it.
    o += f'''<!-- PAGE 1. A shift screen held up as a lamp: one inverted block (SCAN), everything else outline.
     The two heavy banners dim it (D-118). The quiet banner does not. Swipe left for page 2. -->
<sc-if value="{{{{ sPage1 }}}}" hint-placeholder-val="{{{{ true }}}}">
<div onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ p1Up }}}}" style="flex:1;min-height:0;display:flex;flex-direction:column;padding:18px 20px 16px;gap:14px;font-family:{FONT};color:#FFC759;opacity:{{{{ p1Op }}}};touch-action:pan-y">
  <sc-if value="{{{{ notEgress }}}}" hint-placeholder-val="{{{{ true }}}}">
  <div style="position:relative;border:1px solid #8A5C12;padding:22px 14px 14px;display:flex;flex-direction:column;gap:10px">
    <span style="position:absolute;left:10px;top:-7px;background:#000;padding:0 6px;{SILK};line-height:14px;color:#E39A22">SYSTEM INTEGRITY</span>
    <span style="font-size:76px;line-height:.8">87%</span>
    {meter("intSegs")}
    <div style="font-size:22px;line-height:1;color:#C08A2E">Frozen until next house meeting</div>
  </div>
  </sc-if>
  <sc-if value="{{{{ sEgressWidget }}}}" hint-placeholder-val="{{{{ false }}}}">
  <div style="position:relative;border:2px solid #FFC759;padding:22px 14px 14px;display:flex;flex-direction:column;gap:10px">
    <span style="position:absolute;left:10px;top:-8px;background:#000;padding:0 6px;{SILK};line-height:14px;color:#FFC759">EGRESS . BEACON</span>
    <span style="font-size:76px;line-height:.8">1:42</span>
    {meter("egCells")}
    <div style="display:flex;justify-content:space-between;font-size:22px;line-height:1"><span style="color:#C08A2E">CONTAIN AT</span><span>UTILITY . LANDING</span></div>
  </div>
  </sc-if>
  <div style="display:flex;gap:8px">
<sc-if value="{{{{ notCarrying }}}}" hint-placeholder-val="{{{{ true }}}}">
    <button type="button" onClick="{{{{ goWork }}}}" style="flex:1;min-width:0;box-sizing:border-box;border:2px solid #FFC759;background:#000;color:#FFC759;padding:12px 14px;display:flex;flex-direction:column;gap:7px;text-align:left;font-family:{FONT};cursor:pointer">
      <span style="{SILK};color:#E39A22">NEXT SUBROUTINE</span>
      <span style="font-size:40px;line-height:.8">HANDSHAKE</span>
      <span style="display:flex;align-items:center;gap:8px;font-size:22px;line-height:1;color:#E39A22">GARAGE<svg role="img" aria-label="triangle marker" viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M8 3l5 10H3z"></path></svg><span style="flex:1"></span>{lightmark("nextLight")}</span>
    </button>
    </sc-if>
    <sc-if value="{{{{ sCarrying }}}}" hint-placeholder-val="{{{{ false }}}}">
    <button type="button" onClick="{{{{ goFiles }}}}" style="flex:1;min-width:0;box-sizing:border-box;border:2px solid #FFC759;background:#000;color:#FFC759;padding:12px 14px;display:flex;flex-direction:column;gap:7px;text-align:left;font-family:{FONT};cursor:pointer">
      <span style="{SILK};color:#E39A22">CARRYING</span>
      <span style="font-size:40px;line-height:.8">DISK 01</span>
      <span style="font-size:22px;line-height:1;color:#E39A22">TO RACK</span>
      <span style="font-size:18px;line-height:1.05;color:#C08A2E">NOTHING ELSE OPENS UNTIL IT IS DELIVERED</span>
    </button>
    </sc-if>
    <button type="button" onClick="{{{{ goWork }}}}" style="flex:0 0 122px;box-sizing:border-box;border:1px solid #8A5C12;background:#000;color:#FFC759;padding:12px 10px;display:flex;flex-direction:column;align-items:flex-start;gap:7px;text-align:left;font-family:{FONT};cursor:pointer">
      <span style="{SILK};color:#C08A2E">COMPLETED</span>
      <span style="font-size:40px;line-height:.8">3</span>
      <span style="font-size:18px;line-height:1;color:#C08A2E">OF 7 ASSIGNED</span>
    </button>
  </div>
  <div style="display:grid;grid-template-columns:repeat(4, minmax(0, 1fr));gap:6px 4px;margin-top:4px">
    <sc-for list="{{{{ apps }}}}" as="a" hint-placeholder-count="8">
      <sc-if value="{{{{ a.blank }}}}" hint-placeholder-val="{{{{ false }}}}"><div aria-hidden="true"></div></sc-if>
      <sc-if value="{{{{ a.real }}}}" hint-placeholder-val="{{{{ true }}}}"><button type="button" onClick="{{{{ a.go }}}}" disabled="{{{{ a.off }}}}" style="border:none;background:none;padding:4px 0 6px;display:flex;flex-direction:column;align-items:center;gap:7px;color:{{{{ a.ink }}}};font:400 20px/1 {FONT};cursor:{{{{ a.cursor }}}}"><span style="box-sizing:border-box;width:56px;height:56px;border:1px {{{{ a.edgeStyle }}}} #8A5C12;background:{{{{ a.fill }}}};display:flex;align-items:center;justify-content:center"><svg aria-hidden="true" viewBox="0 0 28 28" width="28" height="28" fill="none" stroke="{{{{ a.glyph }}}}" strokeWidth="2.5" strokeLinejoin="miter" strokeLinecap="square"><path d="{{{{ a.d }}}}"></path></svg></span><span>{{{{ a.name }}}}</span></button></sc-if>
    </sc-for>
  </div>
  {pagebar(1, "goPage2", "Page 1 of 2. Show page 2.")}
  {dock()}
</div>
</sc-if>

<!-- The quiet one is another player, number withheld: the dependency is never named, and the text
     reads the same whether they finished or were revoked (gdd.md:276-282). BANNERS over page 1. Swipe up dismisses to Home. The two heavy ones clear themselves after 10s (D-119). -->
<sc-if value="{{{{ sNotify }}}}" hint-placeholder-val="{{{{ false }}}}">
<button type="button" onClick="{{{{ goReveal }}}}" onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ bannerUp }}}}" style="position:absolute;left:20px;right:20px;top:12px;z-index:2;border:none;background:#FFC759;color:#000;padding:14px 16px 16px;display:flex;flex-direction:column;gap:8px;align-items:stretch;text-align:left;font-family:{FONT};touch-action:none;cursor:pointer">
  <span style="display:flex;justify-content:space-between;{SILK}">HOUSE<span>NOW</span></span>
  <span style="font-size:28px;line-height:1.05">Regarding this evening. Please read.</span>
</button>
</sc-if>
<sc-if value="{{{{ sBanner }}}}" hint-placeholder-val="{{{{ false }}}}">
<button type="button" onClick="{{{{ goEgressWidget }}}}" onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ bannerUp }}}}" style="position:absolute;left:20px;right:20px;top:12px;z-index:2;border:none;background:#FFC759;color:#000;padding:14px 16px 16px;display:flex;flex-direction:column;gap:8px;align-items:stretch;text-align:left;font-family:{FONT};touch-action:none;cursor:pointer">
  <span style="display:flex;justify-content:space-between;{SILK}">HOUSE<span>NOW</span></span>
  <span style="font-size:32px;line-height:.95">EGRESS ATTEMPT IN PROGRESS</span>
  <span style="font-size:22px;line-height:1">CONTAIN AT UTILITY AND LANDING</span>
</button>
</sc-if>
<sc-if value="{{{{ sQuiet }}}}" hint-placeholder-val="{{{{ false }}}}">
<button type="button" onClick="{{{{ goWork }}}}" onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ bannerUp }}}}" style="position:absolute;left:20px;right:20px;top:12px;z-index:2;min-height:156px;justify-content:flex-start;border:1px solid #8A5C12;background:#000;color:#FFC759;padding:14px 16px 16px;display:flex;flex-direction:column;gap:8px;align-items:stretch;text-align:left;font-family:{FONT};touch-action:none;cursor:pointer">
  <span style="display:flex;justify-content:space-between;{SILK};color:#E39A22">NUMBER WITHHELD<span style="color:#C08A2E">21:03</span></span>
  <span style="font-size:26px;line-height:1.05">you're up</span>
</button>
</sc-if>

'''

    # PAGE 2: everything is a two-second hold (D-141). A Resident's tiles take no input at all (D-142).
    def tile(name_hole, sub_hole, key, border, ink, subink, fill, bar_w, bar_ink):
        return (f'<button type="button" onPointerDown="{{{{ holds.{key}.down }}}}" onPointerUp="{{{{ holds.{key}.up }}}}" onPointerLeave="{{{{ holds.{key}.up }}}}" '
                f'style="position:relative;overflow:hidden;box-sizing:border-box;border:2px solid {border};background:{fill};color:{ink};'
                f'display:flex;flex-direction:column;align-items:flex-start;gap:10px;padding:16px;text-align:left;font-family:{FONT};'
                f'touch-action:none;user-select:none;cursor:{{{{ p2Cursor }}}}">'
                f'<span style="font-size:52px;line-height:.8;letter-spacing:.04em">{{{{ {name_hole} }}}}</span>'
                f'<span style="font-size:22px;line-height:1;color:{subink}">{{{{ {sub_hole} }}}}</span>'
                f'<span aria-hidden="true" style="align-self:stretch;display:flex;height:8px;box-sizing:border-box;border:1px solid #5A3D0E;margin-top:4px">'
                f'<span style="width:{bar_w};background:{bar_ink}"></span></span>'
                f'<span aria-hidden="true" style="position:absolute;left:0;bottom:0;height:3px;width:{{{{ holds.{key}.w }}}};transition:{{{{ holds.{key}.tr }}}};background:#FFC759"></span></button>')

    def t2(name_hole, key):
        return (f'<button type="button" onPointerDown="{{{{ holds.{key}.down }}}}" onPointerUp="{{{{ holds.{key}.up }}}}" onPointerLeave="{{{{ holds.{key}.up }}}}" '
                f'style="position:relative;overflow:hidden;box-sizing:border-box;height:56px;border:1px solid #8A5C12;background:none;color:#E39A22;'
                f'font:400 24px/1 {FONT};letter-spacing:.04em;touch-action:none;user-select:none;cursor:{{{{ p2Cursor }}}}">{{{{ {name_hole} }}}}'
                f'<span aria-hidden="true" style="position:absolute;left:0;bottom:0;height:3px;width:{{{{ holds.{key}.w }}}};transition:{{{{ holds.{key}.tr }}}};background:#FFC759"></span></button>')

    o += f'''<!-- PAGE 2. Same shape and brightness for both roles. Every tile is a two-second hold (D-141).
     A Resident's tiles take no input at all, not even a press (D-142). Swipe right for page 1. -->
<sc-if value="{{{{ sPage2 }}}}" hint-placeholder-val="{{{{ true }}}}">
<div onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ p2Up }}}}" style="flex:1;min-height:0;display:flex;flex-direction:column;padding:18px 20px 16px;gap:14px;font-family:{FONT};color:#FFC759;touch-action:pan-y">
  {tile("t1aName", "t1aSub", "revoke", "{{ revokeBorder }}", "{{ revokeInk }}", "{{ revokeSubInk }}", "{{ revokeFill }}", "{{ revokeBar }}", "{{ revokeBarInk }}")}
  {tile("t1bName", "t1bSub", "egress", "#8A5C12", "#FFC759", "#C08A2E", "#000", "64%", "#8A5C12")}
  <div style="display:flex;flex-direction:column;gap:10px;margin-top:6px">
    <div style="display:grid;grid-template-columns:repeat(3, minmax(0, 1fr));gap:8px">{t2("t2a", "surge")}{t2("t2b", "static")}{t2("t2c", "isolate")}</div>
    <div aria-hidden="true" style="display:flex;height:8px;box-sizing:border-box;border:1px solid #5A3D0E"><div style="width:82%;background:#E39A22"></div></div>
    <div style="font-size:22px;line-height:1.1;color:#C08A2E">{{{{ t2Note2 }}}}</div>
  </div>
  {pagebar(2, "goHome", "Page 2 of 2. Show page 1.")}
  {dock()}
</div>
</sc-if>

'''

    # TARGET PICKERS (Insider only). A tap fires and returns to page 2 with no feedback.
    def picker(key, title, note, rows):
        return screen(key, f'''
  {hdr(title, D, back="goPage2", back_label="Back to page 2", meta=note)}
  {rules(rows, D)}''', D, f"{title} TARGETS. A tap fires and returns to page 2, with no feedback.")

    o += picker("sSurgeTargets", "SURGE", "SELECT A RESIDENT",
                f'<sc-for list="{{{{ surgeRows }}}}" as="r" hint-placeholder-count="5">{navrow("{{ r }}", "goPage2", D, "", 28)}</sc-for>')
    o += picker("sIsolateTargets", "ISOLATE", "SELECT A MARKER",
                f'<sc-for list="{{{{ isolateRows }}}}" as="r" hint-placeholder-count="4"><button type="button" onClick="{{{{ goPage2 }}}}" style="display:flex;align-items:center;gap:14px;min-height:56px;border:none;border-bottom:1px solid #5A3D0E;background:none;padding:0 2px;color:#FFC759;font:400 28px/1 {FONT};text-align:left;cursor:pointer">{shape("r.d", "r.fill", 22)}<span style="flex:1">{{{{ r.room }}}}</span></button></sc-for>')

    # LANTERN and the opening text arriving on it
    def lantern(key, comment, ground, extra_card="", sender="#5A3D0E"):
        return f'''<!-- {comment} -->
<sc-if value="{{{{ {key} }}}}" hint-placeholder-val="{{{{ true }}}}">
<div style="position:absolute;inset:0;background:{ground};color:#000;display:flex;flex-direction:column;padding:36px 20px 20px;font-family:{FONT}">
  <div style="flex:0 0 auto;height:34px;box-sizing:border-box;display:flex;align-items:center;gap:8px;padding-bottom:4px;border-bottom:1px solid #000;{SILK};letter-spacing:.04em">
    <div aria-hidden="true" style="flex:0 0 auto;display:flex;align-items:flex-end;gap:2px;height:12px"><div style="width:3px;height:5px;background:#5A3D0E"></div><div style="width:3px;height:8px;background:#5A3D0E"></div><div style="width:3px;height:12px;background:#5A3D0E"></div></div>
    <span style="flex:1;min-width:0">SOMEONE'S HOME</span>
    <span style="flex:0 0 auto;display:flex;align-items:center;gap:4px;border:1px solid #000;padding:3px 4px"><span aria-hidden="true" style="box-sizing:border-box;width:9px;height:9px;border:1px solid #000;border-radius:50%;display:flex;align-items:center;justify-content:center"><span style="width:3px;height:3px;background:#000"></span></span>ARMED</span>
  </div>
  <div style="text-align:center;font-size:124px;line-height:.8;margin-top:52px">21:04</div>
  <div style="text-align:center;font-size:26px;line-height:1;letter-spacing:.06em;margin-top:12px">SATURDAY 16 AUGUST</div>
  <div style="margin-top:32px;display:flex;flex-direction:column;gap:10px">
    {extra_card}
    <sc-for list="{{{{ lockCards }}}}" as="c" hint-placeholder-count="2"><div onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ c.up }}}}" style="border:2px solid #000;padding:12px 14px;display:flex;flex-direction:column;gap:8px;touch-action:pan-y"><div style="{SILK};color:{sender}">{{{{ c.from }}}}</div><div style="font-size:26px;line-height:1.05">{{{{ c.text }}}}</div></div></sc-for>
  </div>
  <button type="button" onClick="{{{{ goHome }}}}" style="margin-top:auto;box-sizing:border-box;height:64px;border:2px solid #000;background:none;color:#000;font:400 30px/1 {FONT};letter-spacing:.06em;cursor:pointer">SLIDE TO OPEN &gt;&gt;</button>
</div>
</sc-if>

'''

    o += lantern("sLock", "LANTERN. Locked IS the lamp: amber to every edge, at the level the house sets. Swipe a card left to dismiss it.", "#FFC759")
    o += lantern("sLockNotify", "LANTERN, THE TEXT ARRIVES. The ground drops to the dim step while it is up (D-118), then clears to the lantern after 10s.", "#C08A2E",
                 f'<div style="background:#000;color:#FFC759;padding:12px 14px;display:flex;flex-direction:column;gap:8px"><div style="{SILK};color:#E39A22">HOUSE . NOW</div><div style="font-size:26px;line-height:1.05">Regarding this evening. Please read.</div></div>', "#000")
    return o
