# Third-party notices — celtium glcompat layer

The `org.soutalab.celtium.glcompat` tree is celtium's direct-GL converter,
converted natively into celtium during Stage S4 of the v0.4.0 overhaul. It is
not a verbatim transplant: the supplier backend SPI (`RenderBackend` /
`BackendManager`, 276-method abstraction) was not carried over, and execution
bottoms out on celtium's own `DirectGlVulkanSink` through the single internal
`DirectGlExecutor`.

## Actinium GLSM (LGPL-3.0)

The state machine, FFP emulation, display-list/immediate-mode recording and
quad conversion derive from the GLSM of Actinium (Actinium-Dev/Actinium @
ce77eed9), itself ported from Angelica (GTNewHorizons). License: LGPL-3.0.
Individual files that carry a Mesa origin note in the upstream tree remain
under the MIT license stated there; the conversion headers in each file point
here.

Not carried over: the backend SPI and its window backends (GLFW/SDL window
machinery), the LWJGL2 drawable/context machinery (replaced by celtium's
single virtual context and `GlCapabilities`), the Angelica event-bus surface
(no consumer exists in celtium), the Angelica configuration classes, and the
LWJGL2 GLU quadrics (no users).

## GTNHLib minimal subset (LGPL-3.0)

`org.soutalab.celtium.glcompat.gtnh` contains a file-by-file minimal subset of
GTNHLib (GTNewHorizons/GTNHLib, as vendored by Actinium @ ce77eed9): the
bytebuf/memory fork, vertex format types, tessellators, VAO/VBO managers and
the CEL model-quad types that the converted GLSM references. The library as a
whole is NOT imported; every file present has a proven consumer in the tree.
License: LGPL-3.0.

## mitchej123 LWJGL compatibility pieces

`gtnh/lwjgl2/memory` (MemoryUtilities / Pointer / PointerBufferCompat) and
`gtnh/lwjgl2/GLExtension` derive from the LWJGL2 compatibility layer by
mitchej123, as vendored in GTNHLib / Actinium. License: LGPL-3.0.
`gtnh/lwjgl2/LWJGLServiceProvider` is a celtium-native replacement written for
this conversion (the service indirection does not exist in celtium's GL-less
runtime; probes answer from the emulated GL 3.3 core profile).

## GLSL transformation library (embedded dependency)

FFP shader transformation parses GLSL with `org.taumc:glsl-transformation-lib`
(the Embeddium GLSL tooling by embeddedt, LGPL-3.0) and its ANTLR 4 runtime
(`org.antlr:antlr4-runtime`, BSD-3-Clause). Both are embedded into the
distribution jar by the forge122 build, the same way Actinium bundles them;
they are not source files of this tree.
