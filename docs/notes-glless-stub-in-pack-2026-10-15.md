# The 268-mod pack leg is not GL-less: `core.LwjglxDisplayMixin` is not applied (measured 2026-10-15)

## What was measured

Leg `gv-e2e-268-step4` (E2E 1.93a, 268 JARs, AMD RX 6400, celtium `51f4bde06332be1a…`, fixture `082f28d859b3ef3e…`,
`-Dceltium.vulkan.glless=true`) versus the 7-mod cohort leg `gv-cohort-main-a` (celtium `644fdbb9293aa8ec…`, same
flags). Both render and both present a world frame; the difference is what the window is.

| observation | cohort | 268-mod pack |
|---|---|---|
| `CELTIUM_GLLESS stub GL provider installed` | **present** | **absent** |
| `CELTIUM_GLLESS window hinted GLFW_NO_API` | **present** | **absent** |
| CleanMix line for `core.LwjglxDisplayMixin` | `Mixing … into org.lwjgl.opengl.Display` + `APPLY` | **no line at all** |
| distinct celeritas mixins applied (CleanMix audit) | **65** | **64** (65 minus the `core.ExplicitGcMixin` exclusion PR #87 makes when LoliASM is present) |
| plugin log | `Found 66 mixin classes` | `Found 66 mixin classes` |
| the class is in the built jar | yes | **yes** (same entry count in both jars) |
| window creation | GLFW_NO_API, no context | `[LWJGLXX] Attempting to create OpenGL 4.6 context` → `Obtained OpenGL 4.6 window handle` |
| `logs/celtium-capabilities.json` `openGl` | — | `renderer="AMD Radeon RX 6400 (radeonsi, navi24, ACO, DRM 3.64, …)"`, `version="4.6 (Compatibility Profile) Mesa 26.2.4-1"`, `contextSource="current-gl-context"` |
| fixture's own probe | `celtium$glLessInstalled()` true | `pictureGlLessProbe_world = "probe: the stub class is present but reports no installation"`, `pictureScreenshotUsable_world=true` |

So in the pack run **a real GL 4.6 context is created and the stub is never installed**: the mixin is registered (the
plugin found all 66 classes) but Mixin never applies it in that configuration. The world frame is still presented
from Vulkan — the renderer drew it natively — but the run is a **hybrid**, not the GL-less path.

## Why this matters

- Completion condition **(b)** ("no GL context is created and a normal frame needs no GL draw/readback/bridge/swap")
  is **not demonstrated for the pack leg** by these runs. It is demonstrated for the 7-mod cohort, where the stub is
  installed and the window is `GLFW_NO_API`.
- It also explains the oddities recorded in rounds 721-723 for the pack leg: a *usable* GL screenshot, and a picture
  identity that says `presented-dump` while the probe says `gl-screenshot`.
- The pack's picture evidence stays valid as **rendering** evidence (a Vulkan-presented 1280x720 frame, 13564
  colours, `validationMessageCount=0`); what it does not show is that the pack runs without a GL context.

## Not yet explained, and the experiment that would explain it

