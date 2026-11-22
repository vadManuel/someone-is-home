class Component extends DCLogic {
  state = { torch: false, rtype: "room", checkedIn: false, ready: false, pick: "MARCUS", cast: false, holding: null, dismissed: [] };

  componentDidUpdate(prev) {
    if (prev.screen !== this.props.screen) {
      clearTimeout(this._ht);
      this.setState({ checkedIn: false, ready: false, pick: "MARCUS", cast: false, holding: null, dismissed: [] });
    }
  }
  componentWillUnmount() { clearTimeout(this._ht); }

  renderVals() {
    const p = this.props || {};
    const st = this.state;
    // Older screen ids still reach their renamed screens.
    const ALIAS = { sub: "subhandshake", subbright: "subparity", noterminal: "reviewneeds", passagewarn: "stairswarn", termno: "termlive" };
    const s = ALIAS[p.screen] || p.screen || "home";
    const ins = p.role === "insider";
    const nav = (id) => () => { if (p.nav) p.nav(id); };
    const A = "#FFC759", MID = "#E39A22", DIM = "#C08A2E", LINE = "#8A5C12", FAINT = "#5A3D0E", DEEP = "#150E04";
    const HATCH = "repeating-linear-gradient(45deg,#150E04 0 6px,#000 6px 12px)";
    const cells = (n, on, ink, off) => Array.from({ length: n }, (_, i) => i < on ? { c: ink, e: ink } : { c: "none", e: off || FAINT });

    // The light mark: one, two or three cells for DARK, MEDIUM, BRIGHT (D-106).
    const LV = { dark: 1, medium: 2, bright: 3 };
    const mark = (level, ink) => {
      const n = LV[level];
      return { lightLabel: level.charAt(0).toUpperCase() + level.slice(1) + " light", lInk: ink,
        l1: ink, l2: n >= 2 ? ink : "none", l3: n >= 3 ? ink : "none" };
    };

    // A two-second hold (D-141). The bar under the control fills while the finger stays down.
    const hold = (key, fn, enabled) => {
      const on = st.holding === key;
      return {
        down: () => { if (enabled === false) return; clearTimeout(this._ht); this.setState({ holding: key });
          this._ht = setTimeout(() => { this.setState({ holding: null }); fn(); }, 2000); },
        up: () => { if (this.state.holding === key) { clearTimeout(this._ht); this.setState({ holding: null }); } },
        w: on ? "100%" : "0%", tr: on ? "width 2s linear" : "none",
        note: on ? "KEEP HOLDING" : "HOLD THE BUTTON FOR TWO SECONDS."
      };
    };
    const noop = () => {};

    // Swipes: left and right between springboard pages, up to dismiss a banner or a notice,
    // left to dismiss a card on the lantern.
    const swipeDown = (e) => { this._sx = e.clientX; this._sy = e.clientY; };
    const swipe = (e) => {
      const dx = e.clientX - (this._sx == null ? e.clientX : this._sx);
      const dy = e.clientY - (this._sy == null ? e.clientY : this._sy);
      this._sx = this._sy = null;
      return { dx, dy };
    };

    // ---- the house plan, one source for every plan on every screen ----
    const ROOMS = [
      { n: "KITCHEN", r0: 0, r1: 2, c0: 0, c1: 3 }, { n: "LIVING", r0: 0, r1: 2, c0: 5, c1: 9 },
      { n: "HALL", r0: 4, r1: 5, c0: 0, c1: 5 }, { n: "STAIRS", r0: 4, r1: 5, c0: 6, c1: 9, transit: true },
      { n: "STUDY", r0: 7, r1: 10, c0: 0, c1: 3 }, { n: "GARAGE", r0: 7, r1: 9, c0: 5, c1: 9 }
    ];
    const UPPER = [
      { n: "BED 1", r0: 0, r1: 3, c0: 0, c1: 4 }, { n: "BED 2", r0: 0, r1: 3, c0: 6, c1: 9 },
      { n: "LANDING", r0: 5, r1: 6, c0: 0, c1: 9, transit: true },
      { n: "BATH 1", r0: 8, r1: 11, c0: 0, c1: 3 }, { n: "UTILITY", r0: 8, r1: 11, c0: 5, c1: 9 }
    ];
    const CELL = (v, n) => (v / n * 100).toFixed(2) + "%";
    const at = (rooms) => (r, c) => rooms.find((x) => r >= x.r0 && r <= x.r1 && c >= x.c0 && c <= x.c1) || null;
    const roomAt = at(ROOMS);
    const SLATE_HATCH = "repeating-linear-gradient(45deg,#B3AA96 0 3px,#234A3A 3px 4px)";
    const boneCells = (rooms, focus) => {
      const f = at(rooms), out = [];
      for (let i = 0; i < 120; i++) {
        const r = Math.floor(i / 10), c = i % 10, room = f(r, c), held = focus && room && room.n === focus;
        const b = room ? "0.5px solid " + (held ? "#243029" : "#234A3A") : "0.5px solid #8C8470";
        out.push({ t: b, r: b, b, l: b, f: !room ? "none" : held ? "#3E4C44" : room.transit ? SLATE_HATCH : "#8FA294" });
      }
      return out;
    };
    const TOKENS = { KITCHEN: ["1"], LIVING: ["1", "U"], HALL: ["T"], STUDY: ["1"], GARAGE: ["2"] };
    const boneRooms = (focus, withChips) => ROOMS.map((x) => ({
      name: x.n, lx: CELL(x.c0, 10), ly: CELL(x.r0, 12),
      bg: x.n === focus ? "#3E4C44" : x.transit ? "#B3AA96" : "#8FA294",
      ink: x.n === focus ? "#C6BEAC" : "#123328",
      chips: withChips ? (TOKENS[x.n] || []).map((t) => ({ t, radius: t === "T" || t === "U" ? "50%" : "0" })) : []
    }));

    // Live maps: room edges in the line step, your own room outlined in full ink, stairs hatched.
    const HERE = "GARAGE";
    const mapCells = [];
    for (let r = 0; r < 12; r++) for (let c = 0; c < 10; c++) {
      const room = roomAt(r, c), mine = room && room.n === HERE;
      const w = (o) => (o !== room && room ? (mine ? "2px solid #FFC759" : "1px solid #8A5C12") : "0");
      mapCells.push({ t: w(roomAt(r - 1, c)), r: w(roomAt(r, c + 1)), b: w(roomAt(r + 1, c)), l: w(roomAt(r, c - 1)),
        f: !room ? "none" : room.transit ? "repeating-linear-gradient(45deg,#5A3D0E 0 1px,#000 1px 6px)" : mine ? DEEP : "none" });
    }
    const mapRooms = ROOMS.map((x) => ({ name: x.n, lx: CELL(x.c0, 10), ly: CELL(x.r0, 12), bg: "transparent",
      ink: x.n === HERE ? A : DIM, chips: [], cx: CELL((x.c0 + x.c1 + 1) / 2, 10), cy: CELL((x.r0 + x.r1 + 1) / 2, 12) }));
    const counted = (spec) => mapRooms.filter((x) => x.name in spec).map((x) => Object.assign({}, x, { count: spec[x.name][0], ink: spec[x.name][1] }));

    // A marker is a printed code carrying one of 44 shapes. Players navigate by the shape.
    const DISC = "M8 3a5 5 0 100 10 5 5 0 100-10";
    const SHAPES = { square: ["M3 3h10v10H3z", 1], squareframe: ["M3.5 3.5h9v9h-9z", 0], ring: [DISC, 0], triangle: ["M8 3l5 10H3z", 1],
      diamond: ["M8 3l5 5-5 5-5-5z", 1], cross: ["M8 3v10M3 8h10", 0], arrowright: ["M13 8l-5-4v3H3v2h5v3z", 1], hexagon: ["M5.4 3.4h5.2L13 8l-2.4 4.6H5.4L3 8z", 1] };
    const mk = (n, label) => ({ name: label || n, d: SHAPES[n][0], fill: SHAPES[n][1] ? "currentColor" : "none" });
    const roomMarkers = [mk("triangle"), mk("ring")];

    // ---- status strip (D-078): on every screen. The lantern and the scan draw their own. ----
    const PRE = ["boot", "perms", "join", "secret", "maps", "editor", "savename", "homedetail", "delete", "lobby", "roomedit", "markersheet",
      "stairswarn", "scanmarker", "termtaken", "termremove", "meettaken", "meetremove", "floors", "reviewneeds", "winresidents"];
    const OWNBAR = ["lock", "locknotify", "scan"];
    const OUT = ["revoked", "restrained", "ghost2", "ghostmeeting", "ghost3"];
    // Concealment (D-077) dims the whole surface: the revoked screen and the dark Subroutines.
    // D-077 names the reconnecting screen too, so it dims with them.
    const concealed = ["revoked", "disconnect"].indexOf(s) >= 0 || ["subhandshake", "subsniff", "subshort"].indexOf(s) >= 0;
    const mode = OWNBAR.indexOf(s) >= 0 ? null : PRE.indexOf(s) >= 0 ? "pre" : OUT.indexOf(s) >= 0 ? "ghost" : s === "wininsiders" ? "end" : "live";

    const BACKLOG = [
      ["MUM", "20:12", "did you get there ok? text me when you"], ["DELIVERY", "20:19", "Your parcel could not be left with a neigh"],
      ["FLAT CHAT", "20:31", "Tomas: running late, start without me"], ["VOICEMAIL", "20:44", "Missed call. No message left."],
      ["BANK", "20:47", "A payment of 12.40 was authorised on your"], ["UNKNOWN", "20:52", "sorry wrong number"], ["PRIYA", "20:55", "im outside, which door"]
    ];
    // The count and mix of leftovers are randomised per phone, so inbox density never implies a role.
    const seed = p.inboxSeed === undefined ? 3 : p.inboxSeed;
    // One row per conversation. The house is one sender, so it is one row showing its newest text,
    // and its thread holds all four. Identical for both roles (only the thread's newest line differs).
    const HOUSE_MESSAGES = 4;
    const inbox = [
      { from: "HOUSE", at: "21:02", preview: "Regarding this evening. Please read.", fromInk: A, ink: A }
    ].concat(BACKLOG.slice(seed % 3, (seed % 3) + 3 + (seed % 4)).map(([from, at, preview]) => ({ from, at, preview, fromInk: DIM, ink: MID })));

    const rs = p.revokeState || "ready";
    const hasTerminal = p.hasTerminal === undefined ? true : !!p.hasTerminal;
    const rtype = st.rtype;
    const setType = (t) => () => { if (t !== "room") { nav("stairswarn")(); return; } this.setState({ rtype: t }); };

    // Parity: a checkerboard with one block breaking it. The ring is the player's choice.
    const parityCells = Array.from({ length: 36 }, (_, i) => {
      const r = Math.floor(i / 6), c = i % 6, odd = (r + c) % 2 === 1, breaks = i === 21;
      const lit = breaks ? !odd : odd;
      return { f: lit ? A : DEEP, b: i === 14 ? "3px solid #FFC759" : "1px solid " + (lit ? A : FAINT) };
    });

    const WORK = [
      ["REPLAY", "HALL", "diamond", "bright", "done"], ["SHORT", "GARAGE", "arrowright", "dark", "done"], ["JAM", "BED 2", "cross", "medium", "done"],
      ["HANDSHAKE", "GARAGE", "triangle", "dark", "next"], ["ARRAY WIPE", "STUDY", "squareframe", "bright", "open"],
      ["LOCKED", "WAITING UPSTREAM", null, null, "locked"], ["LOCKED", "WAITING UPSTREAM", null, null, "locked"]
    ];
    const workRows = WORK.map(([name, where, sh, level, state]) => {
      const k = sh ? mk(sh) : { d: "", fill: "none", name: "" };
      const ink = state === "next" ? "#000" : state === "open" ? A : DIM;
      return Object.assign({ name, where, d: k.d, fill: k.fill, shapeName: sh ? sh + " marker" : "", hasShape: !!sh,
        isNext: state === "next", isRow: state !== "next",
        edgeStyle: state === "locked" ? "dashed" : "solid", edge: state === "done" ? FAINT : LINE,
        bg: state === "locked" ? HATCH : "#000", ink, deco: state === "done" ? "line-through" : "none",
        boxEdge: state === "open" ? A : state === "done" ? DIM : state === "locked" ? FAINT : LINE,
        boxFill: state === "done" ? DIM : "none", whereInk: state === "open" ? MID : DIM }, level ? mark(level, state === "next" ? "#000" : state === "open" ? MID : DIM) : {});
    });

    const APPS = [
      ["FILES", "files", "M4 8h8l2 3h10v12H4z"],
      ["MESSAGES", "reveal", "M4 6h20v13H12l-5 4v-4H4z"], null, null,
      ["SETTINGS", "settings", "M4 9h3 M13 9h11 M4 19h11 M21 19h3 M7 6h6v6H7z M15 16h6v6h-6z"],
      ["CAMERA", null, "M4 9h20v14H4z M10 9l2-3h4l2 3 M11 13h6v6h-6z"], null, null
    ];
    // The PHONE tile is gone (D-121): meetings are called only by scanning the meeting card.
    // The TERMINAL tile is gone (D-199): the map opens only by scanning the T card.
    // The NOTES app is gone (D-315): MESSAGES moves up one slot.
    // Their slots stay empty, so the grid keeps its shape for both roles.
    const apps = APPS.map((a) => {
      if (!a) return { blank: true, real: false };
      const off = !a[1];
      return { blank: false, real: true, name: a[0], d: a[2], off, go: off ? undefined : nav(a[1]),
        ink: off ? DIM : MID, edgeStyle: off ? "dashed" : "solid", fill: off ? HATCH : "#000", glyph: off ? LINE : A, cursor: off ? "default" : "pointer" };
    });

    const votes = ["PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS", "SKIP"];
    const voteRows = votes.map((name) => {
      const mine = st.pick === name, skip = name === "SKIP";
      return { name, mine, note: mine ? "YOUR VOTE" : skip ? "RESTRAIN NOBODY" : "",
        pick: () => { if (!this.state.cast) this.setState({ pick: name }); },
        bg: mine ? A : "#000", ink: mine ? "#000" : A, edge: mine ? A : LINE, edgeStyle: skip ? "dashed" : "solid", noteInk: mine ? "#000" : DIM };
    });

    const lockCards = [["NUMBER WITHHELD . 21:03", "you're up"], ["HOUSE . 21:01", "Do not attempt the exterior doors."]]
      .filter((c) => st.dismissed.indexOf(c[0]) < 0)
      .map(([from, text]) => ({ from, text, up: (e) => { if (swipe(e).dx < -48) this.setState((x) => ({ dismissed: x.dismissed.concat([from]) })); } }));

    const t2Ins = ins;
    const holds = {
      unreg: hold("unreg", nav("editor")), del: hold("del", nav("maps")), close: hold("close", nav("join")),
      cast: hold("cast", () => this.setState({ cast: true })),
      // A Resident's page 2 takes no input at all (D-142): the hold never starts.
      revoke: hold("revoke", () => { if (p.onRevokeStep) p.onRevokeStep(); }, ins),
      egress: hold("egress", nav("banner"), ins),
      surge: hold("surge", nav("surgetargets"), ins), static: hold("static", noop, ins), isolate: hold("isolate", nav("isolatetargets"), ins)
    };

    const sP1 = ["home", "notify", "banner", "quiet", "egresswidget", "carrying"].indexOf(s) >= 0;

    return {
      sBoot: s === "boot", sPerms: s === "perms", sJoin: s === "join", sMaps: s === "maps", sEditor: s === "editor",
      sReviewNeeds: s === "reviewneeds", sRoomEdit: s === "roomedit", sStairsWarn: s === "stairswarn", sMarkerSheet: s === "markersheet",
      sScanMarker: s === "scanmarker", sTermTaken: s === "termtaken", sMeetTaken: s === "meettaken", sTermRemove: s === "termremove",
      sMeetRemove: s === "meetremove", sFloors: s === "floors", sSaveName: s === "savename", sHome_detail: s === "homedetail",
      sDelete: s === "delete", sLobby: s === "lobby", sSecret: s === "secret",
      sArmed: s === "armed", sReveal: s === "reveal", sRevealThread: s === "revealthread",
      sPage1: sP1, sNotify: s === "notify", sBanner: s === "banner", sQuiet: s === "quiet", sEgressWidget: s === "egresswidget",
      notEgress: s !== "egresswidget", sCarrying: s === "carrying", notCarrying: s !== "carrying", sPage2: s === "page2", sSurgeTargets: s === "surgetargets", sIsolateTargets: s === "isolatetargets",
      sLock: s === "lock", sLockNotify: s === "locknotify",
      sWork: s === "work", sScan: s === "scan", sScanCaught: s === "scancaught", sScanBad: s === "scanbad", sScanUnknown: s === "scanunknown",
      sFiles: s === "files", sTermBusy: s === "termbusy", sTermLive: s === "termlive", sTermStatic: s === "termstatic",
      sTimelapse: s === "timelapse",
      sSubReplay: s === "subreplay", sSubInterrupt: s === "subinterrupt", sSubParity: s === "subparity", sSubSniff: s === "subsniff",
      sSubDeallocate: s === "subdeallocate", sSubDrift: s === "subdrift", sSubShort: s === "subshort", sSubTrace: s === "subtrace",
      sSubJam: s === "subjam", sSubHandshake: s === "subhandshake",
      sCalling: s === "calling", sCall: s === "call", sFound: s === "found", sAssemble: s === "assemble", sNotice: s === "notice",
      sDiscussion: s === "discussion", sVote: s === "vote", sTally: s === "tally",
      sRestrained: s === "restrained", sRevoked: s === "revoked", sGhost2: s === "ghost2", sGhostMeeting: s === "ghostmeeting",
      sGhost3: s === "ghost3", sDisconnect: s === "disconnect", sSettings: s === "settings",
      sWinInsiders: s === "wininsiders", sWinResidents: s === "winresidents",

      isPre: mode === "pre", isIns: ins,
      buzzes: ["armed", "notify", "locknotify", "banner", "call", "found", "assemble", "notice", "tally", "restrained", "revoked",
        "ghostmeeting", "wininsiders", "winresidents"].indexOf(s) >= 0,
      sbShow: mode !== null,
      sbInk: mode === "pre" ? "#14110B" : concealed ? DIM : A,
      sbDim: mode === "pre" ? "#332E24" : DIM,
      sbEdge: mode === "pre" ? "#837B68" : FAINT,
      // The house owns the network once the round starts: no bars on any live screen.
      sbSig: mode === "pre" ? "#14110B" : FAINT,
      sbSigOff: mode === "pre" && s !== "winresidents" ? "#837B68" : mode === "pre" ? "#14110B" : FAINT,
      // The carrier is blank in play. Out of the round it names the cause.
      sbCarrier: s === "restrained" ? "RESTRAINED" : mode === "ghost" ? "REVOKED" : "",
      sbArmedIcon: mode === "live" || mode === "ghost" || mode === "end",
      sbDisarmedIcon: s === "winresidents",
      sbLock: s === "files" || s === "carrying",
      sbTime: s === "winresidents" || mode === "end" ? "21:38" : mode === "pre" ? "20:41" : mode === "ghost" ? "21:31" : "21:07",

      // ---- data ----
      bootRows: [["MEM 640K", "OK"], ["REGISTRY", "0 ENTRIES"], ["PERIMETER", "IDLE"], ["LAMP ALLOCATION", "GRANTED"], ["UPLINK", "CONNECTED"]].map(([k, v]) => ({ k, v })),
      bootCells: cells(20, 14, "#14110B", "#837B68"),
      permRows: [["LOCAL NETWORK", "GRANTED"], ["CAMERA", "GRANTED"], ["BLUETOOTH", "GRANTED"], ["MOTION & FITNESS", "TAP TO GRANT"], ["NOTIFICATIONS", "TAP TO GRANT"]]
        .map(([name, state]) => { const ok = state === "GRANTED";
          return { name, state, tagEdge: ok ? "#332E24" : "#14110B", tagFill: ok ? "none" : "#14110B", tagInk: ok ? "#14110B" : "#A29A86" }; }),
      joinNets: [["THE BUNGALOW", "192.168.1.24"], ["MUM & DAD'S", "192.168.1.31"], ["FLAT 6", "192.168.1.40"]].map(([name, ip]) => ({ name, ip })),
      savedHomes: [["THE BUNGALOW", "2 FLOORS . 11 ROOMS"], ["MUM & DAD'S", "2 FLOORS . 9 ROOMS"], ["THE LAKE PLACE", "1 FLOOR . 6 ROOMS"]].map(([name, meta]) => ({ name, meta })),
      editCells: boneCells(ROOMS), editRooms: boneRooms(null, true),
      heldCells: boneCells(ROOMS, "GARAGE"), heldRooms: boneRooms("GARAGE", true),
      miniGround: boneCells(ROOMS), miniUpper: boneCells(UPPER), noRooms: [],
      nameChips: ["KITCHEN", "LIVING", "GARAGE", "BATH 1", "BED 1"].map((name) => { const on = name === "GARAGE";
        return { name, on, edge: on ? "#14110B" : "#837B68", fill: on ? "#14110B" : "none", ink: on ? "#A29A86" : "#332E24" }; }),
      // D-127: a home needs a terminal, a meeting card and eight markers before it can be hosted.
      saveLabel: hasTerminal ? "REVIEW HOME" : "REVIEW HOME . 2 THINGS MISSING",
      saveEdge: hasTerminal ? "#234A3A" : "#14110B", saveFill: hasTerminal ? "#8FA294" : "none", saveInk: hasTerminal ? "#123328" : "#14110B",
      goReview: hasTerminal ? nav("savename") : nav("reviewneeds"),
      typeIsRoom: rtype === "room", typeNotRoom: rtype !== "room",
      pickRoom: setType("room"), pickStairs: setType("stairs"),
      tRoomFill: rtype === "room" ? "#14110B" : "none", tRoomInk: rtype === "room" ? "#A29A86" : "#14110B",
      tStairsFill: rtype !== "room" ? "#14110B" : "none", tStairsInk: rtype !== "room" ? "#A29A86" : "#14110B",
      roomMarkers,
      lastShapeD: DISC, lastShapeFill: "none",
      toggleTorch: () => this.setState((x) => ({ torch: !x.torch })), torchOn: st.torch,
      torchLabel: st.torch ? "TORCH ON" : "TORCH OFF", torchFill: st.torch ? "#8FA294" : "none", torchInk: st.torch ? "#14110B" : "#8FA294",
      torchWash: st.torch ? "rgba(143,162,148,.16)" : "none",
      lobbyNames: ["PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS"],
      lobbyRows: [["INSIDERS", "UNKNOWN"], ["INSIDERS KNOW EACH OTHER", "ON"], ["SUBROUTINES EACH", "7"], ["DISCUSSION", "90S"], ["VOTING", "45S"], ["EGRESS TIMER", "120S"], ["REVOKE COOLDOWN", "60S"]].map(([k, v]) => ({ k, v })),

      inbox, inboxCount: inbox.length - 1 + HOUSE_MESSAGES,
      // The inbox shows two house rows, so the thread holds both of them.
      thread: [["20:58", "Occupancy nominal. Access reviewed and retained."], ["20:59", "Seven subroutines assigned. Begin at your convenience."],
        ["21:01", "Do not attempt the exterior doors."],
        ["21:02", ins ? "You still have Priya's spare key. Follow my instructions tonight and nobody else needs to hear about it." : "I am watching you. Thank you for your cooperation."]]
        .map(([at, text]) => ({ at, text })),

      intSegs: cells(32, 28, MID), egCells: cells(32, 22, MID), ghostCells: cells(32, 21, MID), meetCells: cells(30, 16, A),
      scanCells: cells(20, 12, "#000", LINE),
      p1Op: s === "notify" || s === "banner" ? 0.42 : 1,
      nextLight: mark("dark", MID), keyDark: mark("dark", DIM), keyMedium: mark("medium", DIM), keyBright: mark("bright", DIM),
      light_dark: mark("dark", DIM), light_medium: mark("medium", MID), light_bright: mark("bright", A),
      apps, workRows,
      swipeDown,
      p1Up: (e) => { if (swipe(e).dx < -48) nav("page2")(); },
      p2Up: (e) => { if (swipe(e).dx > 48) nav("home")(); },
      bannerUp: (e) => { if (swipe(e).dy < -40) nav("home")(); },
      noticeUp: (e) => { if (swipe(e).dy < -40) nav("discussion")(); },
      lockCards,

      // Page 2. Baselined to the Insider's brightness for both roles (D-142).
      p2Cursor: ins ? "pointer" : "default",
      t1aName: ins ? "REVOKE" : "POWER",
      t1aSub: rs === "armed" ? "ARMED . TOUCH THEIR PHONE" : rs === "cooldown" ? "COOLING DOWN" : ins ? "READY . HOLD TO ARM" : "READY . ALL NOMINAL",
      revokeBorder: rs === "armed" ? A : LINE, revokeFill: rs === "armed" ? DEEP : "#000",
      revokeInk: rs === "cooldown" ? DIM : A, revokeSubInk: rs === "armed" ? A : DIM,
      revokeBar: rs === "cooldown" ? "38%" : "100%", revokeBarInk: rs === "cooldown" ? LINE : A,
      t1bName: ins ? "EGRESS" : "SUBSYS", t1bSub: ins ? "SHARED WITH THE OTHER INSIDER" : "ALL SYSTEMS RESPONDING",
      t2a: ins ? "SURGE" : "BUS", t2b: ins ? "STATIC" : "THERMAL", t2c: ins ? "ISOLATE" : "CACHE",
      t2Note1: ins ? "HOLD TO USE THESE THREE" : "WITHIN NOMINAL PARAMETERS",
      t2Note2: ins ? "SURGE, STATIC AND ISOLATE SHARE THIS" : "BUS, THERMAL AND CACHE NOMINAL",
      surgeRows: ["PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS"],
      isolateRows: [["HALL", "diamond"], ["GARAGE", "triangle"], ["BED 2", "cross"], ["STUDY", "squareframe"]].map(([room, sh]) => Object.assign({ room }, mk(sh))),
      holds,

      parityCells, unavailable: ["SUBROUTINES", "ARCHIVE"],
      mapCells, mapRooms,
      termRooms: counted({ KITCHEN: [2, A], LIVING: [4, DIM], HALL: [1, LINE], STUDY: [0, LINE], GARAGE: [1, A] }),
      lapseRooms: counted({ KITCHEN: [3, DIM], LIVING: [1, DIM], HALL: [2, DIM], STUDY: [0, DIM], GARAGE: [0, DIM] }),
      ghostRooms: counted({ KITCHEN: [2, A], LIVING: [3, A], HALL: [0, A], STUDY: [1, A], GARAGE: [1, A] }),
      jammed: s === "termstatic",

      // The meeting. Nothing here moves the screen: the house moves every phone together.
      checkedIn: st.checkedIn, checkIn: () => this.setState({ checkedIn: true }),
      ciLabel: st.checkedIn ? "YOU ARE HERE" : "I AM HERE",
      ciFill: st.checkedIn ? "none" : A, ciInk: st.checkedIn ? MID : "#000", ciEdge: st.checkedIn ? "2px solid #8A5C12" : "2px solid #FFC759",
      ready: st.ready, markReady: () => this.setState({ ready: true }),
      readyLabel: st.ready ? "YOU ARE READY" : "READY TO VOTE", readyEdge: st.ready ? LINE : A, readyInk: st.ready ? MID : A,
      voteRows, cast: st.cast, notCast: !st.cast,
      ghostVotes: [["ELLIOT", "DANI"], ["PRIYA", "DANI"], ["DANI", "ROSE"], ["ROSE", null], ["TOMAS", null]].map(([by, f]) => ({
        by, for: f || "STILL DECIDING", arrow: f ? "→" : "", ink: f ? A : MID, forInk: f ? A : DIM, forSize: f ? "28px" : "22px" })),
      lockedSettings: ["NETWORK", "PERMISSIONS", "OCCUPANCY"],
      insiders: [["DANI", "I still have your spare key."], ["TOMAS", "You never cancelled the second account."]].map(([name, line]) => ({ name, line })),

      goHome: nav("home"), goJoin: nav("join"), goPerms: nav("perms"), goMaps: nav("maps"), goEditor: nav("editor"), goSecret: nav("secret"),
      goFloors: nav("floors"), goFiles: nav("files"), goRoomEdit: nav("roomedit"), goMarkerSheet: nav("markersheet"), goScanMarker: nav("scanmarker"),
      goTermRemove: nav("termremove"), goMeetRemove: nav("meetremove"), goSaveName: nav("savename"), goHouse: nav("homedetail"),
      goDelete: nav("delete"), goLobby: nav("lobby"), goArmed: nav("armed"), goReveal: nav("reveal"), goRevealThread: nav("revealthread"),
      goPage2: nav("page2"), goWork: nav("work"), goScan: nav("scan"), goLock: nav("lock"), goEgressWidget: nav("egresswidget"),
      goTermLive: nav("termlive"), goTimelapse: nav("timelapse"), goAssemble: nav("assemble"), goSubHandshake: nav("subhandshake"),
      confirmPassage: nav("editor")
    };
  }
}
