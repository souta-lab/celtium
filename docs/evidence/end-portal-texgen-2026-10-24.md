# End portal texgen (2026-10-24)

User-reported bug ② ("エンドポータル内の描画がおかしい"). Cause: `glTexGen*` were no-ops in the GL stub, so the
portal's fifteen layers (POSITION_COLOR + eye-linear texgen + a texture matrix ending in PROJECTION*MODELVIEW) drew
as untextured colour quads.

Fix: `NativeTexGen` mirrors GlStateManager's texgen calls (eye planes fixed under the model view at the call);
a POSITION_COLOR batch under eye-linear S/T becomes format 12 (`POSITION_COLOR_TEXGEN`), drawn by a texture-pipeline
variant whose shaders take the S/T/Q rows (texture matrix * generated coordinate, over the group's positions) and
divide per fragment. Unit test `NativeTexGenTest` (4 tests) checks the portal identity and GL's plane rules.

Evidence (AMD RX 6400, scene world/qmagnet, camera 223.5/50/210.5 yaw 180 pitch 30, HUD hidden,
fixedWorldTime 1500, validation ON):

| leg | build | portal-surface mean RGB (box 560,365-730,445) | mean abs diff vs reference |
|---|---|---|---|
| `portal-ref-1` | Celeritas 2.5.0-dev `567dcf8a…` (GL) | 10.7 / 21.3 / 28.6 | - |
| `portal-before-2` | `d545c628…` (loggate) | 18.8 / 38.7 / 40.4 | 13.5 / 21.9 / 18.1 |
| `portal-texgen-2` | `7273777a…` (texgen) | 9.7 / 19.8 / 27.3 | 5.5 / 9.5 / 10.2 |

The residual difference is the layers' time-based scroll (`Minecraft.getSystemTime()`), not a fixed offset.
Image: `end-portal-texgen-2026-10-24.png` (reference / before / after). Legs are INCONCLUSIVE by design (the image
gate is calibrated only for the default camera); validation messages 0 on both Celtium legs.
Default-camera acceptance leg `texgen-valid-amd-1`: PASSED, validation 0. Intel not run. End gateway uses the same
renderer and was not separately imaged.
