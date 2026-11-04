# GTCEu's lamp bloom is missing, and the pipe rows were a different material every run (2026-11-04)

Round 3 of phase 3 put two things into the `gregtech` scene that the previous rounds had not: a row of GTCEu lamps
with their `BLOOM` property set, and a report of the material each pipe row was placed with. The first found a real
rendering gap; the second found that the scene had never been reproducible.

![Celtium (top) and the Celeritas GL reference (bottom) of the same fixture and pose](gregtech-lamp-bloom-2026-11-04.png)

*Same fixture (`efc48aed…` / `30feaa40…`), same pose (`x=0.5 y=6.0 z=0.5 yaw=180 pitch=15`), 1280x720, guiScale=3.
Top: Celtium `r3-gt4` (`be5629a2…`). Bottom: Celeritas 2.5.0-dev GL bridge `r3-gtref2`. The four lamps on the casing
row are a plain white box in Celtium and a blown-out white glow with a halo in the reference. The final leg of the
round, `r3-gt7` (`be5629a2…` + fixture `efc48aed…`), is the same picture and PASSED with `validationMessageCount=0`,
`sceneGateCalibrated=true`, `sceneGateFloorsEnforced=true`, `sceneCalibrationKeyMatches=true`.*

## What was measured

| | Celtium `r3-gt4` | GL reference `r3-gtref2` |
|---|---|---|
| Lamp box mean RGB (x 320..540, y 265..315) | 170.8 / 173.4 / 168.3 | 243.0 / 245.5 / 240.6 |
| Halo band mean RGB (x 300..560, y 230..265) | 155.9 / 185.1 / 210.9 | 209.4 / 236.3 / 233.2 |
| Near-white fraction in the halo band | 0.0283 | 0.1279 |
| World frame (distinct / nonBackground / bands / black) | 7019 / 0.885949 / 714 / 0.011492 | 19349 / 0.902789 / 720 / 0.009129 |
| `gregtechStageLampState` | `BLOOM=true LIGHT=true INVERTED=false POWERED=false` | same |
| `gregtechStageShadersSupported` | `true` | `true` |
| `gregtechStageBloomFbo` | `1280x720 texture=23802` | `1280x720 texture=172` |

The lamp state is the same in both arms, so the difference is not the fixture's placement. `shadersSupported` is true
and GTCEu's bloom framebuffer exists at the window size in both arms, so the mod's bloom path ran in both.

## What the renderer did with the layer

GTCEu adds a `BLOOM` constant to `BlockRenderLayer` (`BloomEffectUtil.init` → `EnumHelper.addEnum`) and renders it by
replacing the fourth `RenderGlobal.renderBlockLayer` call in `EntityRenderer.renderWorldPass` with
`BloomEffectUtil.renderBloomBlockLayer`. The layer therefore arrives at the same entry point as the vanilla four:

```
[Celtium] CELTIUM_BLOCKLAYER layer=Solid name=SOLID pass=2 calls=1 layers=1
[Celtium] CELTIUM_BLOCKLAYER layer=Mipped Cutout name=CUTOUT_MIPPED pass=2 calls=2 layers=2
[Celtium] CELTIUM_BLOCKLAYER layer=Cutout name=CUTOUT pass=2 calls=3 layers=3
[Celtium] CELTIUM_BLOCKLAYER layer=Bloom name=BLOOM pass=2 calls=4 layers=4
[Celtium] CELTIUM_BLOCKLAYER layer=Translucent name=TRANSLUCENT pass=2 calls=5 layers=5
```

and the pass has geometry, not just a draw call:

```
[Celtium] CELTIUM_LAYER_GEOMETRY layer=Bloom regions=6 passes=[TerrainRenderPass[name=bloom]=true]
```

So the custom layer is built, the pass is drawn, and the mod's framebuffer is created. What is missing is the
composite: GTCEu's `emissiveTexturesBloom=true` path renders the bloom layer into its own `Framebuffer`, then runs
`Shaders.renderFullImageInFBO` and `BloomEffect.renderUnreal` - full-screen quads drawn with CCL `ShaderProgram`s -
and composites the result additively. Under GL-less those programs are stubs (`glShaderSource`, `glCompileShader`,
`glLinkProgram` and the `glUniform*` family are recorded no-ops in `CeltiumGlStub`), so the composite cannot produce
the blurred halo.

