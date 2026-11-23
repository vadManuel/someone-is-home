from kit import *

B = BONE


def build():
    o = "\n<!-- ============ COLD START. Bone field: the house lights are still on. ============ -->\n\n"

    # BOOT: falls through on its own, no control
    o += screen("sBoot", f'''
  <div style="font-size:48px;line-height:.9;padding-top:16px">SOMEONE'S HOME</div>
  <div style="display:flex;flex-direction:column;gap:12px;font-size:24px;line-height:1;margin-top:8px">
    <sc-for list="{{{{ bootRows }}}}" as="b" hint-placeholder-count="5"><div style="display:flex;align-items:baseline;gap:8px"><span style="color:#332E24">{{{{ b.k }}}}</span><span aria-hidden="true" style="flex:1;border-bottom:2px dotted #837B68;transform:translateY(-4px)"></span><span>{{{{ b.v }}}}</span></div></sc-for>
  </div>
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:10px">
    {cap("UPTIME 4331D 02:14", B)}
    {meter("bootCells", 10)}
    {label("STARTING", B, "text-align:center")}
  </div>''', B, "BOOT. Self-test, then falls through to Permissions on its own.", pad="16px 20px 24px", gap=16)

    # PERMS (D-161): real OS state per row, each row asks for its own permission
    o += screen("sPerms", f'''
  {hdr("PERMISSIONS", B)}
  {body("Grant these now, while the lights are on.", B, 24)}
  {rules(f"""<sc-for list="{{{{ permRows }}}}" as="r" hint-placeholder-count="4"><button type="button" style="display:flex;align-items:center;gap:12px;min-height:56px;border:none;border-bottom:1px solid #837B68;background:none;padding:0 2px;color:#14110B;font:400 24px/1 {FONT};text-align:left;cursor:pointer"><span style="flex:1">{{{{ r.name }}}}</span><span style="{SILK};padding:5px 6px;border:1px solid {{{{ r.tagEdge }}}};background:{{{{ r.tagFill }}}};color:{{{{ r.tagInk }}}}">{{{{ r.state }}}}</span></button></sc-for>""", B)}
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:10px">
    {body("THIS PHONE CANNOT TAKE A SEAT YET<br>MOTION &amp; FITNESS", B, 22)}
    {primary("REQUEST WHAT IS MISSING", None, B, 28)}
  </div>''', B, "PERMISSIONS (D-161). Every row is tappable and asks for that one permission.")

    # JOIN
    o += screen("sJoin", f'''
  {hdr("JOIN A HOME", B, back="goPerms", back_label="Back to permissions")}
  <label style="display:flex;flex-direction:column;gap:6px">
    <span style="{SILK};color:#332E24">RESIDENT</span>
    <input type="text" defaultValue="ELLIOT" placeholder="YOUR NAME" maxLength="14" style="box-sizing:border-box;width:100%;height:60px;border:none;border-bottom:2px solid #14110B;background:none;padding:0 2px;color:#14110B;font:400 40px/1 {FONT};letter-spacing:.06em;text-transform:uppercase;outline:none">
  </label>
  {label("NETWORKS NEARBY", B, "margin-top:8px")}
  {rules(f"""<sc-for list="{{{{ joinNets }}}}" as="n" hint-placeholder-count="3"><button type="button" onClick="{{{{ goLobby }}}}" style="display:flex;align-items:center;gap:12px;min-height:60px;border:none;border-bottom:1px solid #837B68;background:none;padding:0 2px;color:#14110B;font:400 26px/1 {FONT};text-align:left;cursor:pointer"><span style="flex:1">{{{{ n.name }}}}</span><span style="font-size:20px;color:#332E24">{{{{ n.ip }}}}</span></button></sc-for>""", B)}
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:12px">
    {secondary("HOST A HOME INSTEAD", "goMaps", B)}
  </div>''', B, "JOIN. A row reaches the Lobby only if the attach succeeds.")

    o += "<!-- ============ HOST SETUP. Bone field, once per house, in the light. ============ -->\n\n"

    o += screen("sMaps", f'''
  {hdr("SAVED HOMES", B)}
  {rules(f"""<sc-for list="{{{{ savedHomes }}}}" as="h" hint-placeholder-count="3"><button type="button" onClick="{{{{ goHouse }}}}" style="display:flex;align-items:center;gap:12px;min-height:72px;border:none;border-bottom:1px solid #837B68;background:none;padding:0 2px;color:#14110B;font-family:{FONT};text-align:left;cursor:pointer"><span style="flex:1;display:flex;flex-direction:column;gap:5px"><span style="font-size:28px;line-height:1">{{{{ h.name }}}}</span><span style="font-size:22px;line-height:1;color:#332E24">{{{{ h.meta }}}}</span></span>{CHEV_R}</button></sc-for>""", B)}
  {primary("MAP A NEW HOME", "goEditor", B, extra="margin-top:auto")}''', B, "SAVED HOMES. Newest first.")

    # EDITOR
    o += screen("sEditor", f'''
  {hdr("THE BUNGALOW", B, back="goMaps", back_label="Back to saved homes")}
  {cap("6 ROOMS . 5 MARKERS", B, size=20, extra="margin-top:-12px")}
  <div style="flex:0 0 auto;display:flex;gap:6px">
    <button type="button" aria-pressed="true" style="flex:1;box-sizing:border-box;height:56px;border:1px solid #14110B;background:#14110B;color:#A29A86;font:400 24px/1 {FONT};letter-spacing:.06em;cursor:pointer">GROUND</button>
    <button type="button" aria-pressed="false" style="flex:1;box-sizing:border-box;height:56px;border:1px solid #14110B;background:none;color:#14110B;font:400 24px/1 {FONT};letter-spacing:.06em;cursor:pointer">UPPER</button>
    <button type="button" onClick="{{{{ goFloors }}}}" aria-label="Floors" style="flex:0 0 56px;box-sizing:border-box;height:56px;border:1px solid #14110B;background:none;color:#14110B;display:flex;align-items:center;justify-content:center;cursor:pointer">{PLUS}</button>
  </div>
  <button type="button" onClick="{{{{ goRoomEdit }}}}" aria-label="Plan of the ground floor. Tap a room to edit it." style="flex:1;min-height:0;position:relative;border:1px solid #837B68;background:none;padding:0;cursor:pointer;font-family:{FONT}">{plan("editCells", "editRooms")}</button>
  {cap("Drag two corners to add a room. Tap a room to edit it.", B, size=20)}
  <button type="button" onClick="{{{{ goReview }}}}" style="flex:0 0 auto;box-sizing:border-box;height:64px;border:1px solid {{{{ saveEdge }}}};background:{{{{ saveFill }}}};color:{{{{ saveInk }}}};font:400 24px/1 {FONT};letter-spacing:.02em;cursor:pointer">{{{{ saveLabel }}}}</button>''',
        B, "PLAN. T is the terminal, U is the meeting card (D-121, D-127).", gap=10)

    # REVIEW NEEDS (D-126, D-127): every missing thing at once
    o += screen("sReviewNeeds", f'''
  {hdr("THIS HOME NEEDS 2 MORE THINGS", B, back="goEditor", back_label="Back to the plan", tsize=28)}
  <div style="display:flex;gap:12px;align-items:flex-start">{token("T", B, 40)}<div style="display:flex;flex-direction:column;gap:6px">{body("SCAN THE CARD MARKED T", B, 26)}{body("Scan it in a room. That room becomes the terminal.", B, 22)}</div></div>
  <div style="display:flex;gap:12px;align-items:flex-start;border-top:1px solid #837B68;padding-top:12px">{token("U", B, 40)}<div style="display:flex;flex-direction:column;gap:6px">{body("SCAN THE CARD MARKED U", B, 26)}{body("Scan it in a room. Meetings are called there.", B, 22)}</div></div>
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:10px">
    {cap("HOSTS UP TO 6", B, center=True, size=20)}
    {primary("OPEN A ROOM", "goEditor", B)}
  </div>''', B, "REVIEW NEEDS. Replaces 'No terminal yet'. Terminal, meeting card and eight markers, all listed at once.")

    # ROOM PANEL
    o += f'''<!-- ROOM PANEL. Rooms are renamed from the five presets only: the name is a readout. -->
<sc-if value="{{{{ sRoomEdit }}}}" hint-placeholder-val="{{{{ true }}}}">
<div style="flex:1;min-height:0;display:flex;flex-direction:column;padding:12px 20px 0;gap:8px;font-family:{FONT};color:#14110B">
  {hdr("GARAGE", B, meta="ROOM PANEL")}
  <div style="flex:1;min-height:0;position:relative;border:1px solid #837B68">{plan("heldCells", "heldRooms", label_size=17)}</div>
</div>
<div style="flex:0 0 auto;position:relative;z-index:1;background:#A29A86;border-top:2px solid #14110B;padding:14px 20px 20px;display:flex;flex-direction:column;gap:10px;font-family:{FONT};color:#14110B">
  <div role="group" aria-label="Room name" style="display:flex;flex-wrap:wrap;gap:6px">
    <sc-for list="{{{{ nameChips }}}}" as="c" hint-placeholder-count="5"><button type="button" aria-pressed="{{{{ c.on }}}}" style="box-sizing:border-box;height:40px;padding:0 10px;border:1px solid {{{{ c.edge }}}};background:{{{{ c.fill }}}};color:{{{{ c.ink }}}};font:400 22px/1 {FONT};cursor:pointer">{{{{ c.name }}}}</button></sc-for>
  </div>
  <div style="display:flex;align-items:center;gap:10px;border-bottom:2px solid #14110B;height:46px"><span style="{SILK};color:#332E24">ROOM NAME</span><span style="font-size:30px;line-height:1;letter-spacing:.06em">GARAGE</span><span aria-hidden="true" style="width:12px;height:24px;background:#14110B"></span></div>
  <div role="group" aria-label="Room type" style="display:flex;gap:6px">
    <button type="button" onClick="{{{{ pickRoom }}}}" aria-pressed="{{{{ typeIsRoom }}}}" style="flex:1;box-sizing:border-box;height:56px;border:1px solid #14110B;background:{{{{ tRoomFill }}}};color:{{{{ tRoomInk }}}};font:400 24px/1 {FONT};letter-spacing:.06em;cursor:pointer">ROOM</button>
    <button type="button" onClick="{{{{ pickStairs }}}}" aria-pressed="{{{{ typeNotRoom }}}}" style="flex:1;box-sizing:border-box;height:56px;border:1px solid #14110B;background:{{{{ tStairsFill }}}};color:{{{{ tStairsInk }}}};font:400 24px/1 {FONT};letter-spacing:.06em;cursor:pointer">STAIRS</button>
  </div>
  <sc-if value="{{{{ typeIsRoom }}}}" hint-placeholder-val="{{{{ true }}}}">
  <button type="button" onClick="{{{{ goMarkerSheet }}}}" style="display:flex;align-items:center;gap:10px;height:56px;border:none;border-top:1px solid #837B68;border-bottom:1px solid #837B68;background:none;padding:0 2px;color:#14110B;font:400 24px/1 {FONT};cursor:pointer"><span style="flex:1;text-align:left">MARKERS</span><sc-for list="{{{{ roomMarkers }}}}" as="k" hint-placeholder-count="2">{shape("k.d", "k.fill", 20, label_hole="k.name")}</sc-for>{CHEV_R}</button>
  </sc-if>
  <sc-if value="{{{{ typeNotRoom }}}}" hint-placeholder-val="{{{{ false }}}}">
  <div style="display:flex;flex-direction:column;gap:6px;border-top:1px solid #837B68;border-bottom:1px solid #837B68;padding:10px 2px"><div style="display:flex;justify-content:space-between;font-size:24px;line-height:1;color:#332E24">MARKERS<span>NONE HERE</span></div>{cap("Nothing can be registered in stairs.", B, size=20)}</div>
  </sc-if>
  {primary("DONE", "goEditor", B, 28, 56)}
  {quiet("DELETE ROOM AND ITS 2 MARKERS", "goEditor", B, extra="margin-top:-6px;text-decoration:underline;text-underline-offset:5px")}
</div>
</sc-if>

'''

    # STAIRS WARN: hold to unregister (D-141). GARAGE holds no T or U, so no token is drawn.
    o += screen("sStairsWarn", f'''
  {hdr("GARAGE BECOMES STAIRS", B, back="goRoomEdit", back_label="Back to the room")}
  {label("THESE WOULD BE UNREGISTERED", B)}
  <div style="display:flex;gap:8px"><sc-for list="{{{{ roomMarkers }}}}" as="k" hint-placeholder-count="2"><span style="box-sizing:border-box;width:56px;height:56px;border:1px solid #14110B;display:flex;align-items:center;justify-content:center">{shape("k.d", "k.fill", 28, "#14110B", "k.name")}</span></sc-for></div>
  {body("These cards will belong to no room. Scan them again in a real room, or their subroutines are gone.", B, 24)}
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:8px">
    {primary("MOVE THEM FIRST", "goMarkerSheet", B)}
    {hold("UNREGISTER AND PROCEED", "unreg", B, h=56, size=24)}
  </div>''', B, "STAIRS WARNING. Unregistering is a two-second hold (D-141) and returns to the plan.", gap=14)

    # MARKERS IN A ROOM
    o += f'''<!-- MARKERS IN A ROOM. Two reserved rows: the terminal and the meeting card. -->
<sc-if value="{{{{ sMarkerSheet }}}}" hint-placeholder-val="{{{{ true }}}}">
<div style="flex:1;min-height:0;display:flex;flex-direction:column;padding:12px 20px 0;gap:8px;font-family:{FONT};color:#14110B">
  {hdr("MARKERS", B, meta="GARAGE . 2")}
  <div style="flex:1;min-height:0;position:relative;border:1px solid #837B68">{plan("heldCells", "heldRooms", label_size=17)}</div>
</div>
<div style="flex:0 0 auto;position:relative;z-index:1;background:#A29A86;border-top:2px solid #14110B;padding:14px 20px 20px;display:flex;flex-direction:column;gap:8px;font-family:{FONT};color:#14110B">
  <div style="display:flex;flex-wrap:wrap;gap:8px"><sc-for list="{{{{ roomMarkers }}}}" as="k" hint-placeholder-count="2"><button type="button" aria-label="Remove {{{{ k.name }}}}" style="box-sizing:border-box;height:52px;border:1px solid #14110B;background:none;padding:0 12px;display:flex;align-items:center;gap:10px;color:#14110B;cursor:pointer">{shape("k.d", "k.fill", 24)}{CROSS}</button></sc-for></div>
  {rules(f"""<div style="display:flex;align-items:center;gap:10px;min-height:52px;border-bottom:1px solid #837B68">{token("T", B, 26)}<span style="flex:1;font-size:24px;line-height:1">TERMINAL <span style="color:#332E24">IN HALL</span></span><button type="button" onClick="{{{{ goTermRemove }}}}" aria-label="Remove the terminal" style="width:52px;height:52px;margin-right:-12px;border:none;background:none;color:#14110B;display:flex;align-items:center;justify-content:center;cursor:pointer">{CROSS}</button></div><div style="display:flex;align-items:center;gap:10px;min-height:52px;border-bottom:1px solid #837B68">{token("U", B, 26)}<span style="flex:1;font-size:24px;line-height:1">MEETING CARD <span style="color:#332E24">IN LIVING</span></span><button type="button" onClick="{{{{ goMeetRemove }}}}" aria-label="Remove the meeting card" style="width:52px;height:52px;margin-right:-12px;border:none;background:none;color:#14110B;display:flex;align-items:center;justify-content:center;cursor:pointer">{CROSS}</button></div>""", B)}
  {primary("REGISTER MARKER", "goScanMarker", B, 28, 60)}
  {secondary("DONE", "goRoomEdit", B)}
</div>
</sc-if>

'''

    # REGISTER MARKER and its two refusals are ONE layout: the same header, the same viewfinder at the
    # same height, and a fixed action area under it with the lit button always at the bottom.
    # Only what sits inside the viewfinder and inside the action area changes.
    ACTION_H = 214

    def scan_screen(key, comment, back, back_label, readout, action):
        return screen(key, f'''
  {hdr("REGISTER MARKER", B, back=back, back_label=back_label, meta="GARAGE")}
  <div style="flex:1;min-height:0;position:relative;background:#14110B;overflow:hidden">
    <div style="position:absolute;left:0;right:0;top:14px;text-align:center;font-size:20px;line-height:1;color:#8FA294">BACK CAMERA . POINT AT THE CODE</div>
    <div aria-hidden="true" style="position:absolute;left:7%;top:11%;width:22px;height:22px;border-left:3px solid #8FA294;border-top:3px solid #8FA294"></div>
    <div aria-hidden="true" style="position:absolute;right:7%;top:11%;width:22px;height:22px;border-right:3px solid #8FA294;border-top:3px solid #8FA294"></div>
    <div aria-hidden="true" style="position:absolute;left:7%;bottom:20%;width:22px;height:22px;border-left:3px solid #8FA294;border-bottom:3px solid #8FA294"></div>
    <div aria-hidden="true" style="position:absolute;right:7%;bottom:20%;width:22px;height:22px;border-right:3px solid #8FA294;border-bottom:3px solid #8FA294"></div>
    <div aria-hidden="true" style="position:absolute;inset:0;background:{{{{ torchWash }}}}"></div>
    <div style="position:absolute;left:50%;top:45%;transform:translate(-50%,-50%);width:228px;display:flex;flex-direction:column;align-items:center;gap:10px;text-align:center">{readout}</div>
    <button type="button" onClick="{{{{ toggleTorch }}}}" aria-pressed="{{{{ torchOn }}}}" style="position:absolute;right:10px;bottom:10px;box-sizing:border-box;height:56px;display:flex;align-items:center;gap:8px;border:1px solid #8FA294;background:{{{{ torchFill }}}};padding:0 14px;color:{{{{ torchInk }}}};font:400 22px/1 {FONT};cursor:pointer">{{{{ torchLabel }}}}</button>
  </div>
  <div style="flex:0 0 {ACTION_H}px;height:{ACTION_H}px;overflow:hidden;display:flex;flex-direction:column;justify-content:flex-end;gap:10px">{action}</div>''', B, comment)

    o += scan_screen("sScanMarker", "REGISTER MARKER", "goMarkerSheet", "Back to markers",
        f'''<svg aria-hidden="true" viewBox="0 0 16 16" width="56" height="56" fill="none" stroke="#8FA294" strokeWidth="1.3" strokeLinejoin="miter"><path d="{{{{ lastShapeD }}}}" fill="{{{{ lastShapeFill }}}}"></path></svg>
      <div style="font-size:26px;line-height:1;color:#8FA294">GARAGE . ADDED</div>''',
        f'''<div style="display:flex;align-items:center;gap:10px;min-height:28px"><span style="{SILK};color:#332E24">THIS ROOM</span><sc-for list="{{{{ roomMarkers }}}}" as="k" hint-placeholder-count="2">{shape("k.d", "k.fill", 22, "#14110B", "k.name")}</sc-for></div>
  {cap("Keep scanning to add more.", B)}
  {primary("DONE SCANNING", "goMarkerSheet", B)}''')

    def taken(key, comment, tok, what, room, second, move_label, note):
        return scan_screen(key, comment, "goScanMarker", "Back to scanning",
            f'''{token(tok, B, 44, "#C9A227")}
      <div style="font-size:30px;line-height:1;color:#C9A227">ALREADY REGISTERED</div>
      <div style="font-size:22px;line-height:1.12;color:#8FA294">This home has one {what} and it is in <span style="color:#C6BEAC">{room}</span>. {second}</div>''',
            f'''{cap(note, B, size=20)}
  {secondary(move_label, "goScanMarker", B, 22)}
  {primary("KEEP IT IN " + room, "goScanMarker", B)}''')

    o += taken("sTermTaken", "TERMINAL ALREADY TAKEN. Same layout as REGISTER MARKER, different words.", "T", "terminal", "HALL",
               "A second one would give the house two places to be found.", "MOVE THE TERMINAL TO GARAGE",
               "Moving it leaves HALL with no terminal.")
    o += taken("sMeetTaken", "MEETING CARD ALREADY TAKEN (D-121). Same layout as REGISTER MARKER, different words.", "U", "meeting card", "LIVING",
               "A second one would give the house two places to be called to.", "MOVE THE MEETING CARD TO GARAGE",
               "Moving it leaves LIVING with nowhere to meet.")

    # REMOVE THE TERMINAL and REMOVE THE MEETING CARD are ONE layout: a one-line title at the same
    # size, the token row, the explanation, and a fixed action area. Only the words change.
    def remove(key, comment, title, tok, room, text):
        return screen(key, f'''
  {hdr(title, B, back="goMarkerSheet", back_label="Back to markers", tsize=28)}
  <div style="flex:0 0 auto;display:flex;align-items:center;gap:14px;padding:6px 0">{token(tok, B, 64)}<span style="font-size:36px;line-height:1">IN {room}</span></div>
  <div style="flex:1;min-height:0">{body(text, B, 24)}</div>
  <div style="flex:0 0 130px;height:130px;display:flex;flex-direction:column;justify-content:flex-end;gap:10px">
    {secondary("REMOVE IT", "goMarkerSheet", B)}
    {primary("KEEP IT", "goMarkerSheet", B)}
  </div>''', B, comment, gap=16)

    o += remove("sTermRemove", "REMOVE THE TERMINAL", "REMOVE THE TERMINAL", "T", "HALL",
                "The T card will belong to no room. This home cannot be saved until a room has one.")
    o += remove("sMeetRemove", "REMOVE THE MEETING CARD", "REMOVE THE MEETING CARD", "U", "LIVING",
                "The meeting card will belong to no room. This home cannot be saved until a room has one.")

    # FLOORS
    o += screen("sFloors", f'''
  {hdr("FLOORS", B, back="goEditor", back_label="Back to the plan", meta="2 IN THIS HOME")}
  <div style="display:flex;flex-direction:column;gap:8px">
    <button type="button" onClick="{{{{ goEditor }}}}" style="display:flex;align-items:center;gap:12px;min-height:60px;border:1px solid #837B68;background:none;padding:0 12px;color:#14110B;font:400 28px/1 {FONT};cursor:pointer"><span style="flex:1;text-align:left">GROUND</span><span style="font-size:22px;color:#332E24">6 ROOMS . 5 MARKERS</span></button>
    <button type="button" onClick="{{{{ goEditor }}}}" aria-current="true" style="display:flex;align-items:center;gap:12px;min-height:60px;border:2px solid #14110B;background:none;padding:0 11px;color:#14110B;font:400 28px/1 {FONT};cursor:pointer"><span style="flex:1;text-align:left">UPPER</span><span style="font-size:22px">5 ROOMS . 4 MARKERS</span></button>
  </div>
  <button type="button" onClick="{{{{ goEditor }}}}" style="box-sizing:border-box;height:56px;border:1px solid #14110B;background:none;color:#14110B;display:flex;align-items:center;justify-content:center;gap:10px;font:400 26px/1 {FONT};cursor:pointer">{PLUS}ADD A FLOOR</button>
  <div style="margin-top:auto">{rules(navrow("RENAME UPPER", None, B) + navrow("DELETE UPPER", None, B), B)}</div>
  {cap("Deleting UPPER removes 5 rooms and 4 markers. Hold two seconds.", B)}''', B, "FLOORS. The open floor is outlined.")

    # SAVE HOME (also the rename screen)
    o += screen("sSaveName", f'''
  {hdr("SAVE HOME", B, back="goEditor", back_label="Back to the plan")}
  <label style="display:flex;flex-direction:column;gap:6px"><span style="{SILK};color:#332E24">NAME THIS HOME</span><input type="text" defaultValue="THE BUNGALOW" style="box-sizing:border-box;width:100%;height:60px;border:none;border-bottom:2px solid #14110B;background:none;padding:0 2px;color:#14110B;font:400 40px/1 {FONT};letter-spacing:.04em;text-transform:uppercase;outline:none"></label>
  {rules(info("FLOORS", "2", B) + info("ROOMS", "11", B) + info("MARKERS", "9", B) + info("HOSTS UP TO", "6", B), B)}
  {primary("SAVE HOME", "goHouse", B, extra="margin-top:auto")}''', B, "SAVE HOME. Also the rename screen: saving under a new name moves the home.", gap=14)

    # HOME DETAIL
    o += screen("sHome_detail", f'''
  {hdr("SAVED HOME", B, back="goMaps", back_label="Back to saved homes", tsize=24)}
  <div style="display:flex;flex-direction:column;gap:8px">
    <div style="font-size:48px;line-height:.9">THE BUNGALOW</div>
    {cap("2 floors . 11 rooms . 9 markers<br>About fifteen minutes of walking<br>Hosts up to 6", B)}
  </div>
  <div style="flex:1;min-height:0;display:flex;gap:10px">
    <div style="flex:1;display:flex;flex-direction:column;gap:6px">{label("GROUND", B)}<div style="flex:1;position:relative;border:1px solid #837B68">{plan("miniGround", "noRooms")}</div></div>
    <div style="flex:1;display:flex;flex-direction:column;gap:6px">{label("UPPER", B)}<div style="flex:1;position:relative;border:1px solid #837B68">{plan("miniUpper", "noRooms")}</div></div>
  </div>
  {primary("HOST WITH THIS HOME", "goLobby", B)}
  {rules(navrow("EDIT THE PLAN", "goEditor", B) + navrow("RENAME", "goSaveName", B), B)}
  {quiet("DELETE THIS HOME", "goDelete", B, extra="text-decoration:underline;text-underline-offset:5px")}''', B, "HOME DETAIL. Hosting goes to the Lobby only once the home is on the air.", gap=12)

    # DELETE: hold
    o += screen("sDelete", f'''
  {hdr("DELETE HOME", B, back="goHouse", back_label="Back to the home")}
  <div style="display:flex;flex-direction:column;gap:8px">
    <div style="font-size:40px;line-height:.95">THE BUNGALOW</div>
    {body("2 floors, 11 rooms and 9 markers. About fifteen minutes of walking this home.", B, 24)}
  </div>
  {cap("This cannot be undone and nothing is backed up off this device.", B)}
  <div style="flex:1;min-height:0;display:flex;gap:10px">
    <div style="flex:1;position:relative;border:1px solid #837B68">{plan("miniGround", "noRooms")}</div>
    <div style="flex:1;position:relative;border:1px solid #837B68">{plan("miniUpper", "noRooms")}</div>
  </div>
  {hold("HOLD TO DELETE", "del", B)}
  {primary("KEEP THIS HOME", "goHouse", B)}''', B, "DELETE HOME. A two-second hold (D-141).", gap=12)

    # LOBBY: host's view
    o += screen("sLobby", f'''
  {hdr("THE BUNGALOW", B, meta="6 JOINED")}
  <div style="display:flex;flex-wrap:wrap;gap:6px">
    <span style="padding:3px 7px;border:2px solid #14110B;font-size:20px;line-height:1">ELLIOT</span>
    <sc-for list="{{{{ lobbyNames }}}}" as="n" hint-placeholder-count="5"><span style="padding:4px 8px;border:1px solid #837B68;font-size:20px;line-height:1">{{{{ n }}}}</span></sc-for>
  </div>
  {rules(f"""<sc-for list="{{{{ lobbyRows }}}}" as="r" hint-placeholder-count="7"><div style="display:flex;align-items:center;min-height:32px;border-bottom:1px solid #837B68;font-size:20px;line-height:1"><span style="flex:1;color:#332E24">{{{{ r.k }}}}</span><span style="font-size:22px">{{{{ r.v }}}}</span></div></sc-for>""", B)}
  <button type="button" onClick="{{{{ goSecret }}}}" style="display:flex;align-items:center;gap:10px;min-height:52px;border:1px solid #14110B;background:none;padding:0 12px;color:#14110B;font:400 26px/1 {FONT};text-align:left;cursor:pointer"><span style="flex:1">YOUR ONE LINE</span><span style="{SILK};padding:5px 6px;background:#14110B;color:#A29A86">REQUIRED</span></button>
  {cap("4 of 6 handed theirs over", B, center=True)}
  <div style="margin-top:auto;display:flex;flex-direction:column;gap:6px">
    <button type="button" disabled="{{{{ true }}}}" style="flex:0 0 auto;box-sizing:border-box;height:56px;border:1px dashed #332E24;background:none;color:#332E24;font:400 30px/1 {FONT};letter-spacing:.1em">LIGHTS OUT</button>
    {cap("WAITING FOR EVERYONE'S LINE", B, center=True, size=20)}
    {hold("CLOSE THE HOUSE", "close", B, h=44, size=22, note=False)}
    {cap("Every phone goes back to the start.", B, center=True, size=20)}
  </div>''', B, "LOBBY, the host's phone. LIGHTS OUT is a two-second hold, inert until every line is in.", gap=8)

    # SECRET
    o += screen("sSecret", f'''
  {hdr("BEFORE THE LIGHTS GO OUT", B, back="goLobby", back_label="Back to the lobby", tsize=28)}
  {body("SOMETHING YOU WOULD RATHER NOT EXPLAIN", B, 30, extra="line-height:1")}
  {cap("One line, and make it real. A thing you did, owe, broke, or never told them. If the house picks you tonight, this is what it will hold over you.", B)}
  <label style="flex:1;min-height:0;display:flex;flex-direction:column;gap:6px"><span style="{SILK};color:#332E24">YOUR LINE</span><textarea defaultValue="i still have priya's spare key" placeholder="one line" maxLength="120" style="flex:1;min-height:96px;box-sizing:border-box;width:100%;resize:none;border:2px solid #14110B;background:none;padding:10px 12px;color:#14110B;font:400 32px/1.1 {FONT};outline:none"></textarea></label>
  {rules(info("SEEN BY", "THE HOUSE ONLY", B, 44) + info("DELETED", "WHEN THE ROUND ENDS", B, 44), B)}
  {primary("HAND IT OVER", "goLobby", B)}''', B, "YOUR ONE LINE. Still in the light.")

    return o
