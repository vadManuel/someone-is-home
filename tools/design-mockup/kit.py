"""Shared building blocks for Screen.dc.html. Every screen is assembled from these, so a header,
a row or a button is drawn one way everywhere. `{{ x }}` holes pass through untouched."""

FONT = "'VT323',ui-monospace,monospace"
SILK = "font:400 12px/1 'Silkscreen',ui-monospace,monospace;letter-spacing:.08em"
HATCH = "repeating-linear-gradient(45deg,#150E04 0 6px,#000 6px 12px)"

# Palettes. "ink" is the brightest thing allowed on the screen.
DARK = dict(ink="#FFC759", mid="#E39A22", dim="#C08A2E", line="#8A5C12", faint="#5A3D0E",
            pfill="#FFC759", pink="#000", pedge="#FFC759")
MEDIUM = dict(DARK, ink="#E39A22", pfill="none", pink="#E39A22", pedge="#E39A22")
CONCEAL = dict(ink="#C08A2E", mid="#C08A2E", dim="#C08A2E", line="#5A3D0E", faint="#5A3D0E",
               pfill="none", pink="#C08A2E", pedge="#5A3D0E")
BONE = dict(ink="#14110B", mid="#14110B", dim="#332E24", line="#837B68", faint="#837B68",
            pfill="#8FA294", pink="#123328", pedge="#234A3A")

CHEV_L = '<svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinejoin="miter" strokeLinecap="square"><path d="M15 4l-8 8 8 8"></path></svg>'
CHEV_R = '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="square" strokeLinejoin="miter"><path d="M9 4l8 8-8 8"></path></svg>'
CROSS = '<svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="square"><path d="M5 5l14 14M19 5L5 19"></path></svg>'
PLUS = '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="square"><path d="M12 4v16M4 12h16"></path></svg>'


def screen(key, body, P, comment="", pad="12px 20px 20px", gap=12, extra=""):
    c = f"<!-- {comment} -->\n" if comment else ""
    return (f'{c}<sc-if value="{{{{ {key} }}}}" hint-placeholder-val="{{{{ true }}}}">\n'
            f'<div style="flex:1;min-height:0;display:flex;flex-direction:column;padding:{pad};gap:{gap}px;'
            f'font-family:{FONT};color:{P["ink"]};{extra}">\n{body}\n</div>\n</sc-if>\n\n')


def hdr(title, P, back=None, meta=None, back_label="Back", tsize=32):
    m = f'<span style="flex:0 0 auto;font-size:22px;line-height:1;color:{P["dim"]}">{meta}</span>' if meta else ""
    if back:
        b = (f'<button type="button" onClick="{{{{ {back} }}}}" aria-label="{back_label}" style="flex:0 0 auto;width:56px;height:56px;'
             f'border:none;background:none;padding:0;color:{P["ink"]};display:flex;align-items:center;justify-content:center;cursor:pointer">{CHEV_L}</button>')
        return (f'<div style="flex:0 0 auto;display:flex;align-items:center;gap:8px;min-height:56px;margin-left:-16px">{b}'
                f'<span style="flex:1;min-width:0;font-size:{tsize}px;line-height:1">{title}</span>{m}</div>')
    return (f'<div style="flex:0 0 auto;display:flex;align-items:center;gap:12px;min-height:56px">'
            f'<span style="flex:1;min-width:0;font-size:{tsize}px;line-height:1">{title}</span>{m}</div>')


def label(text, P, extra=""):
    return f'<div style="{SILK};color:{P["dim"]};{extra}">{text}</div>'


def cap(text, P, center=False, size=22, extra=""):
    a = "text-align:center;" if center else ""
    return f'<div style="font-size:{size}px;line-height:1.15;color:{P["dim"]};{a}{extra}">{text}</div>'


def body(text, P, size=24, color=None, extra=""):
    return f'<div style="font-size:{size}px;line-height:1.12;color:{color or P["ink"]};{extra}">{text}</div>'


def primary(text, go, P, size=30, h=64, extra=""):
    on = f'onClick="{{{{ {go} }}}}" ' if go else ""
    return (f'<button type="button" {on}style="flex:0 0 auto;box-sizing:border-box;height:{h}px;border:1px solid {P["pedge"]};'
            f'background:{P["pfill"]};color:{P["pink"]};font:400 {size}px/1 {FONT};letter-spacing:.06em;cursor:pointer;{extra}">{text}</button>')