One hypothesis was tested and rejected: the composite's self-sampling passes are dropped by
`-Dceltium.vulkan.skipSelfComposite` (default on), so the same jar was run with the drop off (`r3-gt5`). The lamp
region is pixel-identical (mean 145.96/155.86/145.25 in both), so the drop is not what removes the glow.

The mod's own gate is open, measured from the fixture (`r3-gt9`): `Shaders.allowedShader()` is `true`,
`Shaders.IMAGE_F` is a `ShaderObject`, and the bloom framebuffer is `1280x720`. So `renderFullImageInFBO` does not
return early - it binds the framebuffer, calls `ShaderProgram.useShader` (which calls `GL20.glUseProgram`) and draws
the quad through `Tessellator`. A census of the GL stub's draw entry (`CELTIUM_PROGRAM_DRAW`, one line per program a
draw is decoded under) reports **zero** draws with a non-zero program, and the backend's
`CELTIUM_SHADER_PROGRAM_REFUSED` (which fires when a batch is offered while a custom program is bound) does not fire
either. So the composite's full-screen quad does not reach either draw path with the program bound: the next
question is where it goes instead (the `VertexBuffer` path is cancelled into `CeltiumNativeDraw.vertexBuffer`, and
the `WorldVertexBufferUploader` hook offers every `Tessellator` batch, so the two candidates are a program that is
already 0 by the time the batch is offered and a batch that never reaches the uploader).

## The pipe rows were not reproducible

The item pipe row measured lavender in `r2-gt6` and cream in `r2-gtref` with the same fixture and pose. The cause is
the fixture, not the renderer: `BlockMaterialPipe.getSubBlocks` iterates a `HashMap<Material, …>`, `Material` declares
no `hashCode`, and the identity hash order differs between JVMs - so "the first sub-block" was a different material,
and a GTCEu pipe's colour is its material's RGB (`PipeRenderer`'s `ColourMultiplier`). The fixture now picks the
sub-block with the lowest (item id, metadata) and reports the material:

```
gregtechStageCableMaterial = aluminium=0x80C8F0
gregtechStageFluidPipeMaterial = aluminium=0x80C8F0
gregtechStageItemPipeMaterial = americium=0x287869
```

Both arms of this round report the same three materials, and the item pipe row is the same dark teal in both pictures
(`docs/evidence/gregtech-item-pipe-material-2026-11-04.png` is the earlier lavender/cream pair that named the defect).

## Next

The composite is the remaining question, and it is now a narrow one: count the full-screen quads offered while a
custom program is bound and their refusal reason. `CELTIUM_SHADER_PROGRAM_REFUSED` did not fire in this run, so the
composite's draw is either never offered (the program is 0 at draw time, or the draw is refused earlier for its
format) or offered and dropped by a rule that does not name itself. The instrument to add is a per-program census of
the full-screen POSITION_TEX draws, with the refusal reason, at the point the immediate path decides.

## Round 4: the program bind had no route (fixed)

The census was empty because `glUseProgram` never reached the stub. The transformer rewrites every GL native and
answers the ones no route claims with a bare return; `glUseProgram` had no route, so the stub's tracked program stayed
0 for the whole session. With it at 0 the backend's "a custom shader program is bound" refusal could never fire, and
the composite's full-screen quad was accepted by the immediate path and drawn with the world projection.

The fix routes `glUseProgram` / `nglUseProgram` / `glUseProgramObjectARB` to `CeltiumGlStub.celtium$useProgram`, and
`CELTIUM_PROGRAM_BOUND` names the first bind of each program with its call site. Legs `r5-gt1` (census) and `r5-gt2` (acceptance: PASSED, validationMessageCount=0, gpuRequirementMet=true, sceneCalibrationKeyMatches=true; renderer
`ea32d975…`, fixture `22fcf3e0…`, AMD RX 6400, scene=gregtech):

