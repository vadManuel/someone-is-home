"""Build Screen.dc.html for the Claude Design project from the screen modules.

    python3 -I build.py OUT_PATH

OUT_PATH is the project's Screen.dc.html (fetched from the artifact, see README.md).
"""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, here)
import s_setup, s_play, s_work, s_room

def read(name):
    return open(os.path.join(here, name)).read()

out_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "Screen.dc.html")
start = '<div style="position:relative;z-index:1;flex:1;min-height:0;display:flex;flex-direction:column;overflow:hidden">'
body = s_setup.build() + s_play.build() + s_work.build() + s_room.build()
out = (read("shell_head.html") + start + "\n" + body + "\n</div>\n</div>\n\n</x-dc>\n"
       + read("shell_script_tag.html") + read("logic.js") + "\n</script>\n</body>\n</html>\n")
# Writing rule for on-screen copy: no em dashes.
vis = re.sub(r"<[^>]+>", " ", re.sub(r"<!--.*?-->", "", body, flags=re.S))
assert "—" not in vis and "&mdash;" not in vis, "em dash in on-screen copy"
open(out_path, "w").write(out)
print("written", len(out), "screens:", body.count('<sc-if value="{{ s'))
