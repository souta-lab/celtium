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