def secondary(text, go, P, size=26, h=56, extra=""):
    on = f'onClick="{{{{ {go} }}}}" ' if go else ""
    return (f'<button type="button" {on}style="flex:0 0 auto;box-sizing:border-box;height:{h}px;border:1px solid {P["line"] if P is not BONE else P["ink"]};'
            f'background:none;color:{P["ink"]};font:400 {size}px/1 {FONT};letter-spacing:.04em;cursor:pointer;{extra}">{text}</button>')


def quiet(text, go, P, size=24, extra=""):
    on = f'onClick="{{{{ {go} }}}}" ' if go else ""
    return (f'<button type="button" {on}style="flex:0 0 auto;box-sizing:border-box;height:56px;border:none;background:none;'
            f'color:{P["mid"] if P is not BONE else P["dim"]};font:400 {size}px/1 {FONT};letter-spacing:.04em;cursor:pointer;{extra}">{text}</button>')


def hold(text, key, P, lit=False, h=64, size=28, note=True, done_text=None):
    """A two-second hold (D-141). The bar along the foot fills while a finger is down."""
    edge = P["pedge"] if lit else (P["ink"] if P is BONE else P["line"])
    bg = P["pfill"] if lit else "none"
    ink = P["pink"] if lit else P["ink"]
    btn = (f'<button type="button" onPointerDown="{{{{ holds.{key}.down }}}}" onPointerUp="{{{{ holds.{key}.up }}}}" '
           f'onPointerLeave="{{{{ holds.{key}.up }}}}" style="flex:0 0 auto;position:relative;overflow:hidden;box-sizing:border-box;'
           f'height:{h}px;border:2px solid {edge};background:{bg};color:{ink};font:400 {size}px/1 {FONT};letter-spacing:.06em;'
           f'touch-action:none;user-select:none;cursor:pointer">'
           f'<span aria-hidden="true" style="position:absolute;left:0;bottom:0;height:5px;width:{{{{ holds.{key}.w }}}};'
           f'transition:{{{{ holds.{key}.tr }}}};background:{ink}"></span>{text}</button>')
    n = cap(f'{{{{ holds.{key}.note }}}}', P, center=True, size=20) if note else ""
    return btn + "\n" + n


def info(k, v, P, h=48, vcolor=None):
    return (f'<div style="display:flex;align-items:center;gap:12px;min-height:{h}px;border-bottom:1px solid {P["faint"]};'
            f'font-size:22px;line-height:1"><span style="flex:1;color:{P["dim"]}">{k}</span>'
            f'<span style="font-size:24px;color:{vcolor or P["ink"]}">{v}</span></div>')


def navrow(text, go, P, trail=CHEV_R, size=26):
    on = f'onClick="{{{{ {go} }}}}" ' if go else ""
    return (f'<button type="button" {on}style="display:flex;align-items:center;gap:10px;min-height:56px;border:none;'
            f'border-bottom:1px solid {P["faint"]};background:none;padding:0 2px;color:{P["ink"]};font:400 {size}px/1 {FONT};'
            f'text-align:left;cursor:pointer"><span style="flex:1">{text}</span>{trail}</button>')


def rules(inner, P):
    return f'<div style="display:flex;flex-direction:column;border-top:1px solid {P["faint"]}">{inner}</div>'


def token(ch, P, size=28, ink=None):
    c = ink or P["ink"]
    return (f'<span aria-hidden="true" style="flex:0 0 auto;box-sizing:border-box;width:{size}px;height:{size}px;'
            f'border:2px solid {c};border-radius:50%;display:inline-flex;align-items:center;justify-content:center;'
            f'font-size:{int(size*.72)}px;line-height:1;color:{c}">{ch}</span>')


def lightmark(hole, ink=None):
    """1, 2 or 3 cells: DARK, MEDIUM, BRIGHT (D-106). `hole` is an object carrying l1..l3, lInk, lightLabel."""
    i = ink or f'{{{{ {hole}.lInk }}}}'
    cell = 'box-sizing:border-box;width:7px;height:14px;border:1px solid ' + i + ';background:{{ %s.%s }}'
    return (f'<span role="img" aria-label="{{{{ {hole}.lightLabel }}}}" style="flex:0 0 auto;display:inline-flex;gap:2px;align-items:center">'
            f'<span style="{cell % (hole, "l1")}"></span><span style="{cell % (hole, "l2")}"></span><span style="{cell % (hole, "l3")}"></span></span>')