```
CELTIUM_PROGRAM_BOUND program=23887 site=[... codechicken.lib.render.shader.ShaderProgram.useShader:122 <- gregtech.client.shader.Shaders.renderFullImageInFBO:130]
CELTIUM_SHADER_PROGRAM_REFUSED program=23887 format=6 (first batch refused with this program bound)
```

(Intel UHD 630, leg `r7-gt-intel`, same jar: PASSED, validationMessageCount=0, gpuRequirementMet=true, the same four refusals.)

and the same three lines for programs 23916, 23917 and 23918: four GTCEu bloom programs, all bound from
`Shaders.renderFullImageInFBO`, all refused and counted. No other program was bound in the run (no vanilla program:
the vanilla post chain is cancelled at `ShaderGroup.render` HEAD and never calls `useShader`). The lamps are still a
plain white box - the bloom stays missing until mod GLSL is compiled and run natively (goal.md, 2026-11-04) - but the
gap is now counted per program instead of being drawn as a different picture.

## Round 5: the programs can be lowered to Vulkan GLSL (not yet executed)

`ModGlslTranslator` lowers a mod's legacy program (vertex + fragment, or fragment-only with a synthesized
fixed-function vertex stage) to GLSL 450: attribute/varying locations, one std140 block for the non-sampler
uniforms with each member's offset, sampler bindings, `gl_FragColor`/`gl_FragData[0]`, `texture2D*`, the
fixed-function inputs (`gl_Vertex`, `gl_Color`, `gl_MultiTexCoord0`, `gl_ModelViewProjectionMatrix`, `ftransform()`,
`gl_TexCoord[0]`, `gl_FrontColor`), names GLSL 450 reserves (GTCEu's `blackhole.frag` calls its sampler `texture`),
GL's lower-left `gl_FragCoord` (a hidden `celtium_TargetHeight` uniform) and the clip-space convention.

`ModGlslTranslatorTest` compiles the output with shaderc (Vulkan 1.2). Run against GTCEu 2.8.10's own shaders
(extracted locally, `CELTIUM_MOD_SHADER_DIR`, not committed): **14 of 14 lowered and compiled for both stages**,
including the seven the bloom chain uses (`image`, `down_sampling`, `up_sampling`, `blur`, `seperable_blur`,
`bloom_combine`, `composite`). Full `:celtium-vulkan:test`: 426 tests, 0 failures.

Still missing for the bloom to appear: the stub has to keep the sources and `glUniform*` values per program, the
backend has to build a pipeline from the SPIR-V and run the batch into the mod's framebuffer, and the shipped jar has
to carry shaderc's natives (distribution decision, see the report of 2026-11-04).

## Round 6: the stub keeps each mod program's sources, link result and uniform values

`glShaderSource`/`glAttachShader`/`glLinkProgram`/`glDeleteProgram` and every `glUniform*` spelling (scalar,
pointer and matrix forms, ARB included) were listed no-ops; they now route to `ModProgramRegistry`, which lowers the
program at link (`CELTIUM_MOD_PROGRAM_LINKED`) and stores uniform values under the uniform's name (the stub's
location is the name hash, so a location maps back once the program is linked). The refusal line now says whether the
refused program could run natively.

Leg `r13-gt` (renderer `bc7f37b5…`, fixture `22fcf3e0…`, AMD RX 6400, scene=gregtech): PASSED,
validationMessageCount=0, gpuRequirementMet=true.

```
CELTIUM_SHADER_PROGRAM_REFUSED program=23812 format=6 lowered=true uniformWrites=0
CELTIUM_SHADER_PROGRAM_REFUSED program=23841 format=6 lowered=true uniformWrites=4
CELTIUM_SHADER_PROGRAM_REFUSED program=23842 format=6 lowered=true uniformWrites=7
CELTIUM_SHADER_PROGRAM_REFUSED program=23843 format=6 lowered=true uniformWrites=6
```

