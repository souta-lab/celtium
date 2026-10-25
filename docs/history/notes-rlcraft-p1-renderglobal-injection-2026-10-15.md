# RLCraft P1's first blocker: our own `RenderGlobal` injection does not find its target (measured 2026-10-15)

Leg `rlcraft-p1-leg1` (RLCraft 2.9.3, 177 staged JARs + 10 resource packs, isolated profile, `-Xmx5g`) ends
`exitCode=255` after **15.2 s**, `fixtureStatus=MISSING_REPORT`, no device line, no picture. The crash report says
what happened before any of that could be measured:

```
Description: Initializing game
java.lang.NoClassDefFoundError: net/minecraft/client/renderer/RenderGlobal
  at net.minecraftforge.client.ForgeHooksClient.shouldUseVanillaReloadableListener(ForgeHooksClient.java:954)
  ...
Caused by: org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError
Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError:
  Critical injection failure: Callback method sodium$renderTileEntities(...) in
  mixins.celeritas.json:core.terrain.RenderGlobalMixin from mod celeritas failed injection check,
  (0/1) succeeded. Scanned 0 target(s). No refMap loaded.
```

So the class that fails to define is `RenderGlobal`, the mixin is **ours**
(`core.terrain.RenderGlobalMixin`, `@Inject(method = "renderEntities", at = @At(value = "INVOKE", target =
"Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", shift = AFTER, ordinal = 1))`), and
the injector found **no target method at all** (`Scanned 0 target(s)`), which is a critical failure because the
injection does not set `require = 0`.

## What is ruled out

- **The artifact**: `RenderGlobalMixin.class` extracted from the RLCraft leg's celtium jar and from the working
  7-mod cohort leg's jar are **byte-identical** (16086 bytes; `renderEntities` x2, `func_180446_a` x1 in both).
- **"No refMap loaded"**: both jars have no `mixins.celeritas.refmap.json` and the config declares no `refmap`
  field, so that part of the message is the same in the working legs and is not the difference.
- **A generic pack effect**: the 268-mod E2E pack applies the same injection successfully
  (`CELTIUM_RENDERENTITIES pass=1 calls=1` in leg `gv-e2e-268-step4`), so this is RLCraft-specific.

## What the RLCraft set does to that method

Scanning the 177 staged JARs for the SRG name of `renderEntities` (`func_180446_a`), three mods reference it:
`RenderLib-1.12.2-1.2.8` (its refmap), `EntityCulling-1.12.2-6.3.1` (its refmap) and `iChunUtil-1.12.2-7.2.2`
(`me/ichun/mods/ichunutil/common/module/worldportals/client/render/world/RenderGlobalProxy`). Other mods mix into
`RenderGlobal` without naming that method (`particleculling`, `BlockOverlayFix`, `foamfix`'s coremod, whose targets
are `func_174959_b`/`func_177958_n`/`func_177956_o`/`func_177952_p`/`func_184385_a`).

Which of them makes the target vanish is **not established here**; the bisect order that would decide it is
RenderLib → EntityCulling → RLMixins → particleculling → BlockOverlayFix, reading `CELTIUM_RENDERENTITIES` (present
means the injection applied) and the crash in each leg.

## Two responses, in this order

1. **Remove the collider** from the runtime with the pack's substitution policy stated explicitly (the E2E pack
   already excludes Nothirium for the same reason: two things owning the same class).
2. **`require = 0` on that injection** so the game boots without it — this loses the entity-pass handling the
   callback performs, so it is a recovery path that must be logged as a lost injection, never a silent pass. The
   project already learned this shape twice (round 704: `require = 0` did not reach a critical transformer error;
   round 718: a change justified by reading bytecode rather than by a measurement was reverted).

## The strongest candidate: RenderLib's coremod rewrites that exact method (measured 2026-10-15)

`RenderLibClassTransformer` (an `IClassTransformer`, i.e. a coremod transformer that runs **before** Mixin) in the
RLCraft set names our target method directly:

| jar | strings in `RenderLibClassTransformer` |
|---|---|
| RLCraft `RenderLib-1.12.2-1.2.8` | `net/minecraft/client/renderer/RenderGlobal`, **`renderEntities`**, `(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V`, `Transforming method: RenderGlobal#renderEntities(Entity, ICamera, float)` |
| E2E `RenderLib-1.12.2-1.4.5` (where our injection **works**) | the same, plus the SRG name **`func_180446_a`** and `func_71192_d`/`func_71195_b_`/`func_76318_c` |

