# Where the `blackFraction` ceiling actually trips (measured 2026-10-15, GPU-free)

The round-722 cohort A/B had 3 of 8 legs fail the world gate's `blackFraction<=0.05` ceiling
(main-d 0.0640, pr93-b 0.0843, pr93-d 0.0691) while the same gate passed on the other five, on **both** sides of
the A/B. gpu-verifier preserved those run dirs (`.gv-verify/run-*`), so the failing pixels can be located without a
GPU. This note records what the pixels are and what is still unmeasured.

## The pixels are not a band and not a frame-wide failure

Decoding the preserved presented PNGs (1280x720, `max(r,g,b) <= 8` counted as black, the same rule the fixture
uses) gives, per leg:

| leg | whole-frame black | lower-right region (x 760-1240, y 340-700) | distinct colours in it |
|---|---|---|---|
| main-a (PASS) | 0.0106 | **0.0034** | 957 |
| main-c (PASS) | 0.0230 | 0.0556 | 256 |
| main-d (FAIL 0.0640) | 0.0640 | **0.1949** | 1899 |
| pr93-b (FAIL 0.0843) | 0.0843 | **0.2201** | 277 |

- **No row is more than half black and no column is**: a 32x18 occupancy map shows the dark pixels concentrated in
  the lower-middle/lower-right of the frame, as a blob, not as a band, a border or a uniform veil. A full-frame or
  band failure would show dense rows/columns; none exists.
- The failing legs carry **far more distinct colours in that region** (1899) than the passing ones (256), i.e. the
  region holds more content, not a flat dark fill.
- `captureTotalWorldTime` differs between legs (1018 / 890 / 902 / 888) even though the scene pins the light map, so
  the frames are not pixel-comparable to begin with; the A/B's masked-diff distributions said the same thing.

## What is still unmeasured (and what would separate it)

The dark region is where, at `yaw=180 pitch=20 y=12` in this flat scene, the **near terrain and the player's arm**
are drawn. Three explanations remain, and no current counter distinguishes them:

1. **dark terrain content** — a dark block (or a shadowed face) in front of the camera in that leg;
2. **the held arm/skin** — the fixture only sets a held item when `-Dceltium.validation.heldItem` is given, and the
   cohort script does not, so the arm is drawn from the player's own skin;
3. **a partially built or unlit section** — `captureBuildState` reports `U=05/07/07/08` on these legs, and an unlit
   mesh would be dark rather than absent.

The separating measurement is one instrumented leg: report the held stack (`player.getHeldItemMainhand()`), the
block the camera's ray first hits, and the light level at that block, beside the same `blackFraction`. Until that
runs, the gate is **not** to be relaxed and the ceiling is **not** to be reinterpreted — the failing legs are
evidence that something in that region renders very dark, and the analysis above only says *where* it is.