All four bloom programs are lowered and carry their values (23812 is the one-sampler `image` pass, which sets
nothing). Leg `r13-world` (same jar, `celtium-world-accept`, 188 mods, validation on): PASSED,
validationMessageCount=0; the ten programs linked in that run all lowered. CPU: route verifier 259 calls / 87
classes / 0 failures, `:celtium-vulkan:test` green.

What remains for the bloom: run the batch natively (pipeline from the SPIR-V, the block packed from the recorded
values, samplers resolved from texture units to native images, the mod framebuffer as target) and ship shaderc.

## Round 7: the programs compile to SPIR-V inside the game

`ModShaderCompiler` compiles both lowered stages at link when LWJGL's shaderc binding and native load
(`CELTIUM_MOD_PROGRAM_LINKED ... compiled=`). Cleanroom's mod class loader does not see a jar added to the launcher
classpath (leg `r15-gt`: `ClassNotFoundException: org.lwjgl.util.shaderc.Shaderc` with the jar on `-cp`), so the
**binding classes** are embedded like lwjgl-vulkan's; the **native library is not shipped** (distribution decision).

Leg `r16-gt` (renderer `eb6a8656…`, fixture `22fcf3e0…`, AMD RX 6400, scene=gregtech, natives supplied to the leg
only with `-Dorg.lwjgl.librarypath=<dir with libshaderc.so from lwjgl-shaderc-3.4.3-natives-linux>`): PASSED,
validationMessageCount=0, gpuRequirementMet=true; every linked program compiled, the four bloom programs among them:

```
CELTIUM_MOD_PROGRAM_LINKED program=23812 compiled=true spirvBytes=1500 lowered=true samplers=1 uniformBytes=0
CELTIUM_MOD_PROGRAM_LINKED program=23841 compiled=true spirvBytes=2908 lowered=true samplers=1 uniformBytes=32
CELTIUM_MOD_PROGRAM_LINKED program=23842 compiled=true spirvBytes=2612 lowered=true samplers=5 uniformBytes=16
CELTIUM_MOD_PROGRAM_LINKED program=23843 compiled=true spirvBytes=2584 lowered=true samplers=2 uniformBytes=16
```

Without the native (every ordinary install today) the line reads `compiled=false compileFailure=shaderc unavailable`
and `CELTIUM_MOD_SHADERC available=false reason=...` names why, once.

## Next unit: run the batch natively (design, not built)

What exists: per program, the lowered stages and their SPIR-V, the std140 member offsets, the sampler bindings
(1..n), and the recorded uniform values by name (`ModProgramRegistry.Program`). The batch arrives at the
immediate path's program refusal (`submitImmediateBatchBody`, the `boundProgram != 0` branch).

Steps, each separately verifiable on the gregtech scene:

1. **Vertex inputs.** GL aliases generic attribute 0 with the conventional vertex position, which is how GTCEu's
   `attribute vec2 position` is fed by a Tessellator POSITION_TEX batch. Map: location 0 <- position; a lowered
   `celtium_MultiTexCoord0` <- the batch's UV; `celtium_Color` <- its colour. Refuse any other attribute.
2. **Inputs.** A sampler's value is a texture unit; the texture bound on that unit at draw time is the input. Check
   that the stub/state mirror keeps the binding per unit (GTCEu binds its FBO textures on units 0..4 with
   `GlStateManager.setActiveTexture`), and resolve each name to its native image through the texture registry.
3. **Executor.** Generalise `VulkanPostChain.Program` from its fixed table (two samplers, UBO at binding 2) to a
   program built from runtime SPIR-V with N samplers at bindings 1..n and the block at binding 0, and a pass whose
   output is the mod framebuffer currently bound (the raw/mod FBO target the off-target routing already resolves).
4. **Ordering.** Treat the batch like a post-chain pass inside the immediate stream: flush the open window, record
   the pass with the read barriers the chain already issues for its inputs, then continue.
5. **Verify.** The lamp-row metrics in this file (lamp box mean RGB ~243, near-white halo fraction ~0.128 in the GL
   reference vs ~171 / 0.028 now) are the acceptance numbers, on both GPUs, validation 0.

Distribution: libshaderc for Windows and Linux (~4 MB each) has to ship for any of this to run outside a leg.
