# The no-op census could not fire, and the drawing census that can

2026-11-03. AMD RX 6400 (RADV NAVI24), native-vulkan, 1280x720, `guiScale3` seed options.
Two legs, because the first one measured the placement fix and the second the final JAR:

| leg | renderer | fixture | what it measured |
|---|---|---|---|
| `r40a-gate` | `14ad4125cf47b9198ba751b6af561f5bf4f2f7f4478b7bcac32d626ce466883a` | `03d5e6cdd2d37e4ad7adde0484538b86ecfae1b11709b4d8fa70a13f0c5160b8` | the census in the log, and the zero that is finding 2 |
| `r40b-gate` | `d656bd8593e69978bd38fe78b3f0f50bcd088f260701379b212e8a8d6013ce08` | `863b62aa3710d9e8bb1d3bb913857b29b2026c8d90a52f93d2986f8a873c39e6` | the live drawing line in a real leg |

## The question this instrument exists for

Priority A is "models and custom rendering are invisible". The instrument that answers *which* drawing reached no
image is `CeltiumGlStub`'s no-op census: the stub swallows every GL call by design, and a swallowed call whose job was
to put geometry in the image is a hole in the picture rather than a state detail. Its own doc says so, and says it was
written because `RenderGlobal.renderSky`'s dome, stars and horizon were missing from every GL-less picture while no
counter mentioned them.

## Finding 1: the census was in no file of a run

Measured on leg `r39b-gate` (the previous round's calibrated world leg): `CELTIUM_NOOP_CENSUS`,
`CELTIUM_UNKNOWN_SYMBOLS`, `CELTIUM_BLEND_CENSUS`, `CELTIUM_TEX_PARAM_CENSUS` and `CELTIUM_DISPLAY_LISTS` appear in
**neither** `logs/latest.log` nor `validation-console.log` (the raw stdout capture). In the same teardown,
`CELTIUM_GL_SURFACE trigger=shutdown` **does** appear, twice - once through Log4j and once raw on the process stdout
descriptor. So the JVM runs shutdown hooks in this process and the raw descriptor is writable at that moment; this
particular hook still leaves nothing. The reason is not established and is not needed: the fix does not depend on it.

**Fix.** The census is printed from `celtium$printVerificationSummary` when the trigger is not a bounded snapshot -
that is, at world teardown and at the fixture's own final request, the two points a leg is guaranteed to reach while
the log is still open. This is the same placement and the same reasoning as the font run census in round 19.

## Finding 2: with the line readable, it reads zero - and zero is not clean

Leg `r40a-gate`, first run with the line in the log:

```
CELTIUM_NOOP_CENSUS via=world teardown noopEntryPoints=0 noopCalls=0 top=[]
CELTIUM_NOOP_CENSUS via=fixture end noopEntryPoints=0 noopCalls=0 top=[]
CELTIUM_NOOP_DRAWING via=world teardown none
CELTIUM_NOOP_DRAWING via=fixture end none
```

In the same run:

```
CELTIUM_GL_SURFACE trigger=shutdown rewritten=1041 trackedUnroutedNames=590 calledUnroutedNames=12
  untrackedNames=0 unroutedCalls=47078 [glUseProgram(I)V=35250 nglMultMatrixf(J)V=8179 nglTexGenfv(IIJ)V=3375
  glGetHandleARB(I)I=175 glFramebufferRenderbuffer(IIII)V=70 nglShaderSource(IIJJ)V=18 glTexGeni(III)V=3
  nglShaderSourceARB(IIJJ)V=3 glDetachShader(II)V=2 glCompileShaderARB(I)V=1 glAttachObjectARB(II)V=1
  glLinkProgramARB(I)V=1]
```

47,078 calls were answered with zero and the stub's no-op counter saw none of them. The transformer rewrites the LWJGL
natives and answers them itself, so a rewritten call never reaches the trampoline that increments those counters. The
zero is **"the trampoline was not the path"**, not "nothing was swallowed" - and read as the latter it would have been
the strongest possible false clean bill for priority A.

## Fix 2: the live drawing census

`CELTIUM_GL_SURFACE_DRAWING` is the drawing-only subset of the transformer's own unrouted counts, printed with the
surface report (always at shutdown, in-run only when it names something). It is the line that answers the picture's
question, because it is computed from the counts that actually move.

The first version of it was wrong in a way worth recording: the unrouted census spells a call as the JVM key it
rewrote - `glDrawElements(IIIJ)V` - while the stub's table holds bare names, so a bare-name `contains` was false for
every entry and the line would have read `none` for ever. That is the same failure as finding 2 in a smaller form: a
census that cannot fire. `CeltiumGlStub.isDrawingEntryPointKey` strips the descriptor once, and
`NoopDrawingCensusTest` drives both spellings.

## Positive control

`none` on a run whose unrouted calls are all state is indistinguishable from a dead census, so the CPU check registers
one drawing key and reads the line back:

```
DRAWING-CENSUS positive control named the drawing key
```

`scripts/cpu-contract/transformer-route-verify.sh` fails if the line does not name `glDrawElements(IIIJ)V=1`.

## The live reading, and what it is scoped to

`r40b-gate` (final JAR), the three lines side by side:

```
CELTIUM_NOOP_CENSUS via=fixture end noopEntryPoints=0 noopCalls=0 top=[]
CELTIUM_NOOP_DRAWING via=fixture end none
CELTIUM_GL_SURFACE_DRAWING trigger=shutdown none
CELTIUM_GL_SURFACE trigger=shutdown rewritten=1041 trackedUnroutedNames=590 calledUnroutedNames=12
  untrackedNames=0 unroutedCalls=49803 [glUseProgram(I)V=38550 nglMultMatrixf(J)V=8279 nglTexGenfv(IIJ)V=2700 ...]
```

12 unrouted names, **none of them a drawing entry point**. For the calls that arrived in this scene, the drawing
surface is complete - every drawing entry point that was called was answered by a route that draws.

This is not a claim about the reported symptoms. The calibrated world scene has 0 non-player entities at capture, so
the entity-outline/glow chain does not run; no mod framebuffer is bound while a tooltip is drawn; and no GregTech
display list is compiled. Those three arms are exactly the ones the symptoms name, and this instrument says nothing
about them until a scene exercises them.

## Also measured: the stub's static initializer is at the JIT limit

Adding the drawing-name set inline took `CeltiumGlStub.<clinit>` to **8030** bytes, over HotSpot's 8000-byte
`HugeMethodLimit`, and `HugeMethodLimitTest` failed on it. Above the limit the JIT never compiles the method, and this
is the initializer that registers every GL entry point - it would run interpreted for the whole session. The set moved
to a nested holder class with its own initializer. **Any new static field initializer in `CeltiumGlStub` is now over
the limit**; it has to go in a holder.

## Acceptance for the leg

`r40b-gate` (final JAR): `accepted=true`, `exitCode=0`, `verdict=PASSED`, `fixtureStatus=PASSED`, `problems=[]`,
`nativeDeviceName=AMD Radeon RX 6400 (RADV NAVI24)`, 0 lines matching `VUID|Validation Error|Validation Warning` in
either log, `CELTIUM_REFUSALS_BY_FORMAT {}`, `sceneCalibrationKeyMatches=true`, `sceneGateFloorsEnforced=true`,
`presentedImageVerdict=PASSED`, `worldFrameNonBackgroundFraction=0.989756`, `worldFrameDistinctColours=76457`.
`r40a-gate` (the placement fix) passed the same gate with `worldFrameDistinctColours=76329`.
