# Design

Source files for the launcher's look. Reference only: nothing here is bundled in the app
(the app's scenes are painted in code, `app/src/main/java/.../Scenes.kt`, ported 1:1 from round 4).

## Mockups (`mockups/`), in the order we explored them

All boards are 960×540, the TV's screen in Android dp, so sizes carry straight into the app.

| Round | Folder | What it was | Verdict |
| --- | --- | --- | --- |
| 1 | `1-dark-keycaps/` | Japanese keycap idea, first pass: **Sumi** (dark ink + vermilion, rain), **Sakura** (pastel caps, branch), **Yuki** (snowy night, Fuji, moon). Katakana sub-legends. | Too dark; Sumi not liked. |
| 2 | `2-white-keycaps-art/` | From the reference keyboards (white caps with ink-wash/plum art keys; white + pale-pink sakura set): **Ume** (plum & ink art keys, bloom on focus), **Sakura** (pink modifiers, petal splash), **Yuki** (white snow day). Spacebar-style clock bar. | Sakura too pink; no Japanese text wanted. |
| 3 | `3-glass-subtle/` | Stripped back: white keys, app name only, one small nature accent each, then **frosted glass + Apple TV focus** (grow, shadow, sheen) on top. | Too simplistic: scene should be obvious. |
| 4 | `4-glass-scenes/` | **Chosen.** Minimal glass keys over a full scene: Ume (ink-wash mountains, red sun, boat, plum branch), Sakura (cherry tree, falling and fallen petals), Yuki (snowy mountains, pines, snowfall). | Built; themes rotate by season. |
| 5 | `5-app-icons/` | Our own icon set in three styles: A filled, B ink outline + brand dot, C soft brand tint with the symbol in the brand colour. Generic symbols/letters, not the companies' logos. | **C chosen**; built (`AppIcons.kt`). |

Round 3 contains the final state of those boards (subtle version with the glass added); its
`canvas.json` notes already describe round 4.

Design rules we landed on: minimal UI, rich scene · white/frosted keys, colour lives in the
wallpaper · no Japanese characters · no bold colour on keys · Apple TV-style focus.

### Opening them again

Each board is a Design-canvas component (`*.dc.html`, needs the canvas runtime's `support.js`,
so it won't render as a plain HTML file). To view or edit: create a new Design canvas artifact
and publish a folder's files to it under `project/` (the `canvas.json` index + the boards).
The scene drawings are plain inline SVG inside the boards and can be copied out directly.

## Screenshots (`screenshots/`)

Captured from the TV (1920×1080) with the round-4 build: `tv-ume.png`, `tv-sakura.png`,
`tv-yuki.png`, `tv-all-apps.png`, `tv-inputs.png`.

The reference photos of keycap sets that inspired round 2 are not stored here (third-party
product photos).