Why Mixin skips that one mixin in the pack is **not** established. The candidates are a coremod interaction or
ordering effect (40 coremods are present in E2E; LoliASM is the one PR #87 already had to work around), or the target
class being owned by another transformer first. The discriminating run is cheap: the **7-mod cohort plus LoliASM
alone**, one leg, reading the same four lines (`stub GL provider installed`, `window hinted GLFW_NO_API`, CleanMix's
`LwjglxDisplayMixin` line, `Found N mixin classes`). If the stub line disappears there, LoliASM is the cause and the
next step is the pack minus it; if it does not, the interaction is with another coremod and the same bisect continues.

## Fix direction (not implemented, deliberately)

The GL-less path currently depends on **one** mixin surviving every coremod in a pack. A stub installed from a
mixin-independent hook (the mod's own construction/loading path, which runs before the display is created) would
make `GL.createCapabilities()` resolve against the stub regardless of that mixin, while the `GLFW_NO_API` hint needs
its own robust path — a stub installed without the hint still leaves a GL context, which is exactly the hybrid state
measured here. Neither change is made yet: the finding is recorded first, and the experiment above decides which of
the two is the one to fix.

## Follow-up investigation (GPU-free, 2026-10-15)

**The mechanism has an upstream name.** CleanMix/Mixin reports `Critical problem: <config>:<mixin> … target <class>
was loaded too early` for `MixinTargetAlreadyLoadedException`: the target class was loaded before Mixin could
transform it. [SpongePowered/Mixin #328](https://github.com/SpongePowered/Mixin/pull/328) exists precisely because
readers blamed another mod's transformation; the exception means early loading. [CleanMix](https://github.com/CleanroomMC/CleanMix)
is the fork Cleanroom uses, and [CleanroomMC/Cleanroom#342](https://github.com/CleanroomMC/Cleanroom/issues/342)
shows the same wording for `stellar_core_forge_asmmodparser.json` and `rlmixins` — the same two families the pack
leg reports.

**But that mechanism does not fully fit our case yet**: the pack's `logs/cleanmix.log` reports "loaded too early" for
two other mods' mixins and has **no line for `core.LwjglxDisplayMixin` at all** — not even the `@Mixin target … was
not found` warning the cohort logs. So "the target was loaded too early" is a candidate, not the established cause;
the bisect below decides it.

**Which staged jars reference `org/lwjgl/opengl/Display`** (scanned class by class over the 270 staged JARs):

| JAR | class that references it |
|---|---|
| `loliasm-5.33` | `zone/rong/loliasm/common/crashes/mixins/MinecraftMixin` (a mixin) |
| `RenderLib-1.12.2-1.4.5` | `meldexun/renderlib/mixin/debug/MixinMinecraft` (a mixin) |
| `vintagefix-0.7.1` | `org/embeddedt/vintagefix/mixin/version_protest/MinecraftMixin` (a mixin) |
| `StellarCore-1.6.0` | **`github/kasuminova/stellarcore/client/util/TitleUtils`** — a plain utility class, so anything that calls it early loads `Display` early |
| `UniversalTweaks-1.12.2-1.21.0` | `…/gamewindow/mixin/UTDisplayTitleMixin`, `UTWindowIconMixin` (mixins) |

**The 7-mod cohort contains none of them** (it is AutoRegLib, ImmersiveEngineering, Mantle, Mekanism, Quark,
TConstruct and Forestry), so the bisect must add one candidate at a time to the cohort and read the same four lines:
`CELTIUM_GLLESS stub GL provider installed`, `window hinted GLFW_NO_API`, CleanMix's `LwjglxDisplayMixin` line and
`Found N mixin classes`, plus any new "loaded too early" line. `StellarCore` is first because its reference is in a
plain class rather than a mixin; the rest follow in the order above.

## The one-leg experiment that decides it: a class-load trace (designed 2026-10-15, GPU-free design)

Everything measured so far narrows the pack's signature to: the plugin **found 66 mixin classes** (logged in every
leg), the Display mixin is therefore in the list it returns, the cohort sees Mixin try to resolve its target
(`Error loading class: org/lwjgl/opengl/Display (CNFE)` → `@Mixin target … was not found` → later `APPLY`), and the
pack has **no line about it at all** — not the CNFE attempt, not the warning, not the apply.

Two mechanisms could produce "never processed", and the logs so far cannot separate them:

1. **The target class was already loaded when the config was prepared.** Mixin then has nothing to defer to and drops
   the mixin without the CNFE/warning pair. The five mods that *reference* `org/lwjgl/opengl/Display` were tested
   directly (leg `gv-cohort-all5`: cohort + StellarCore + loliasm + RenderLib + vintagefix + UniversalTweaks +
   mixinbooter) and **refuted as a sufficient cause** — that leg logs the full four-line sequence and installs the
   stub. So if this is the mechanism, one of the other ~260 pack JARs loads the class, directly or indirectly.
2. **CleanMix/Mixin never bound the mixin to that target at all** (config preparation path), which would be a
   CleanMix-side difference between the two configurations rather than a mod loading a class.

**A class-load trace separates them in one leg, with no code change**: run the pack leg with
`--jvm-arg=-Xlog:class+load=info` (optionally `:file=…`) and read

- the timestamp and **source** of `org.lwjgl.opengl.Display`'s load (`[class,load] org.lwjgl.opengl.Display source:
  jar:file:…`), which names the JAR that caused it, and
- the same log's `Preparing mixins.celeritas.json` / `Found 66 mixin classes` timestamps.

If Display loads **before** the celtium config is prepared, mechanism 1 holds and the source line names the mod; if it
loads **after** and the mixin still never appears, mechanism 2 holds and the next step is a CleanMix-side trace
(`-Dmixin.debug=true`, which prints config preparation and target selection) rather than another bisect.

`shouldApplyMixin` is **not** the cause: this plugin returns `false` unconditionally, but CleanMix's
`MixinInfo.shouldApplyMixin(String)` only consults the plugin when `ignorePlugin` is false, and plugin-supplied
mixins are constructed with `ignorePlugin = true` (`MixinProcessor.applyMixins` → `MixinInfo.shouldApplyMixin` →
`PluginHandle` is the call chain, read from `cleanmix-0.7.2.jar` bytecode). That matches the round-705 note that the
`false` "is not used for whether a mixin is applied".

## Cause named: RandomPatches loads `Display` before the mixin list exists (measured 2026-10-15)

The `-Xlog:class+load=info` leg (`gv-e2e-268-xlog`) reproduced the pack's failure and put timestamps on it:

| time | event |
|---|---|
| **7.123 s** | `Adding [mixins.celeritas.json] mixin configuration.` |
| 8.953 s | `com.therandomlabs.randompatches.config.RPConfig$Window` loads |
| **8.975 s** | **`org.lwjgl.opengl.Display source: __JVM_DefineClass__`** |
| **9.837 s** | **`Found 66 mixin classes`** (the plugin's `getMixins()` runs here) |

So `org.lwjgl.opengl.Display` is defined **after** the celeritas config is added but **before** the mixin list is
built, triggered by **RandomPatches' own tweak initialisation** (`RPConfig$Window`, and its
`randompatches.client.WindowIconHandler.setWindowIcon` appears in the same leg's log). CleanMix defines the class at
that moment, when no celeritas mixin exists yet, so `LwjglxDisplayMixin` can never apply — which is why the pack has
**no line about it at all** while the cohort logs the CNFE/defer/apply sequence. No other config mixins into
`org.lwjgl.opengl.Display` (0 matching CleanMix lines), so the early load is a plain class reference, not a mixin.

**This closes the loop with the platform's own recipe.** RandomPatches is on the Cleanroom migration notes' remove
list for this pack family — the E2Eu entry lists *BetterFPS, Clumps, Hesperus, NotEnoughIDs, Old Java Warning,
**RandomPatches**, Shadowfacts' Forgelin* — and the E2E 1.93a set in this project does contain
`randompatches-1.12.2-1.22.1.10.jar`. RLCraft does **not** contain RandomPatches, and the RLCraft leg installs the
stub and renders GL-less, which is the same fact from the other side.

**Consequences**

1. **The immediate unblock for the pack is a documented substitution**: remove RandomPatches (and the rest of the
   recipe for that family) and the stub should install. The confirmation leg (7-mod cohort plus RandomPatches alone,
   predicting the two `CELTIUM_GLLESS` lines disappear) still pins the cause to one mod, which is task-6's
   acceptance (a).
2. **The fix design changes**: installing `CeltiumGlStub` from the mod's construction would not restore
   `GLFW_NO_API`, because the hint is needed immediately before the window is created — and in this environment that
   creation is triggered by RandomPatches' path, not by a mixin we own. A robust (b) needs a hint path that does not
   depend on `Display.create` at all (a loading-plugin/coremod hook that calls `glfwWindowHint(GLFW_CLIENT_API,
   GLFW_NO_API)` by reflection before any window exists), or a target class that is guaranteed to load after our
   mixins are registered. That is a design decision for the next unit, and it is worth doing even once RandomPatches
   is removed: "one mixin must win a race against every coremod" is the property that failed here.

## What Mixin's own source says about the two registration lists (read 2026-10-15)

The fix in PR #128 moves `core.LwjglxDisplayMixin` from the plugin's `getMixins()` list into
`mixins.celeritas.json`'s `client` list. Whether that changes *when* the mixin is registered is a question about
Mixin, and its source answers it - with a caveat that matters:

```java
// MixinConfig
void prepare(Extensions extensions) {
    ...
    this.prepareMixins("mixins", this.mixinClasses, false, extensions);
    switch (this.env.getSide()) {
        case CLIENT: this.prepareMixins("client", this.mixinClassesClient, false, extensions); break;
        ...
    }
}

void postInitialise(Extensions extensions) {
    if (this.plugin != null) {
        List<String> pluginMixins = this.plugin.getMixins();
        this.prepareMixins("companion plugin", pluginMixins, true, extensions);
    }
    ...
}
```

Three facts follow, and they are quoted from [MixinConfig.java](https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java):

1. **The JSON `client` list is prepared before the plugin's list** - but both are prepared by the *same*
   `prepareConfigs` pass, and the class javadoc says that pass is deferred: "we will invoke the initialisation on
   the first call to either the `hasMixinsFor()` or `getMixinsFor()` methods". In the measured pack leg that pass is
   where `Found 66 mixin classes` appears at **9.837 s** - *after* `Display` was defined at 8.975 s. So moving the
   mixin from one list to the other may **not** change the timing at all, and PR #128's own claim is therefore
   "plausible, unverified" until a leg says otherwise.
2. **Plugin-supplied mixins are prepared with `ignorePlugin = true`** (`prepareMixins("companion plugin", …, true,
   …)`), which confirms the earlier bytecode reading: `MixinInfo.shouldApplyMixin` only consults the plugin when
   `ignorePlugin` is false, so this plugin's unconditional `false` never vetoed its own mixins.
3. A target that is already loaded is a hard error for a `required` config
   (`MixinTargetAlreadyLoadedException` from `MixinInfo.readDeclaredTargets`, seen in the wild as "target … was
   loaded too early"), yet the pack leg neither errors nor logs. That asymmetry is itself evidence that the mixin is
   dropped before that check - and it is the thing the next leg has to look at.

**What the next leg must therefore distinguish**, for the pack with RandomPatches present:

- the JSON route worked (any `LwjglxDisplayMixin` line appears, or the stub installs), or
- it did not (still zero lines), in which case the fix has to be **independent of Mixin registration order
  entirely** - a coremod/`IFMLLoadingPlugin` ASM transformer that edits `org.lwjgl.opengl.Display.create` in the
  transformer chain, where the hint can be placed after `glfwInit` and before `glfwCreateWindow` regardless of when
  any mixin config is prepared. The early `CeltiumGlStub.install()` from `onLoad` stays useful either way: it makes
  the GL function surface resolve to the stub, but it cannot prevent the context from being created.