So the pack that fails carries a coremod that transforms the very method our `@Inject(method = "renderEntities")`
targets, and it is an **older version** (1.2.8) than the one in the pack where the same injection applies (1.4.5).
That is a mechanism, not a proof: what the 1.2.8 transformer does to the method (and why Mixin's selector then finds
`Scanned 0 target(s)`) is not established here, and the version difference is confounded with the rest of the
RLCraft set. The one-leg test decides it: **RLCraft minus `RenderLib-1.12.2-1.2.8`**, reading whether the game
reaches a world and whether `CELTIUM_RENDERENTITIES` appears.

Two further facts for that leg:

- **`Scanned 0 target(s)` means the target method could not be resolved at all**, and the public reports of that
  message are overwhelmingly name-resolution failures in a production environment
  ([geckolib/NeoForge](https://stackoverflow.com/questions/79664084/geckolib-humanoidarmorlayermixin-critical-injection-failure-no-refmap-loaded-neo),
  [Subtle Effects](https://github.com/MincraftEinstein/SubtleEffects/issues/255),
  [Sable](https://github.com/ryanhcode/sable/issues/413)) — and at least one case where **another mod's
  transformation of the target class** caused it, with the workaround being a compatibility fix
  ([Carpet's report naming Lithium](https://github.com/gnembon/fabric-carpet/issues/1999)). Both families are
  consistent with what is measured here; neither is proven for RLCraft yet.
- If RenderLib is the collider, the substitution to try first is **RenderLib 1.4.5** (the version already proven to
  coexist with this injection in E2E) rather than removing the mod, and either way the substitution belongs in the
  pack's documented list next to the Nothirium exclusion.

## Correction: excluding RenderLib does not boot the pack, and the next failure has the same shape (measured 2026-10-15)

Leg `bisect-r1-r6` runs the RLCraft set **without `RenderLib-1.12.2-1.2.8`** (178 jars in `mods/`, RenderLib absent,
same fixture). Results:

- The `RenderGlobal` failure is **gone** — no `Scanned 0 target(s)` for our mixin, and the game gets further.
- It still dies, 7.9 s in, now inside **another mod's mixin**:
  ```
  [pl.asie.foamfix.coremod.FoamFixTransformer:spliceClasses:114]: Spliced in METHOD: net.minecraft.tileentity.TileEntity.func_190559_a
  [CleanMix]: Mixin apply for mod entityculling failed mixins.entityculling.json:MixinTileEntity from mod entityculling
      -> net.minecraft.tileentity.TileEntity
  InvalidInjectionException: Critical injection failure: @Inject annotation on updateCachedBoundingBox
      could not find any targets matching 'updateCachedBoundingBox' in net/minecraft/tileentity/TileEntity
    at ...InjectionInfo.readAnnotation -> TargetSelectors.validate
  ... Caused by: ClassNotFoundException: net.minecraft.tileentity.TileEntity
  [Foundation]: Unable to launch
  ```
- Only 10 celtium mixins had applied by then (the full pack reaches 64), so the run aborts early.

**What this changes**: the failure is **not unique to our mixin and not unique to `RenderGlobal`**. Two different mods
fail with the same shape — an `@Inject` whose **MCP method name resolves to no target** — and the second one fires
right after `FoamFixTransformer` spliced a method into `TileEntity`. RenderLib's transformer naming our exact method
remains a real mechanism for the *first* failure, but **excluding RenderLib is not sufficient to boot the pack**, so
"RenderLib is the collider" is **not** established and must not be recorded as the cause.

The shape now points at a **pack-wide name-resolution or class-definition problem**, and the discriminators that would
separate it are one leg each: **RLCraft − FoamFix** (it spliced into `TileEntity` immediately before the failure),
**RLCraft − EntityCulling** (whose mixin fails), and **RLCraft − RLMixins** (the RLCraft-specific mixin/ASM bundle,
the one component this pack has that the working E2E and cohort sets do not). Until one of those boots the pack, the
honest statement is: *RLCraft's first blocker is a mixin whose MCP method name resolves to nothing, ours is one
instance of it, and the shared cause is unidentified.*

## The blocker chain, measured (2026-10-15, after the substitution legs)

pack-ranger ran the two substitutions. Together with the external evidence, the chain is now:

**1. `RenderLib-1.12.2-1.2.8` breaks our `RenderGlobal` injection** — its `RenderLibClassTransformer` (a coremod
`IClassTransformer`, so it runs before Mixin) names `RenderGlobal.renderEntities` outright, and the leg with that jar
dies at 15.2 s with `Scanned 0 target(s)` for `sodium$renderTileEntities` → `RenderGlobal` never defines.

**2. With RenderLib swapped to 1.4.5 (leg `rlcraft-p1-leg3-renderlib145`) our injection gets past that**, and the run
reaches **21.7 s** — then dies of a *different* failure:

```
Description: Initializing game
java.lang.VerifyError: Bad type on operand stack
  Location: net/minecraft/client/renderer/EntityRenderer.func_78484_h()V @289: invokestatic
  Reason:   Type 'net/minecraft/client/multiplayer/WorldClient' (current frame, stack[1]) is not assignable to
            'net/minecraft/world/biome/Biome'
```

`func_78484_h` is **`addRainParticles`** (`scripts/mc-sources.sh --srg func_78484_h`), and the JAR that patches that
method is **SereneSeasons**: `sereneseasons/asm/transformer/EntityRendererTransformer` references `addRainParticles`,
`func_78484_h`, `net/minecraft/world/biome/Biome`, `net/minecraft/world/World` and `SeasonASMHelper` — i.e. a
shape-sensitive ASM patch of exactly the method the verifier rejects. (DynamicSurroundings' weather renderer also
references `addRainParticles`, but it is not a transformer of it.)

**3. Removing RenderLib is not a valid substitution** — `EntityCulling-1.12.2-6.3.1` references
`meldexun/renderlib` in **16 classes**, so leg `rlcraft-p1-leg4-no-renderlib` (16.3 s, same VerifyError) is **invalid
evidence**: it breaks a dependency rather than isolating a cause. An independent crash report shows the same shape
when RenderLib is absent — `MixinPreProcessorException … MutableAABB … ClassNotFoundException:
meldexun/renderlib/util/MutableAABB`
([Minecraft Forum thread](https://www.minecraftforum.net/forums/support/java-edition-support/3193345-log-error-meaning)).

**Next discriminator (one leg)**: RLCraft with **RenderLib 1.4.5** and **SereneSeasons removed** — if the VerifyError
goes and the game boots, SereneSeasons' transformer is the second blocker and the substitution list gains a second
entry with a named reason. If it does not, the same `VerifyError` shape should be read as a *combination* effect and
the next candidate is whatever else patches `addRainParticles`.

## The second blocker is a known Cleanroom-ecosystem issue, not ours (verified externally 2026-10-15)

The `VerifyError` in `EntityRenderer.func_78484_h` (`addRainParticles`) that leg 3 dies of is reported, with the
same method and the same failure shape, against Cleanroom's own built-in mod:

- [CleanroomMC/Fugue#118 "[Crash] Regression: VerifyError in EntityRenderer with Serene Seasons & OptiFine"](https://github.com/CleanroomMC/Fugue/issues/118):
  "The crash occurs in `EntityRenderer.func_78484_h` (`addRainParticles`)" — the reporter's environment is
  **Cleanroom 0.3.35-alpha, Serene Seasons 1.2.18** (the exact version RLCraft 2.9.3 pins), and the maintainer's
  reply distinguishes it from a plain Fugue regression. In the thread the suggested direction is explicit:
  *"Can we just disable the ASM patches from Serene Seasons and use mixins instead?" — "That's a solution."*
- SereneSeasons upstream **removed** that transformer: the repository comparison shows
  `src/main/java/sereneseasons/asm/transformer/EntityRendererTransformer.java | removed | 0 | 195` alongside
  `ASMHelper`/`ObfHelper`/`SSLoadingPlugin`, i.e. the ASM patch this crash comes from was later dropped in favour of
  mixins ([Glitchfiend/SereneSeasons comparison](https://github.com/Glitchfiend/SereneSeasons/compare/SS-1.7.10-1.x.x...SS-public)).
- A third-party account of the same patch's fragility: *"it's caused by Optifine messed some bytecode and then Serene
  Seasons accidentally removed the `if` statement in `EntityRenderer#renderRainSnow`"*
  ([OptiFine#5959 comment](https://github.com/spx/optifine/issues/5959)) — the transformer edits a method's control
  flow and does not survive another transformer changing it first.

So the second RLCraft blocker is **SereneSeasons' own ASM transformer under Cleanroom**, and the honest handling is a
documented substitution (remove SereneSeasons, or use a build/config without that patch) rather than a change to our
renderer. The leg that confirms it stays the same: **RenderLib 1.4.5 kept, SereneSeasons removed** — if the
`VerifyError` disappears and the game boots, the substitution list gains its third entry with this citation.
