# The platform's own direction on raw GL access (read 2026-10-15)

Celtium's GL-less mode is not an isolated idea in this ecosystem: Cleanroom's
[Discussion #405, "A Modular Render System for Cleanroom"](https://github.com/CleanroomMC/Cleanroom/discussions/405),
is the platform's design thread for Kirino Engine, and one of its stated goals is
"to slowly deprecate and remove the way to access raw GL calls provided from LWJGL". The thread lists the problem
exactly as this project met it in packs - "modders leak GL state" - and discusses how to detect the calls:

- warn on raw GL calls, either from inside the call with `StackWalker` (noted as a platform performance cost) or by
  wrapping the callers in synthetic methods with a logger;
- **collect all `GL`/`GlStateManager` references from a mod and log them once at startup** rather than per call;
- treat `GlStateManager`, `TextureManager#bindTexture` and `TextureUtil` as raw GL access;
- a compile-time annotation processor to detect GL calls before full removal, with something like `@UnsafeGL` to
  suppress the warnings where a call is harmless.

Why this matters here: the pack leg's failure mode was not a mod drawing with GL, it was a mod *loading a class*
early enough that Celtium's window hint never ran. That is a different failure from the one the platform thread is
designing against, and it is worth saying so - the thread's "log a mod's GL references once" idea would not have
caught it, because RandomPatches' reference to `Display` is an ordinary class reference in its window-icon handler,
not a GL call. The lesson from this project's leg is narrower and complements theirs: **anything that must happen
before a window exists has to be installed from the transformer chain, not from a mixin**, because a pack can define
the class a mixin targets before the mixin exists.

The thread also confirms the platform's own reading of the ecosystem: Cleanroom's README describes Kirino Engine as
the "WIP rendering backbone", which is the same layer Celtium is building natively on Vulkan - worth keeping in view
when the shader and shadow work starts, since the two will meet at the same public entry points.
