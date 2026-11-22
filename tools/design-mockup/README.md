# Design mockup generator

Builds `Screen.dc.html`, the one component that draws every screen of the device mockup in the
Claude Design project (`https://claude.ai/code/artifact/c9618b5f-a9d0-4cf4-8e3e-adab35fc2a58`).
The project's other page, `Someone's Home - Device.dc.html` (gallery plus walkable device), is
edited by hand in the artifact and is not generated here.

**Every screen is built from the blocks in `kit.py`**, so a header, a row, a button or a hold is
drawn one way everywhere. Edit a screen module, never the built file.

| file | holds |
|---|---|
| `kit.py` | palettes, header, rows, buttons, two-second hold, light mark, meters, floor plans |
| `s_setup.py` | cold start and host setup (bone field, lights on) |
| `s_play.py` | arming, springboard pages 1 and 2, banners, lantern, Surge and Isolate pickers |
| `s_work.py` | work order, scan path, Files, Terminal, the ten Subroutines |
| `s_room.py` | meetings, the out screens, settings, both endings |
| `logic.js` | the component's `renderVals()`: data, holds, swipes, status strip rules |
| `shell_head.html`, `shell_script_tag.html` | the fixed shell: helmet, root, status strip, props |
| `preview.dc.html` | local preview page: `?s=id,id&r=insider&rs=armed` |

## Loop

1. **Fetch the project** with the Artifact tool: `action: read`, the URL above, `paths`:
   `project/compat.json`, `project/Screen.dc.html`, `project/Someone's Home - Device.dc.html`,
   `project/support.js`. They land in the session scratchpad under `artifact-files/<id>/project/`.
2. **Build** into that folder: `python3 -I build.py <scratchpad>/artifact-files/<id>/project/Screen.dc.html`
3. **Preview before publishing, always.** Copy `preview.dc.html` into the same folder, serve it with
   `python3 -I -m http.server 8765 --bind 127.0.0.1`, open
   `http://127.0.0.1:8765/preview.dc.html?s=home,page2,...` in Playwright and look at the screenshot.
   Playwright writes into the repo's `.playwright-mcp/`: delete it afterwards.
4. **Publish** with the Artifact tool: `url` as above, `root` = the `artifact-files/<id>` folder,
   `file_path` = its `project/compat.json`, `files` = `{"project/Screen.dc.html": "project/Screen.dc.html"}`.
   Do not publish the preview page.

## Rules the screens follow

- **Light rules first** (GDD art direction, D-077, D-093, D-118): one solid amber block per shift
  screen, no glow, gradients or local animation, concealment screens never brighter than `#C08A2E`.
- **Screens say little.** A headline and the next action. The buzz and the light change already say
  that something happened.
- **Variants of one screen share one layout.** Register marker and its two refusals, the two remove
  screens, the scan refusals, the two incoming calls: same template, only the words change.
- **The mockup leads the Kotlin app.** Where they differ, the decision log says which is right
  (Revision 41: D-199, D-200, D-201).
