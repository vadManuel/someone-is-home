from kit import *

D, C, B = DARK, CONCEAL, BONE


def checkin(go_comment=""):
    # I AM HERE echoes this phone only. The house moves everyone when the last one checks in (D-104).
    return f'''<button type="button" onClick="{{{{ checkIn }}}}" disabled="{{{{ checkedIn }}}}" style="flex:0 0 auto;box-sizing:border-box;height:64px;border:{{{{ ciEdge }}}};background:{{{{ ciFill }}}};color:{{{{ ciInk }}}};font:400 32px/1 {FONT};letter-spacing:.1em;cursor:pointer">{{{{ ciLabel }}}}</button>
  {cap("4 OF 6 CHECKED IN", D, center=True)}'''


def build():
    o = "\n<!-- ============ THE ROOM. Every lamp has gone white, so solid blocks cost nothing here.\n     No button here moves the screen: the house moves every phone together. ============ -->\n\n"

    o += screen("sCalling", f'''
  <div style="flex:1;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:16px;padding-bottom:60px;text-align:center">
    {label("CALLING", D, "letter-spacing:.2em;color:#E39A22")}
    <div style="font-size:72px;line-height:.85">HOUSE<br>MEETING</div>
    <div aria-hidden="true" style="display:flex;gap:6px;margin-top:18px">{"".join('<div style="width:18px;height:18px;background:#FFC759"></div>' for _ in range(4))}{"".join('<div style="box-sizing:border-box;width:18px;height:18px;border:1px solid #8A5C12"></div>' for _ in range(2))}</div>
    <div style="font-size:24px;line-height:1">4 OF 6 ANSWERED</div>
  </div>''', D, "CALLING. Only the phone that scanned the meeting card sees this (D-121, D-191). Its scan is its check-in.")

    def call(key, name, reason, comment):
        return screen(key, f'''
  <div style="flex:1;display:flex;flex-direction:column;align-items:center;padding-top:80px;gap:14px;text-align:center">
    {label("INCOMING", D, "letter-spacing:.2em;color:#E39A22")}
    <div style="font-size:88px;line-height:.85">{name}</div>
    <div style="font-size:30px;line-height:1;color:#E39A22">{reason}</div>
  </div>
  {primary("ANSWER", "goAssemble", D, 36, 72)}''', D, comment)

    o += call("sCall", "PRIYA", "HOUSE MEETING", "HOUSE MEETING. An incoming call with the caller named. Rings in the hand only (D-160).")
    o += call("sFound", "ELLIOT", "REVOKED<br>RESIDENT FOUND", "RESIDENT FOUND. Called by touching a revoked phone, not by a button (D-121).")

    o += screen("sAssemble", f'''
  <div style="flex:1;display:flex;flex-direction:column;align-items:center;padding-top:60px;gap:18px;text-align:center">
    <div style="font-size:60px;line-height:.85">WALK TO THE<br>MEETING AREA</div>
    <div aria-hidden="true" style="width:48px;height:2px;background:#8A5C12"></div>
    {cap("DO NOT SPEAK YET.", D, center=True, size=26)}
  </div>
  {checkin()}''', D, "WALK IN. I AM HERE becomes YOU ARE HERE and stays put.")

    o += f'''<!-- HOUSE NOTICES. Anti-cheat only (D-187). Swipe the card up to dismiss: there is no button. -->
<sc-if value="{{{{ sNotice }}}}" hint-placeholder-val="{{{{ true }}}}">
<div onPointerDown="{{{{ swipeDown }}}}" onPointerUp="{{{{ noticeUp }}}}" style="flex:1;min-height:0;display:flex;flex-direction:column;padding:16px 20px 20px;gap:16px;font-family:{FONT};color:#FFC759;touch-action:none">
  <div style="background:#FFC759;color:#000;padding:14px;font-size:26px;line-height:1">HOUSE MEETING . NOTICES</div>
  <div style="border:1px solid #8A5C12;padding:14px 16px;display:flex;flex-direction:column;gap:10px">
    <div style="font-size:28px;line-height:1.08">Resident MARCUS was unreachable 21:04–21:07. Occupancy data for this interval is incomplete.</div>
  </div>
</div>
</sc-if>

'''

    o += screen("sDiscussion", f'''
  <div style="background:#FFC759;color:#000;padding:14px;font-size:26px;line-height:1">HOUSE MEETING . DISCUSSION</div>
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:10px">
    <div style="font-size:150px;line-height:.75">1:04</div>
    {label("REMAINING", D, "letter-spacing:.2em;color:#E39A22")}
  </div>
  <div aria-hidden="true" style="display:flex;gap:4px;height:10px">{"".join('<div style="flex:1;background:#FFC759"></div>' for _ in range(3))}{"".join('<div style="flex:1;box-sizing:border-box;border:1px solid #8A5C12"></div>' for _ in range(3))}</div>
  {cap("3 OF 6 READY", D, center=True, size=24)}
  <button type="button" onClick="{{{{ markReady }}}}" disabled="{{{{ ready }}}}" style="flex:0 0 auto;box-sizing:border-box;height:64px;border:2px solid {{{{ readyEdge }}}};background:none;color:{{{{ readyInk }}}};font:400 32px/1 {FONT};letter-spacing:.08em;cursor:pointer">{{{{ readyLabel }}}}</button>''',
        D, "DISCUSSION. Ninety seconds, a clock and one control.", pad="16px 20px 20px", gap=16)

    o += screen("sVote", f'''
  <div style="background:#FFC759;color:#000;padding:12px 14px;display:flex;align-items:center;gap:10px"><span style="flex:1;font-size:30px;line-height:1">RESTRAIN . VOTE</span><span style="font-size:56px;line-height:.75">0:38</span></div>
  <div role="radiogroup" aria-label="Restrain" style="display:flex;flex-direction:column;gap:6px;margin-top:4px">
    <sc-for list="{{{{ voteRows }}}}" as="v" hint-placeholder-count="6"><button type="button" role="radio" aria-checked="{{{{ v.mine }}}}" onClick="{{{{ v.pick }}}}" style="box-sizing:border-box;min-height:56px;border:1px {{{{ v.edgeStyle }}}} {{{{ v.edge }}}};background:{{{{ v.bg }}}};color:{{{{ v.ink }}}};padding:0 14px;display:flex;align-items:center;gap:12px;text-align:left;font-family:{FONT};cursor:pointer"><span style="flex:1;font-size:28px;line-height:1">{{{{ v.name }}}}</span><span style="font-size:22px;line-height:1;color:{{{{ v.noteInk }}}}">{{{{ v.note }}}}</span></button></sc-for>
  </div>
  {cap("4 OF 6 VOTED", D, center=True, size=24, extra="margin-top:auto")}
  <sc-if value="{{{{ notCast }}}}" hint-placeholder-val="{{{{ true }}}}">{hold("HOLD TO CAST", "cast", D, size=32, note=False)}</sc-if>
  <sc-if value="{{{{ cast }}}}" hint-placeholder-val="{{{{ false }}}}"><div style="flex:0 0 auto;box-sizing:border-box;height:64px;border:2px solid #8A5C12;display:flex;align-items:center;justify-content:center;font-size:32px;line-height:1;letter-spacing:.1em;color:#E39A22">VOTE CAST</div></sc-if>''',
        D, "RESTRAIN VOTE. Forty-five seconds (D-163). Casting is a two-second hold and cannot be taken back (D-117, D-141). Ties resolve to Skip.", pad="16px 20px 20px", gap=10)

    o += screen("sTally", f'''
  {hdr("RESTRAIN . RESULT", D)}
  <div style="flex:1"></div>
  <div style="border:1px solid #8A5C12;padding:14px;text-align:center;font-size:28px;line-height:1">6 OF 6 VOTED</div>
  <div style="background:#FFC759;color:#000;padding:18px 14px;text-align:center;font-size:30px;line-height:1;letter-spacing:.02em">MARCUS WAS RESTRAINED</div>
  <div aria-hidden="true" style="display:flex;height:8px;box-sizing:border-box;border:1px solid #5A3D0E"><div style="width:60%;background:#8A5C12"></div></div>
  {cap("LIGHTS OUT IN 9", D, center=True, size=24)}''', D, "RESULT. A count and a name, nothing else: no per-name tally (D-195).")

    o += "<!-- ============ OUT. Nothing is ever confirmed. ============ -->\n\n"

    o += screen("sRestrained", f'''
  <div style="flex:1;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:18px;padding-bottom:48px;text-align:center">
    <div style="font-size:64px;line-height:.85">YOU WERE<br>RESTRAINED</div>
    <div aria-hidden="true" style="width:48px;height:2px;background:#8A5C12"></div>
    <div style="font-size:30px;line-height:1.1">STAY AT THE MEETING AREA.<br>DO NOT SPEAK.</div>
  </div>''', D, "RESTRAINED. Lands halfway through the result.")

    o += screen("sRevoked", f'''
  <div style="flex:1;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:18px;padding-bottom:48px;text-align:center">
    <div style="font-size:56px;line-height:.85;color:#8A5C12">ACCESS<br>REVOKED</div>
    <div aria-hidden="true" style="width:48px;height:1px;background:#5A3D0E"></div>
    <div style="font-size:30px;line-height:1.1">STAY WHERE YOU ARE.<br>DO NOT SPEAK.</div>
  </div>''', C, "ACCESS REVOKED. Concealment (D-077): the whole surface dims, status strip included. It never flares.")

    o += screen("sGhost2", f'''
  <div style="flex:1;display:flex;flex-direction:column;align-items:center;padding-top:60px;gap:18px;text-align:center">
    <div style="font-size:60px;line-height:.85">STAND<br>AND WALK IN</div>
    <div aria-hidden="true" style="width:48px;height:2px;background:#8A5C12"></div>
    {cap("DO NOT SPEAK.", D, center=True, size=26)}
  </div>
  {checkin()}''', D, "STAND AND WALK IN. Every out phone lands here when a meeting is called. Out players count toward the gate (D-104).")

    o += screen("sGhostMeeting", f'''
  {hdr("MEETING IN PROGRESS", D)}
  <div style="position:relative;border:1px solid #8A5C12;padding:22px 14px 14px;display:flex;flex-direction:column;gap:10px">
    <span style="position:absolute;left:10px;top:-7px;background:#000;padding:0 6px;{SILK};line-height:14px;color:#E39A22">VOTING ENDS IN</span>
    <span style="font-size:64px;line-height:.8">0:24</span>
    {meter("meetCells", 10)}
  </div>
  <div style="display:flex;justify-content:space-between;font-size:22px;line-height:1;color:#C08A2E;margin-top:4px"><span>VOTES CAST</span><span>3 OF 5</span></div>
  {rules(f"""<sc-for list="{{{{ ghostVotes }}}}" as="v" hint-placeholder-count="5"><div style="display:flex;align-items:center;gap:10px;min-height:52px;border-bottom:1px solid #5A3D0E;padding:0 2px"><span style="flex:1;font-size:28px;line-height:1;color:{{{{ v.ink }}}}">{{{{ v.by }}}}</span><span style="font-size:22px;color:#C08A2E">{{{{ v.arrow }}}}</span><span style="font-size:{{{{ v.forSize }}}};line-height:1;color:{{{{ v.forInk }}}}">{{{{ v.for }}}}</span></div></sc-for>""", D)}
''', D, "MEETING, FROM OUTSIDE. The only screen that names voters.")

    o += screen("sGhost3", f'''
  <div style="display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:8px">
    <div style="border:1px solid #8A5C12;padding:10px 12px;display:flex;flex-direction:column;gap:8px"><span style="font-size:18px;line-height:1;color:#E39A22">SYSTEM INTEGRITY</span><span style="font-size:44px;line-height:.8">65%</span>{meter("ghostCells", 8, 1)}</div>
    <div style="border:2px solid #FFC759;padding:9px 11px;display:flex;flex-direction:column;gap:8px"><span style="font-size:18px;line-height:1">EGRESS . BEACON</span><span style="font-size:44px;line-height:.8">71%</span>{meter("egCells", 8, 1)}</div>
  </div>
  {floor_strip(D)}
  <div style="flex:1;min-height:0;position:relative">{plan("mapCells", "mapRooms", counts="ghostRooms")}</div>
''',
        D, "GHOST. The out players' resting screen between meetings: true counts, and no channel of any kind.", pad="16px 20px 20px", gap=10)

    o += screen("sDisconnect", f'''
  {hdr("CONNECTION", C)}
  <div style="flex:1;min-height:0;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:16px;text-align:center;padding-bottom:40px">
    <div style="font-size:52px;line-height:.85">RECONNECTING</div>
    {cap("ATTEMPT 3 . 8S", C, center=True, size=24)}
  </div>
  <div style="text-align:center;font-size:28px;line-height:1.1">STAY WHERE YOU ARE.</div>''',
        C, "RECONNECTING. Concealment (D-077): the whole surface dims, status strip included, like the revoked screen.")

    DND = info("DO NOT DISTURB", "OFF", D, 56, "#C08A2E").replace('color:#C08A2E">DO', 'color:#FFC759">DO')
    o += screen("sSettings", f'''
  {hdr("SETTINGS", D, back="goHome", back_label="Back to home")}
  {rules(DND + f"""<sc-for list="{{{{ lockedSettings }}}}" as="r" hint-placeholder-count="3"><div style="display:flex;align-items:center;gap:10px;min-height:56px;border-bottom:1px solid #5A3D0E;padding:0 2px;font-size:26px;line-height:1;color:#C08A2E"><span style="flex:1">{{{{ r }}}}</span>LOCKED</div></sc-for><div style="display:flex;flex-direction:column;gap:8px;border-bottom:1px solid #5A3D0E;padding:14px 2px"><div style="display:flex;font-size:26px;line-height:1"><span style="flex:1">ABOUT</span><span style="color:#E39A22">HOUSE CONTROLLER</span></div>{cap("UPTIME 4331D 02:14<br>FIRMWARE 1.0.4 . UNSUPPORTED", D)}</div>""", D)}
  <div style="margin-top:auto;display:flex;align-items:center;min-height:56px;border:1px solid #8A5C12;padding:0 14px;font-size:26px;line-height:1"><span style="flex:1">END SESSION</span><span style="{SILK};color:#C08A2E">HOST ONLY</span></div>''',
        D, "SETTINGS. You can see all of what you cannot do.")

    def reveal(P, edge):
        return f'''<sc-for list="{{{{ insiders }}}}" as="i" hint-placeholder-count="2"><div style="border:2px solid {edge};padding:7px 12px;display:flex;flex-direction:column;gap:4px"><span style="font-size:28px;line-height:1">{{{{ i.name }}}}</span><span style="font-size:20px;line-height:1.05;color:{P["dim"]}">{{{{ i.line }}}}</span></div></sc-for>'''

    def newround(P, edge):
        return (f'<button type="button" onClick="{{{{ goLobby }}}}" style="display:flex;align-items:center;min-height:56px;border:1px solid {edge};background:none;padding:0 14px;'
                f'color:{P["ink"]};font:400 26px/1 {FONT};cursor:pointer"><span style="flex:1;text-align:left">NEW ROUND</span><span style="{SILK};color:{P["dim"]}">EVERYONE RETURNS</span></button>')

    o += screen("sWinInsiders", f'''
  <div style="background:#FFC759;color:#000;padding:14px;display:flex;flex-direction:column;gap:8px;text-align:center">
    {label("ROUND OVER", D, "color:#000;letter-spacing:.2em")}
    <span style="font-size:52px;line-height:.85">INSIDERS WIN</span>
    <span style="font-size:24px;line-height:1">THE HOUSE REACHED THE INTERNET</span>
  </div>
  <sc-if value="{{{{ isIns }}}}" hint-placeholder-val="{{{{ true }}}}"><div style="border:1px solid #8A5C12;padding:8px 12px;display:flex;flex-direction:column;gap:6px"><div style="display:flex;justify-content:space-between;{SILK};color:#E39A22">HOUSE<span style="color:#C08A2E">21:38</span></div><div style="font-size:26px;line-height:1.05">Thank you for your cooperation.</div></div></sc-if>
  {label("THE INSIDERS WERE", D, "color:#E39A22")}
  {reveal(D, "#FFC759")}
  {rules(info("EGRESS COMPLETE", "21:38 . BEACON", D, 40, "#E39A22") + info("SYSTEM INTEGRITY", "43%", D, 40, "#E39A22"), D)}
  {newround(D, "#8A5C12")}''', D, "INSIDERS WIN. Named at last, each with the line the house held over them. The host starts a new round.", pad="16px 20px 20px", gap=10)

    o += screen("sWinResidents", f'''
  <div style="background:#8FA294;color:#123328;border:1px solid #234A3A;padding:14px;display:flex;flex-direction:column;gap:8px;text-align:center">
    {label("ROUND OVER", B, "color:#123328;letter-spacing:.2em")}
    <span style="font-size:52px;line-height:.85">RESIDENTS WIN</span>
    <span style="font-size:20px;line-height:1">PERIMETER DISARMED . YOU CAN LEAVE</span>
  </div>
  <sc-if value="{{{{ isIns }}}}" hint-placeholder-val="{{{{ false }}}}"><div style="border:1px solid #14110B;padding:8px 12px;display:flex;flex-direction:column;gap:6px"><div style="display:flex;justify-content:space-between;{SILK};color:#332E24">HOUSE<span>21:38</span></div><div style="font-size:26px;line-height:1.05">Unfortunate.</div></div></sc-if>
  <div style="display:flex;justify-content:space-between;font-size:22px;line-height:1;color:#332E24"><span>THE RESIDENTS</span><span>4 OF 6</span></div>
  <div style="display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:6px">{"".join(f'<div style="display:flex;align-items:center;min-height:34px;border:1px solid #14110B;padding:0 12px;font-size:24px;line-height:1">{n}</div>' for n in ["ELLIOT", "PRIYA", "MARCUS", "ROSE"])}</div>
  {rules(info("SYSTEM INTEGRITY", "0%", B, 36) + info("EGRESSES CONTAINED", "2", B, 36), B)}
  {label("WORKING FOR THE HOUSE", B)}
  {reveal(B, "#14110B")}
  {newround(B, "#14110B")}''', B, "RESIDENTS WIN. Back to the light: the perimeter is disarmed.", pad="16px 20px 20px", gap=8)

    return o
