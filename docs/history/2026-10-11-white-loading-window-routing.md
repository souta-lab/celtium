# The white loading window: the name is not dropped, and the splash's groups are offered for a target that rarely runs a pass

Owner: **fog-path** (round-17). Date: 2026-10-11, ~23:30 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `r17-loading` — GL-less, `--validate` fixture leg with a compositor capture (`--interval 0.5`, 68
captures), `enqAll` and layers on, jar `fog-path-mod15.jar` sha256
`6a89906b2982bb30a6a02cc815560901b274f059a199a166d365f059db33e08c` (md5 `55542e67558562b6e2bab447cf1a9133`,
the Lead's sizing-fix jar), fixture `fog-path-fixture8.jar` sha256
`84189e52cb1cc2bba13879e4d441805927692c25e7dd62d06df535ee816889aa`.

## 1. The window and the acceptance numbers

| captures (0.5 s) | content |
|---|---|
| 0000-0024 | black (window not mapped) |
| **0025-0040** | **uniform white, 1 distinct colour (~8 s)** |
| 0041-0066 | content |
| 0067 | black (run end) |

`worldTicksAtEnd=206`, `presentedImageVerdict=PASSED`, `gpuStalled=false`,
`validationMessageCount` on this fixture leg: the harness reported the leg clean; the fixture's own counter
is in the report as `validationAllowedCount=0`, `validationMessageCount=0`.

## 2. Candidate (a) — the name is dropped — is ruled out in this leg

| counter | value |
|---|---|
| `publishedFrameTexturesCreated` | 2 |
| `publishedFrameTexturesUnpublished` | **0** |
| `publishedFrameTexturesRepublished` | **0** |
| `publishedFrameTexturesStalePruned` | **0** |
| `publishedFrameTexturesSkippedForAttach` | 35 |
| `externalImages` | `published=2 republished=0 unpublished=0 live=2` |

The names are published once each and never dropped or re-pointed, and both name the same live image:
`published glTexture=6 image=0x300000000030 view=0x320000000032 size=1280x720` and the same handles for
`glTexture=22`. The chain pair exists and is acquired: `chain(size=1280x720 creations=1 clears=1 selfTests=1
acquired=true)`. So the white is not "the composite paints an empty mirror" on this jar.

## 3. What the routing accounting says

From `offTargetPerTarget` and the `framebuffer bind` lines:

| target | route / pair | offered | atPass | drawnPasses |
|---|---|---|---|---|
| `0` (default pair) | default-pair | 70 | 70 | **70** |
| `0x1d468ab3` (the splash FBO, group 1's target) | chain-pair | 2 | 1 | **1** |
| `0x63482454` | chain-pair | **677** | **32** | **17** |

The splash's own logo group (`frame=0 group=1 fmt=2 texture=8 batches=2 target=0x1d468ab3`) reaches a pass and
draws; the composite (`group=2 fmt=2 texture=6 batches=1 target=0`) is offered for the default pair, which
draws 70 of 70. But the third target carries **677 offered groups with only 32 reaching a pass and 17 drawn** -
645 offered groups for that target never drew at all. That is the counter this round stops at: the white frame
is a cleared frame (`CELTIUM_CLEAR_LATCH colour=(1.0,1.0,1.0,0.0)` and `CELTIUM_TEX_COPY_SERVED mean=255,255,255
opaque=65536/65536`, the Lead's leg) with the splash's groups offered for a target whose pass does not run for
them.

## 4. What is still owed

The measurement that separates the two remaining readings - "the logo quad lands off-screen" from "the groups
for `0x63482454` never reach a pass" - is the splash group's NDC footprint and the ortho it draws under:
`-Dceltium.vulkan.groupBounds=true -Dceltium.vulkan.groupBoundsFormat=2` with `CELTIUM_ORTHO`. The leg for it
was queued (`r17b-bounds`) and was killed by the harness slot wait (the machine is contended); it is the first
thing to re-run, and the counter to read is the group's NDC bounds against the ortho's extents.
