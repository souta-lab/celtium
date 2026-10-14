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