def meter(listname, h=12, gap=2):
    return (f'<div aria-hidden="true" style="display:flex;gap:{gap}px;height:{h}px"><sc-for list="{{{{ {listname} }}}}" as="g" '
            f'hint-placeholder-count="20"><div style="flex:1;box-sizing:border-box;background:{{{{ g.c }}}};border:1px solid {{{{ g.e }}}}"></div></sc-for></div>')


def plan(cells, rooms, edge="#837B68", label_size=18, counts=None, count_size=44, static=False):
    """A 10 x 12 floor plan. Bone editor plans pass rooms with bg/ink; live maps pass labelInk and counts."""
    out = (f'<div style="position:absolute;inset:0;display:grid;grid-template-columns:repeat(10, minmax(0, 1fr));'
           f'grid-template-rows:repeat(12, minmax(0, 1fr))"><sc-for list="{{{{ {cells} }}}}" as="c" hint-placeholder-count="120">'
           f'<div style="border-top:{{{{ c.t }}}};border-right:{{{{ c.r }}}};border-bottom:{{{{ c.b }}}};border-left:{{{{ c.l }}}};background:{{{{ c.f }}}}"></div></sc-for></div>')
    out += (f'<sc-for list="{{{{ {rooms} }}}}" as="q" hint-placeholder-count="6"><div style="position:absolute;left:{{{{ q.lx }}}};top:{{{{ q.ly }}}};'
            f'margin:4px;display:flex;align-items:center;gap:4px;padding:1px 3px;background:{{{{ q.bg }}}};color:{{{{ q.ink }}}};'
            f'font-size:{label_size}px;line-height:1">{{{{ q.name }}}}<sc-for list="{{{{ q.chips }}}}" as="k" hint-placeholder-count="1">'
            f'<span style="box-sizing:border-box;min-width:18px;height:18px;padding:0 3px;border-radius:{{{{ k.radius }}}};background:{{{{ q.ink }}}};'
            f'color:{{{{ q.bg }}}};display:inline-flex;align-items:center;justify-content:center;font-size:17px;line-height:1">{{{{ k.t }}}}</span></sc-for></div></sc-for>')
    if counts:
        out += (f'<sc-for list="{{{{ {counts} }}}}" as="q" hint-placeholder-count="5"><div style="position:absolute;left:{{{{ q.cx }}}};top:{{{{ q.cy }}}};'
                f'transform:translate(-50%,-40%);font-size:{count_size}px;line-height:1;color:{{{{ q.ink }}}}">{{{{ q.count }}}}</div></sc-for>')
    if static:
        out += ('<sc-if value="{{ jammed }}" hint-placeholder-val="{{ false }}"><div aria-label="Static" role="img" style="position:absolute;inset:0;'
                'background:repeating-linear-gradient(83deg,rgba(255,199,89,.5) 0 1px,transparent 1px 3px,rgba(0,0,0,.85) 3px 4px,transparent 4px 7px),'
                'repeating-linear-gradient(7deg,rgba(227,154,34,.45) 0 2px,transparent 2px 5px,rgba(0,0,0,.8) 5px 6px,transparent 6px 11px),'
                'repeating-linear-gradient(141deg,rgba(255,199,89,.35) 0 1px,transparent 1px 4px),rgba(0,0,0,.55)"></div></sc-if>')
    return out


def floor_strip(P):
    """GROUND / UPPER on the live maps: a reading, not a control (the shipped strip takes no taps)."""
    return (f'<div style="flex:0 0 auto;display:flex;border:1px solid {P["line"]};height:44px;font-size:24px;line-height:1">'
            f'<span style="flex:1;display:flex;align-items:center;justify-content:center;border-right:1px solid {P["line"]};background:#150E04">GROUND</span>'
            f'<span style="flex:1;display:flex;align-items:center;justify-content:center;color:{P["dim"]}">UPPER</span></div>')


def shape(hole_d, hole_fill, size=20, stroke="currentColor", label_hole=None):
    role = f'role="img" aria-label="{{{{ {label_hole} }}}}"' if label_hole else 'aria-hidden="true"'
    return (f'<svg {role} viewBox="0 0 16 16" width="{size}" height="{size}" fill="none" stroke="{stroke}" strokeWidth="1.5" '
            f'strokeLinejoin="miter"><path d="{{{{ {hole_d} }}}}" fill="{{{{ {hole_fill} }}}}"></path></svg>')
