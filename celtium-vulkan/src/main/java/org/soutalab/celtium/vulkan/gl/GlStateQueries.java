package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutLong;

/**
 * The stub's state queries, extracted from {@code CeltiumGlStub} (giant-split seam 6, QUEUE 674).
 * Behaviour is unchanged: this class owns the {@code glGet*} answer bodies - the string, integer,
 * float, boolean and buffer/texture-parameter answers, the texture-unit and extension answer
 * censuses, and the capability record read - while {@code CeltiumGlStub} keeps the public doors
 * ({@code celtium$getInteger} and kin) and the dispatch that calls in.
 * <p>
 * The answers read the stub's live recorded state (the raster and texture ledgers, the enable
 * record, the viewport and binding fields), so the implementations take the stub itself rather
 * than a copy of its values: the record is the answer, and a snapshot would answer a frame that
 * no longer exists. The fields and helper methods the queries read are package-private on the
 * stub for this class (the same widening the function-table extraction applied to the constants).
 */
final class GlStateQueries {


    /**
     * {@return the live Vulkan driver identity for {@code GL_RENDERER}}, or {@code "unavailable"} when no device
     * exists.
     * <p>
     * Resolved through {@code TerrainGpuBackends.current()} rather than cached: the window renderer is created at
     * display creation and adopted by the world renderer later, so the answer appears as soon as a device exists and
     * never goes stale across a world change. The string is the one the driver itself reports
     * ({@code VulkanContext.driverSummary()}: driverName/driverID/driverInfo/driverVersion and the device), because
     * the user's F3 request was for the Vulkan driver, not for this stub's name.
     * <p>
     * The fallback is {@code "unavailable"} and deliberately not a stub string: presenting "Celtium stub GL" where a
     * driver name is expected is how the overlay came to claim a driver that was not rendering. A configuration with
     * no device has no driver to name, and says so.
     */
    static String celtium$nativeRendererName() {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                String driver = org.soutalab.celtium.vulkan.VulkanContext.driverSummary();
                if (driver != null && !driver.isEmpty() && !"unavailable".equals(driver)) {
                    return driver + " via Celtium Vulkan";
                }
            }
        } catch (RuntimeException | LinkageError error) {
            // No renderer, or one that cannot answer: the caller gets "unavailable" rather than a stub name.
        }
        return "unavailable";
    }

    static long stringFor(CeltiumGlStub stub, int pname) {
        String value;
        switch (pname) {
            case 0x1F00: // GL_VENDOR
                value = "Celtium";
                break;
            case 0x1F01: // GL_RENDERER
                // The live Vulkan driver the frames are actually drawn on, not this stub's own name: F3 shows this
                // string, and the fixture records it as glRenderer. Before the renderer exists there is no device to
                // name yet, and a configuration without Vulkan has none at all - both answer "unavailable" rather
                // than a stub name standing in for a driver. GL_VENDOR stays "Celtium" so a GL-less leg remains
                // identifiable in a report.
                value = celtium$nativeRendererName();
                break;
            case 0x1F02: // GL_VERSION
                // BH-35: what is advertised is the version whose gates are all backed, enumerated rather than guessed
                // (docs/history/scope-and-limits.md, the compatibility-boundary table). The floor is 3.0, and it is a floor
                // because vanilla reads it through LWJGL's capability flags:
                //   OpenGlHelper.initializeShaders needs framebufferSupported =
                //     openGL14 && (ARB_framebuffer_object || EXT_framebuffer_object || OpenGL30); this stub advertises
                //     no extensions at all, so OpenGL30 is the only term that can make it true; and
                //     shadersAvailable = openGL21 || (ARB shaders), so OpenGL21 is needed too. OpenGL15 (VBOs) and
                //     OpenGL20 (GuiMainMenu) follow from 3.0. Advertising less than 3.0 turns shadersSupported off and
                //     with it the outline/spectator and post-process paths (EntityRenderer:195/219/290,
                //     RenderGlobal:216/565). Advertising more than 3.0 claims entry points this stub answers with
                //     recorded no-ops, which is the lie BH-35 names.
                // The string comes from GlContextTerms, the one set of terms for the created context: the
                // floor above is its default (the compatibility promise), and the only other terms it can
                // hold are the core terms a core-profile creation request at 3.3 or above earns - a core
                // context promises only the programmable surface, which is the surface this renderer
                // executes natively. See GlContextTerms and GLVERSION-DESIGN-2026-11-17.md.
                value = GlContextTerms.versionString();
                break;
            case 0x1F03: // GL_EXTENSIONS
                // The one named extension is backed by the native image binding and storage-image pipeline.
                value = "GL_ARB_shader_image_load_store";
                noteExtensionAnswer("lwjgl2", value, 1);
                break;
            case 0x8B8C: // GL_SHADING_LANGUAGE_VERSION
                // Decided separately from GL_VERSION and by the same rule: the shaders this renderer actually takes are
                // vanilla's, which are #version 120 (ShaderGroup, the outline and the post chains). Nothing in the
                // vanilla sources or the mod cohort gates on this string (Fugue's only glGetString is GL_RENDERER,
                // HEI inherits vanilla's OpenGlHelper gate, flare/scalar/LagSpikeProfiler never query a GL string), so
                // 1.20 is the highest version whose shaders are backed rather than the highest number available.
                value = "1.20";
                break;
            default:
                value = "";
        }
        return intern(stub, value);
    }

    static long stringIndexed(CeltiumGlStub stub, int pname, int index) {
        return pname == 0x1F03 && index == 0 ? intern(stub, "GL_ARB_shader_image_load_store") : 0L;
    }

    static long intern(CeltiumGlStub stub, String ascii) {
        // One buffer is enough: the game compares/prints, it does not keep the pointer. Grow it only when the
        // string is larger than what is already interned.
        byte[] bytes = (ascii + "\0").getBytes(StandardCharsets.US_ASCII);
        if (stub.getStringBuffer == null || stub.getStringBuffer.capacity() < bytes.length) {
            stub.getStringBuffer = ByteBuffer.allocateDirect(bytes.length);
            stub.getStringAddress = memAddress(stub.getStringBuffer);
        }
        stub.getStringBuffer.clear();
        stub.getStringBuffer.put(bytes);
        return stub.getStringAddress;
    }

    /**
     * The texture-unit limits this renderer advertises, as one story across every query surface
     * (TEXUNIT-DESIGN-2026-11-17). Per stage, the count is the size of the binding ledger itself
     * ({@code NativeTextureBindings} holds 32 units) and the same number the leg GPU's own GL driver
     * reports per stage (radeonsi on the RX 6400, measured 2026-11-17); the mod-program pipeline
     * allocates one descriptor per declared sampler, so no smaller native cap exists. Combined is the
     * per-stage count times the two stages whose sampler descriptors the pipeline binds (vertex and
     * fragment) - not the driver's 160, which prices per-stage binding points that this renderer's
     * single shared unit table does not have. Combined is answered on both pnames that carry the
     * question: 0x8F39, and 0x8B4D - the value LWJGL2's GL20 assigns to
     * GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS (35661, javap-verified on the game's 2.9.4 jar), which is
     * the pname GLSM's sampler-cache sizing actually sends. The geometry limit's own pname (0x8C29)
     * stays unanswered, geometry not being a lowered stage. The bootstrap hint carries the same
     * numbers as literals for the window before this provider is installed.
     */
    static final int TEXTURE_UNITS_PER_STAGE = 32;
    static final int TEXTURE_UNITS_COMBINED = 64;
    /** The native image binding ledger and descriptor model both have eight per-program image slots. */
    public static final int IMAGE_UNITS_PER_PROGRAM = org.soutalab.celtium.vulkan.NativeImageBindings.IMAGE_UNITS_PER_PROGRAM;

    /** Pnames whose answers {@link #noteTextureUnitAnswer} reports once: the texture-unit limit family. */
    static final java.util.Set<Integer> TEXUNIT_ANSWER_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Reports the first answer this JVM gives for each texture-unit limit pname. The answers are load-bearing
     * and were invisible: Iris sizes its sampler budget and GLSM sizes its texture-unit arrays from
     * {@code GL_MAX_TEXTURE_IMAGE_UNITS}, and GLSM's RenderSystem sizes its sampler-object cache from
     * {@code GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS} - a pname this stub answered nowhere, so the value that
     * arrived was the getInts default of 0 and the cache was allocated empty (measured as a latent failure
     * during the leg-5 Demonica diagnosis, 2026-11-17; see TEXUNIT-DESIGN-2026-11-17). One line per pname
     * per JVM, so the census stays bounded; the family check is four int compares on a hot path.
     */
    static void noteTextureUnitAnswer(int pname, int value) {
        if (pname != 0x8872 && pname != 0x8B4C && pname != 0x8B4D && pname != 0x8F39) {
            return;
        }
        if (TEXUNIT_ANSWER_LOGGED.add(Integer.valueOf(pname))) {
            System.out.println("[Celtium] CELTIUM_TEXUNIT_ANSWER pname=0x" + Integer.toHexString(pname)
                    + " value=" + value);
        }
    }

    /** Image-unit query census: one line for the sole pname, regardless of scalar or array query surface. */
    static final java.util.Set<Integer> IMGUNIT_ANSWER_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Image bind census: at most 32 distinct (unit, texture) first sightings for the life of this JVM. */
    static final java.util.Set<String> IMAGE_BINDS_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final java.util.Set<String> EXT_ANSWER_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static void noteExtensionAnswer(String surface, String extensions, int count) {
        if (EXT_ANSWER_LOGGED.add(surface))
            System.out.println("[Celtium] CELTIUM_EXT_ANSWER surface=" + surface + " extensions=" + extensions + " count=" + count);
    }
    static void noteImageUnitAnswer(int pname, int value) {
        if (pname == 0x8F38 && IMGUNIT_ANSWER_LOGGED.add(Integer.valueOf(pname)))
            System.out.println("[Celtium] CELTIUM_IMGUNIT_ANSWER pname=0x8f38 value=" + value);
    }
    static void noteImageBind(int unit, int texture) {
        String key = unit + ":" + texture;
        if (IMAGE_BINDS_LOGGED.size() < 32 && IMAGE_BINDS_LOGGED.add(key))
            System.out.println("[Celtium] CELTIUM_IMAGE_BIND unit=" + unit + " texture=" + texture);
    }

    /**
     * {@return the stub's answer to {@code glGetInteger(pname)} for the tracked scalar parameters}, or
     * {@link Integer#MIN_VALUE} when the pname is array-valued or untracked - the same values {@link #getInts} writes,
     * exposed so a caller inside the JVM can ask the stub directly instead of paying a trampoline round trip for a
     * field it already holds. The fn-census measurement that motivated it: 72% of all glGetIntegerv arrivals were
     * GL_TEXTURE_BINDING_2D, most of them this project's own capture and mirror code re-reading state the stub tracks.
     */
    static int celtium$getInteger(int pname) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub == null) {
            return Integer.MIN_VALUE;
        }
        int value = getIntegerValue(stub, pname);
        noteTextureUnitAnswer(pname, value);
        noteImageUnitAnswer(pname, value);
        return value;
    }

    /**
     * {@return the program the stub last bound}, or {@link Integer#MIN_VALUE} when no stub is installed.
     * <p>
     * The batch preflight asks this once per offered batch, and it was going through the general pname switch a few
     * hundred times a frame.
     */
    static int celtium$currentProgram() {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        return stub == null ? Integer.MIN_VALUE : stub.currentProgram;
    }

    /**
     * Records the program {@code glUseProgram} bound, and names the first bind of each program.
     * <p>
     * This is the setter the transformer's route calls. It exists because the field was dead in the shipped path: the
     * stub's own table has answered {@code glUseProgram} with {@code SEM_USE_PROGRAM} since the table was written, but
     * the transformer rewrites every GL native and answers the ones no route claims with a bare return - so the
     * trampoline was never reached and {@code currentProgram} stayed 0 for the whole session. The consequence is not
     * cosmetic: the backend refuses a batch offered while a custom program is bound (it cannot run the program), and
     * with the field stuck at 0 that refusal could never fire, so a mod's shader-drawn full-screen quad was accepted
     * and drawn by the immediate pipeline with the world projection instead of being refused and counted. Measured on
     * the GTCEu scene: {@code CELTIUM_SHADER_PROGRAM_REFUSED} and {@code CELTIUM_PROGRAM_DRAW} both printed zero lines
     * while GTCEu's bloom composite demonstrably ran.
     * <p>
     * The first bind of each program is reported with its call site, because the id alone does not say whose program it
     * is and the stack does.
     */
    static void celtium$useProgram(int program) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub == null) {
            return;
        }
        stub.currentProgram = program;
        if (program != 0 && stub.celtium$programBinds.add(program)) {
            System.out.println("[Celtium] CELTIUM_PROGRAM_BOUND program=" + program
                    + " site=[" + CeltiumGlStub.celtium$callSite() + "]");
        }
    }

    /** The buffer-object query: GL_BUFFER_SIZE answers the recorded or native size, everything else 0. */
    static void getBufferParameterQuery(CeltiumGlStub stub, String name, int target, int pname, long out) {
        if (pname == 0x8764) { // GL_BUFFER_SIZE - LWJGL needs this to size glMapBuffer results
            int bound = stub.boundBuffer(target);
            NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(bound);
            ByteBuffer storage = bound == 0 ? null : stub.bufferStore.get(bound);
            int size = nativeBuffer != null ? nativeBuffer.byteSize()
                    : stub.logicalSizeOf(bound, storage == null ? 0 : storage.capacity());
            if ("glGetBufferParameteri64v".equals(name)) {
                memPutLong(out, size);
            } else {
                memPutInt(out, size);
            }
            return;
        }
        memPutInt(out, 0);
    }

    /** LWJGL3's buffer-parameter query natives; see {@link #getBufferParameterQuery}. */
    static void celtium$getBufferParameter(String name, int target, int pname, long out) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub != null && out != 0L) {
            getBufferParameterQuery(stub, name, target, pname, out);
        }
    }

    /** The non-DSA tex-parameter query: (target, pname, out) on the texture bound to that target's active unit. */
    static void getTexParameterQuery(CeltiumGlStub stub, String name, int target, int paramPname, long out) {
        int textureName = 0;
        if (!CeltiumGlStub.isProxyTarget(target)) {
            int bound = (target == CeltiumGlStub.GL_TEXTURE_2D ? stub.textures.texture2d() : stub.textureBindings.get(stub.texBindKey(target)));
            textureName = bound < 0 ? 0 : bound;
        }
        int value = CeltiumGlStub.textureParameterValue(textureName, paramPname);
        stub.celtium$noteTextureParameterQuery(name, textureName, paramPname, value,
                CeltiumGlStub.textureParameterRecorded(textureName, paramPname));
        if (name.endsWith("fv")) {
            if (paramPname == 0x1004) { // GL_TEXTURE_BORDER_COLOR - four components, GL's initial zero
                for (int i = 0; i < 4; i++) {
                    MemoryUtil.memPutFloat(out + (long) i * 4L, 0.0f);
                }
                return;
            }
            MemoryUtil.memPutFloat(out, value);
        } else {
            memPutInt(out, value);
        }
    }

    /** LWJGL3's tex-parameter query natives, which bypass the dispatch path; see {@link #getTexParameterQuery}. */
    static void celtium$getTexParameter(String name, int target, int pname, long out) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub != null && out != 0L) {
            getTexParameterQuery(stub, name, target, pname, out);
        }
    }

    /** The scalar half of {@link #getInts}; {@link Integer#MIN_VALUE} means "not a tracked scalar pname". */
    static int getIntegerValue(CeltiumGlStub stub, int pname) {
        switch (pname) {
            case 0x0BE1: // GL_BLEND_SRC
            case 0x80C9: return stub.raster.blendSrcRgb(); // GL_BLEND_SRC_RGB
            case 0x0BE0: // GL_BLEND_DST
            case 0x80C8: return stub.raster.blendDstRgb(); // GL_BLEND_DST_RGB
            case 0x80CB: return stub.raster.blendSrcAlpha();
            case 0x80CA: return stub.raster.blendDstAlpha();
            case 0x8009: return stub.raster.blendEquationRgb();
            case 0x883D: return stub.raster.blendEquationAlpha();
            case 0x0B45: return stub.raster.cullFace();
            case 0x0B46: return stub.raster.frontFace();

            case 0x8068: // GL_TEXTURE_BINDING_1D
                return stub.textureBindingOrZero(0x0DE0);
            case 0x806A: // GL_TEXTURE_BINDING_3D
                return stub.textureBindingOrZero(0x806F);
            case 0x8514: // GL_TEXTURE_BINDING_CUBE_MAP
                return stub.textureBindingOrZero(0x8513);
            case 0x8CA7: // GL_RENDERBUFFER_BINDING
                return stub.boundRenderbuffer;
            case 0x8919: // GL_SAMPLER_BINDING - sampler objects are not modelled
                return 0;
            case 0x8A28: { // GL_UNIFORM_BUFFER_BINDING - the generic binding record
                int bound = stub.boundBuffer(0x8A11);
                return bound < 0 ? 0 : bound;
            }
            case 0x8F36: { // GL_COPY_READ_BUFFER_BINDING
                int bound = stub.boundBuffer(0x8F36);
                return bound < 0 ? 0 : bound;
            }
            case 0x8F37: { // GL_COPY_WRITE_BUFFER_BINDING
                int bound = stub.boundBuffer(0x8F37);
                return bound < 0 ? 0 : bound;
            }
            case 0x8895: // GL_ELEMENT_ARRAY_BUFFER_BINDING - the binding the indexed decode reads
                return stub.boundBuffer(0x8893);
            case 0x88ED: // GL_PIXEL_PACK_BUFFER_BINDING
                return stub.pixelPackBuffer;
            case 0x88EF: // GL_PIXEL_UNPACK_BUFFER_BINDING
                return stub.pixelUnpackBuffer;
            case 0x8894: // GL_ARRAY_BUFFER_BINDING, including a native API resource's opaque identity
                return NativeGeometryBuffer.boundId();
            case 0x8069: { // GL_TEXTURE_BINDING_2D - the name bound to TEXTURE_2D on the active unit
                return stub.textures.texture2d();
            }
            case 0x84E0: // GL_ACTIVE_TEXTURE
                return 0x84C0 + stub.textures.activeUnit();
            case 0x8CA6: // GL_DRAW_FRAMEBUFFER_BINDING / GL_FRAMEBUFFER_BINDING
                return stub.drawFbo;
            case 0x8CAA: // GL_READ_FRAMEBUFFER_BINDING
                return stub.readFbo;
            case 0x8CA5: // GL_STENCIL_BACK_WRITEMASK - no stencil state is kept; GL's default makes every bit writable
                return 0xFF;
            case 0x0B33: // GL_LIST_INDEX - the display list being compiled, which the model path keys on
                return stub.compilingList;
            case 0x8B8D: // GL_CURRENT_PROGRAM
                return stub.currentProgram;
            case 0x0BC1: { // GL_ALPHA_TEST_FUNC
                // The mirror is the authority while a backend exists; without one the stub's own record is, which is
                // what the attribute stack puts back and what a mod's raw glAlphaFunc reported here.
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$alphaTestFuncGl() : stub.attribAlphaFunc;
            }
            case 0x0BC2: { // GL_ALPHA_TEST_REF
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                float reference = vulkan != null ? vulkan.celtium$alphaTestReference() : stub.attribAlphaReference;
                return reference >= 0.5f ? 1 : 0;
            }
            case 0x0B50: return enabledCap(stub, 0x0B50) ? 1 : 0; // GL_LIGHTING
            case 0x0B60: return enabledCap(stub, CeltiumGlStub.GL_FOG) ? 1 : 0; // GL_FOG
            case 0x0B71: return enabledCap(stub, CeltiumGlStub.GL_DEPTH_TEST) ? 1 : 0; // GL_DEPTH_TEST
            case 0x0BC0: return enabledCap(stub, CeltiumGlStub.GL_ALPHA_TEST) ? 1 : 0; // GL_ALPHA_TEST
            case 0x0C11: return enabledCap(stub, CeltiumGlStub.GL_SCISSOR_TEST) ? 1 : 0; // GL_SCISSOR_TEST
            // The delivered frame is RGBA8 colour with D24 depth and no stencil. Mods probe these to pick a format or
            // a shadow path; the old answer was zero for every one of them, which reads as "no colour bits at all".
            // The colour answers describe the frame, so while a non-default chain pair is the current target
            // they are stale for the target actually being drawn into - QUEUE 405 (S6) counts that mismatch
            // (targetFormatBitsAnsweredStale) rather than changing an answer mods pick formats by.
            case 0x0D52: celtium$noteBitsAnswer(0x0D52, 8); return 8; // GL_RED_BITS
            case 0x0D53: celtium$noteBitsAnswer(0x0D53, 8); return 8; // GL_GREEN_BITS
            case 0x0D54: celtium$noteBitsAnswer(0x0D54, 8); return 8; // GL_BLUE_BITS
            case 0x0D55: celtium$noteBitsAnswer(0x0D55, 8); return 8; // GL_ALPHA_BITS
            case 0x0D56: return 24; // GL_DEPTH_BITS
            case 0x0D57: return 0; // GL_STENCIL_BITS - this renderer keeps no stencil
            // Display/format probes: the window is double buffered, not stereo, and the delivered frame has no MSAA.
            case 0x0C32: return 1; // GL_DOUBLEBUFFER
            case 0x0C33: return 0; // GL_STEREO
            case 0x80A8: return 0; // GL_SAMPLE_BUFFERS
            case 0x80A9: return 0; // GL_SAMPLES
            case 0x8D57: return 0; // GL_MAX_SAMPLES
            case 0x0B40: // GL_POLYGON_MODE - the rasterizer only fills, and glPolygonMode is a recorded no-op
                return 0x1B02; // GL_FILL
            case 0x0C40: // GL_RENDER_MODE - no feedback or selection mode is ever entered
                return 0x1C00; // GL_RENDER
            case 0x0D50: // GL_SUBPIXEL_BITS - the 8-bit subpixel precision Vulkan rasterization reports
                return 8;
            case 0x0B54: // GL_SHADE_MODEL - the shaders interpolate the vertex colour, which is GL_SMOOTH
                return 0x1D01;
            case 0x0B52: // GL_LIGHT_MODEL_TWO_SIDE - the shader lights one side of every face
                return 0;
            case 0x0C01: // GL_DRAW_BUFFER - the window's back buffer, or the colour attachment when a framebuffer is bound
                return stub.drawFbo == 0 ? 0x0405 : 0x8CE0;
            case 0x0C02: // GL_READ_BUFFER
                return stub.readFbo == 0 ? 0x0405 : 0x8CE0;
            case 0x0BA0: // GL_MATRIX_MODE - the stack glMatrixMode selected, as GL reports it
                return stub.matrixMode;
            case 0x0B74: { // GL_DEPTH_FUNC
                // Answered from the mirror: raw glDepthFunc and GlStateManager both feed it, and neither necessarily
                // touches this stub's own enable record. With no backend the stub's record is the answer, the same one
                // glPushAttrib(GL_DEPTH_BUFFER_BIT) saves and glPopAttrib puts back.
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$depthFuncGl() : stub.attribDepthFunc;
            }
            case 0x0D05: // GL_PACK_ALIGNMENT
                return 4;
            case 0x0CF5: // GL_UNPACK_ALIGNMENT - the recorded value the upload conversion reads rows with
                return stub.unpackAlignment;
            case 0x0CF2: // GL_UNPACK_ROW_LENGTH
                return stub.unpackRowLength;
            case 0x0CF3: // GL_UNPACK_SKIP_ROWS - the same pnames the pixelStore setter uses
                return stub.unpackSkipRows;
            case 0x0CF4: // GL_UNPACK_SKIP_PIXELS
                return stub.unpackSkipPixels;
            case 0x0D02: // GL_PACK_ROW_LENGTH - the readback paths write tightly packed rows
            case 0x0D03: // GL_PACK_SKIP_ROWS
            case 0x0D04: // GL_PACK_SKIP_PIXELS
                return 0;
            case 0x0B65: // GL_FOG_MODE
                return org.soutalab.celtium.vulkan.ImmediateFogState.current().glMode();
            case 0x0D3B: // GL_MAX_CLIENT_ATTRIB_STACK_DEPTH - the bounded stack the push/pop routes use
                return 16;
            case 0x85B5: // GL_VERTEX_ARRAY_BINDING
                return stub.boundVertexArray;
            case 0x8CDF: // GL_MAX_COLOR_ATTACHMENTS - the MRT ledger size: a chain target holds a colour image
                // per slot up to COLOR_ATTACHMENT_SLOTS, and a pass is built with that many attachments
                return CeltiumGlStub.COLOR_ATTACHMENT_SLOTS;
            case 0x8824: // GL_MAX_DRAW_BUFFERS - the same ledger: a program's outputs lower to that many
                // per-location fragment outputs (ModGlslTranslator's MAX_FRAG_OUTPUTS)
                return CeltiumGlStub.COLOR_ATTACHMENT_SLOTS;
            case 0x84E8: { // GL_MAX_RENDERBUFFER_SIZE
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$maxTextureSize() : 16384;
            }
            case 0x0D33: { // GL_MAX_TEXTURE_SIZE
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$maxTextureSize() : 16384;
            }
            case 0x8869: // GL_MAX_VERTEX_ATTRIBS
                return 16;
            case 0x8DFB: // GL_MAX_VERTEX_UNIFORM_VECTORS - a quarter of the component count
                return 1024;
            case 0x8DFD: // GL_MAX_FRAGMENT_UNIFORM_VECTORS
                return 1024;
            case 0x821B: // GL_MAJOR_VERSION
                // Kept consistent with the version string this stub advertises ("3.0.0 Celtium", see the
                // GL_VERSION case below). The two used to disagree - the string said 3.0 while these said 4.6 -
                // and the disagreement was not harmless: MappedStagingBuffer.isSupported asks
                // LWJGL.isOpenGLVersionSupported(4, 4), got true from these numbers, and took the persistent
                // mapping path, whose constructor maps 16 MB through commandList.mapBuffer. Measured
                // 2026-10-15 (leg gv-cohort-fix151): the client stalls inside that construction, in
                // RenderRegionManager's createStagingBuffer, with the world loaded and no player ever created.
                // A capability claim has to be one story: with 3.0 the support test is false and the fallback
                // staging buffer is used instead, which creates a plain mutable buffer. The numbers now
                // come from the same GlContextTerms as the string; under both term sets the (4, 4) test
                // above stays false, because the core cap is 3.3, not a 4.x claim.
                return GlContextTerms.current().major();
            case 0x821C: // GL_MINOR_VERSION
                return GlContextTerms.current().minor();
            case 0x9126: { // GL_CONTEXT_PROFILE_MASK
                // Answered only when the latched terms make a profile claim - a core-profile creation
                // request (GlContextTerms). The default terms predate the profile concept, so the query
                // keeps falling through to the MIN_VALUE answer it has always had.
                int mask = GlContextTerms.current().profileMask();
                return mask >= 0 ? mask : Integer.MIN_VALUE;
            }
            case 0x821D: // GL_NUM_EXTENSIONS
                noteExtensionAnswer("lwjgl2", "GL_ARB_shader_image_load_store", 1);
                return 1;
            case 0x8F38: // GL_MAX_IMAGE_UNITS - the native image binding ledger size
                return IMAGE_UNITS_PER_PROGRAM;
            case 0x84FF: // GL_MAX_TEXTURE_MAX_ANISOTROPY - the sampler path does not filter anisotropically
                return 1;
            case 0x8872: // GL_MAX_TEXTURE_IMAGE_UNITS - the per-stage count the binding ledger backs
                return TEXTURE_UNITS_PER_STAGE;
            case 0x8B4C: // GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS - the pipeline binds vertex-stage samplers too
                return TEXTURE_UNITS_PER_STAGE;
            case 0x8F39: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS - per-stage x the two stages served
                return TEXTURE_UNITS_COMBINED;
            case 0x8B4D: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS as LWJGL2 names it: GL20's constant is
                // 35661 (javap-verified on the game's 2.9.4 jar), so this is the pname the combined
                // query actually arrives on - GLSM sizes its sampler-object cache from it, and the 16
                // this case used to return (a geometry label the value never measured) sized that
                // cache to 16 and overflowed it on the first deferred pass (leg 10, 2026-11-17). The
                // geometry limit's own pname is 0x8C29, which stays unanswered (0): geometry is not
                // a lowered stage. The value is the combined backing, as on 0x8F39 above.
                return TEXTURE_UNITS_COMBINED;
            default:
                return Integer.MIN_VALUE;
        }
    }

    /**
     * Answers tracked integer state for LWJGL3's replaced native query. Untracked capability limits are
     * left to the bootstrap helper, which can also answer them before this provider is installed.
     */
    static boolean celtium$getIntegers(int pname, long out) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub == null || out == 0L) {
            return false;
        }
        if (pname != 0x0BA2 && pname != 0x0C10 && getIntegerValue(stub, pname) == Integer.MIN_VALUE) {
            return false;
        }
        getInts(stub, pname, out, null);
        return true;
    }

    static void getInts(CeltiumGlStub stub, int pname, long out, CeltiumGlStub.Fn fn) {
        if (out == 0L) {
            return;
        }
        int value;
        switch (pname) {
            case 0x0D3A: { // GL_MAX_VIEWPORT_DIMS - two limits
                int dimension = 16384;
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                if (vulkan != null) {
                    dimension = vulkan.celtium$maxTextureSize();
                }
                memPutInt(out, dimension);
                memPutInt(out + 4, dimension);
                return;
            }
            case 0x0BA2: // GL_VIEWPORT
                memPutInt(out, stub.viewportX);
                memPutInt(out + 4, stub.viewportY);
                memPutInt(out + 8, stub.viewportW);
                memPutInt(out + 12, stub.viewportH);
                return;
            case 0x0C23: { // GL_COLOR_WRITEMASK - four integers, R,G,B,A
                int mask = stub.raster.colorMask();
                for (int i = 0; i < 4; i++) {
                    memPutInt(out + (long) i * 4L, (mask >>> i) & 1);
                }
                return;
            }
            case 0x0C10: // GL_SCISSOR_BOX - independent of the viewport, even when scissor is disabled
                memPutInt(out, stub.raster.scissorX());
                memPutInt(out + 4, stub.raster.scissorY());
                memPutInt(out + 8, stub.raster.scissorWidth());
                memPutInt(out + 12, stub.raster.scissorHeight());
                return;
            case 0x8872: // GL_MAX_TEXTURE_IMAGE_UNITS - the per-stage count the binding ledger backs
                value = TEXTURE_UNITS_PER_STAGE;
                break;
            case 0x8F38: // GL_MAX_IMAGE_UNITS - the native image binding ledger size
                value = IMAGE_UNITS_PER_PROGRAM;
                break;
            case 0x0B33: // GL_LIST_INDEX - the list glNewList opened, 0 when none is open
                // This is the answer the model capture reads to decide whether a quad belongs to a list. It asked the
                // driver and got 0, refused the quad with `reason=unknown-list`, and nothing drew the model - so the
                // recording side of display lists was never reached even after the lists were implemented. The stub
                // already tracked the open list; it simply did not answer the question about it.
                value = stub.compilingList;
                break;
            case 0x0B30: // GL_LIST_MODE - GL_COMPILE (0x1300) or GL_COMPILE_AND_EXECUTE (0x1301); 0 when none is open
                value = stub.compilingList == 0 ? 0 : stub.compilingListMode;
                break;
            default:
                value = getIntegerValue(stub, pname);
                if (value == Integer.MIN_VALUE) {
                    value = 0;
                }
        }
        noteTextureUnitAnswer(pname, value);
        noteImageUnitAnswer(pname, value);
        memPutInt(out, value);
    }

    static void getLongs(CeltiumGlStub stub, int pname, long out, CeltiumGlStub.Fn fn) {
        if (out != 0L) {
            memPutLong(out, 0L);
        }
    }

    /**
     * Answers a float query with the number of components its pname has.
     * <p>
     * One float was written for every pname before this, which left the rest of the caller's buffer uninitialised: a
     * sixteen-float matrix read produced one zero and fifteen words of whatever the allocation held, and every
     * transform built from it was garbage. Matrices read the renderer's live state mirror (identity only before a
     * backend exists); scalar and vector queries write exactly the number of components implied by their pname.
     */
    static void getFloats(CeltiumGlStub stub, int pname, long out, CeltiumGlStub.Fn fn) {
        getFloats(stub, pname, out, "glGetDoublev".equals(fn.name));
    }

    /** {@return whether the boolean query was answered}; the caller's buffer receives one byte per component. */
    static boolean celtium$getBooleans(int pname, long out) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub == null || out == 0L) {
            return false;
        }
        getBooleans(stub, pname, out, null);
        return true;
    }

    /** {@return whether the capability is enabled}, answered from the same record {@code glIsEnabled} reads. */
    static boolean celtium$isEnabled(int capability) {
        CeltiumGlStub stub = CeltiumGlStub.installed;
        return stub != null && enabledCap(stub, capability);
    }

    /** The same state-query implementation for callback dispatch and LWJGL3's replaced native entry points. */
    static void celtium$getFloats(int pname, long out, boolean asDouble) {
        if (out == 0L) {
            return;
        }
        CeltiumGlStub stub = CeltiumGlStub.installed;
        if (stub != null) {
            getFloats(stub, pname, out, asDouble);
        } else {
            putFloatOrDouble(out, 0, 0.0f, asDouble);
        }
    }

    static void getFloats(CeltiumGlStub stub, int pname, long out, boolean asDouble) {
        org.soutalab.celtium.vulkan.ImmediateFogState fog = org.soutalab.celtium.vulkan.ImmediateFogState.current();
        if (out != 0L && pname >= 0x0B62 && pname <= 0x0B66) {
            if (pname == 0x0B66) {
                float[] color = fog.getFogColor();
                for (int i = 0; i < 4; i++) putFloatOrDouble(out, i, i == 3 ? fog.colorAlpha() : color[i], asDouble);
            } else {
                float value = pname == 0x0B62 ? fog.getFogDensity() : pname == 0x0B63 ? fog.getFogStart()
                        : pname == 0x0B64 ? fog.getFogEnd() : fog.glMode();
                putFloatOrDouble(out, 0, value, asDouble);
            }
            return;
        }

        if (out == 0L) {
            return;
        }
        switch (pname) {
            case 0x8038: { // GL_POLYGON_OFFSET_FACTOR
                putFloatOrDouble(out, 0, stub.raster.polygonOffsetFactor(), asDouble);
                return;
            }
            case 0x2A00: { // GL_POLYGON_OFFSET_UNITS
                putFloatOrDouble(out, 0, stub.raster.polygonOffsetUnits(), asDouble);
                return;
            }
            case 0x0BA2: { // GL_VIEWPORT
                putFloatOrDouble(out, 0, stub.viewportX, asDouble);
                putFloatOrDouble(out, 1, stub.viewportY, asDouble);
                putFloatOrDouble(out, 2, stub.viewportW, asDouble);
                putFloatOrDouble(out, 3, stub.viewportH, asDouble);
                return;
            }
            case 0x0C10: { // GL_SCISSOR_BOX
                putFloatOrDouble(out, 0, stub.raster.scissorX(), asDouble);
                putFloatOrDouble(out, 1, stub.raster.scissorY(), asDouble);
                putFloatOrDouble(out, 2, stub.raster.scissorWidth(), asDouble);
                putFloatOrDouble(out, 3, stub.raster.scissorHeight(), asDouble);
                return;
            }
            case 0x0C23: { // GL_COLOR_WRITEMASK, as glGetFloatv reports the four channels
                int mask = stub.raster.colorMask();
                for (int i = 0; i < 4; i++) {
                    putFloatOrDouble(out, i, ((mask >>> i) & 1), asDouble);
                }
                return;
            }
            case 0x0B72: { // GL_DEPTH_WRITEMASK, the same boolean state through the float query
                // GL answers boolean-state parameters through every glGet* width; this case was missing, so a
                // save/restore that reads the depth mask with glGetFloatv got the generic zero - which reads as
                // "depth writes are off" and is exactly the state a pop is supposed to have restored.
                putFloatOrDouble(out, 0, stub.attribDepthWrites() ? 1.0f : 0.0f, asDouble);
                return;
            }
            case 0x0C22: { // GL_COLOR_CLEAR_VALUE
                CeltiumGlStub.mainClearValues(stub.clearValueScratch);
                for (int i = 0; i < 4; i++) {
                    putFloatOrDouble(out, i, stub.clearValueScratch[i], asDouble);
                }
                return;
            }
            case 0x0B73: { // GL_DEPTH_CLEAR_VALUE
                putFloatOrDouble(out, 0, (float) CeltiumGlStub.clearDepth(), asDouble);
                return;
            }
            case 0x0B91: { // GL_STENCIL_CLEAR_VALUE - no stencil state is recorded, GL's default is 0
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                return;
            }
            case 0x0BC1: { // GL_ALPHA_TEST_FUNC, as a float as glGetFloatv reports enums
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                float func = vulkan != null ? vulkan.celtium$alphaTestFuncGl() : (float) 0x0207;
                putFloatOrDouble(out, 0, func, asDouble);
                return;
            }
            case 0x0BC2: { // GL_ALPHA_TEST_REF
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                float reference = vulkan != null ? vulkan.celtium$alphaTestReference() : 0.0f;
                putFloatOrDouble(out, 0, reference, asDouble);
                return;
            }
            case 0x0BA6: // GL_MODELVIEW_MATRIX
            case 0x0BA7: // GL_PROJECTION_MATRIX
            case 0x0BA8: // GL_TEXTURE_MATRIX
                // The renderer's own mirror when it has one. Vanilla reads the terrain's projection and model-view
                // through exactly this call (ActiveRenderInfo.updateRenderInfo -> GlStateManager.getFloat), and it
                // builds the frustum the chunk traversal culls with from the same two reads (ClippingHelperImpl). An
                // identity here therefore does two things at once: every section but the camera's own is culled, and
                // the surviving geometry is drawn with clip coordinates equal to camera-relative world coordinates -
                // measured as 1-2 sections instead of 13-17, and as a two-block window of stretched atlas instead of a
                // terrain plane.
                float[] mirrored = mirroredMatrix(pname);
                for (int i = 0; i < 16; i++) {
                    putFloatOrDouble(out, i, mirrored != null ? mirrored[i] : (i % 5 == 0 ? 1.0f : 0.0f), asDouble);
                }
                return;
            case 0x0B00: // GL_CURRENT_COLOR
                putFloatOrDouble(out, 0, stub.currentRed, asDouble);
                putFloatOrDouble(out, 1, stub.currentGreen, asDouble);
                putFloatOrDouble(out, 2, stub.currentBlue, asDouble);
                putFloatOrDouble(out, 3, stub.currentAlpha, asDouble);
                return;
            case 0x0B70: // GL_DEPTH_RANGE
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x0B21: // GL_LINE_WIDTH
                putFloatOrDouble(out, 0, stub.lineWidth, asDouble);
                return;
            case 0x0B11: // GL_POINT_SIZE
                putFloatOrDouble(out, 0, stub.pointSize, asDouble);
                return;
            case 0x8005: // GL_BLEND_COLOR - the constant blend colour is never recorded; GL's initial value is black
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                putFloatOrDouble(out, 1, 0.0f, asDouble);
                putFloatOrDouble(out, 2, 0.0f, asDouble);
                putFloatOrDouble(out, 3, 0.0f, asDouble);
                return;
            case 0x84FF: // GL_MAX_TEXTURE_MAX_ANISOTROPY - the float spelling of the int answer
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0B53: { // GL_LIGHT_MODEL_AMBIENT - the ambient the shader lighting uses
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                float[] ambient = vulkan == null ? stub.lightAmbientDefault : vulkan.celtium$lightAmbient();
                for (int i = 0; i < 4; i++) {
                    putFloatOrDouble(out, i, ambient != null && i < ambient.length ? ambient[i] : 0.0f, asDouble);
                }
                return;
            }
            case 0x0B12: // GL_POINT_SIZE_RANGE - points are not rasterized, so the one supported size is one
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x0B13: // GL_POINT_SIZE_GRANULARITY
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0B23: // GL_LINE_WIDTH_GRANULARITY
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0B22: // GL_LINE_WIDTH_RANGE - two values, not the point size
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, celtium$lineWidthRangeMax(), asDouble);
                return;
            case 0x0D3A: { // GL_MAX_VIEWPORT_DIMS - 0x0D3A is the viewport limit, not the line-width range
                int dimension = 16384;
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                if (vulkan != null) {
                    dimension = vulkan.celtium$maxTextureSize();
                }
                putFloatOrDouble(out, 0, dimension, asDouble);
                putFloatOrDouble(out, 1, dimension, asDouble);
                return;
            }
            case 0x846D: // GL_ALIASED_POINT_SIZE_RANGE - points are not rasterized; one supported size
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x8129: // GL_POINT_DISTANCE_ATTENUATION - GL's initial value, in all three components
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, 0.0f, asDouble);
                putFloatOrDouble(out, 2, 0.0f, asDouble);
                return;
            case 0x846E: // GL_ALIASED_LINE_WIDTH_RANGE - the device's range, not the width currently set
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, celtium$lineWidthRangeMax(), asDouble);
                return;
            default:
                putFloatOrDouble(out, 0, 0.0f, asDouble);
        }
    }

    /**
     * The matrix the renderer's mirror holds for a GL matrix pname, or null when no mirror is published.
     * <p>
     * The stub is the only thing that can answer these reads, and answering the identity is not a neutral choice -
     * it is a culling frustum and a camera transform. The backend publishes what its own hooks observed, so the two
     * agree on what the frame's matrices are.
     */
    static float[] mirroredMatrix(int pname) {
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (!(backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend)) {
            return null;
        }
        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan =
                (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend;
        // Peeked, not copied: the caller writes the sixteen values out at once, and vanilla asks per entity.
        switch (pname) {
            case 0x0BA6:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.VulkanGlState.MATRIX_MODE_MODELVIEW);
            case 0x0BA7:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.VulkanGlState.MATRIX_MODE_PROJECTION);
            case 0x0BA8:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.VulkanGlState.MATRIX_MODE_TEXTURE);
            default:
                return null;
        }
    }

    static void putFloatOrDouble(long out, int index, float value, boolean asDouble) {
        if (asDouble) {
            MemoryUtil.memPutDouble(out + (long) index * 8L, value);
        } else {
            MemoryUtil.memPutFloat(out + (long) index * 4L, value);
        }
    }

    static void getBooleans(CeltiumGlStub stub, int pname, long out, CeltiumGlStub.Fn fn) {
        if (out == 0L) {
            return;
        }
        // The query used to write a single false byte and nothing else, whatever was asked. Callers that save and restore
        // GL state read the whole vector: glColorMask(true,true,true,true) is recorded here, and the readback answered
        // [0,85,85,85] because only the first byte was written and it was false (#388).
        switch (pname) {
            case 0x0C23: { // GL_COLOR_WRITEMASK
                int mask = stub.raster.colorMask();
                for (int i = 0; i < 4; i++) {
                    MemoryUtil.memPutByte(out + i, (byte) (((mask >>> i) & 1) != 0 ? 1 : 0));
                }
                return;
            }
            case 0x0B72: { // GL_DEPTH_WRITEMASK
                // GL's default is enabled. The mirror is the authority: glDepthMask goes through the hint to the backend
                // and never reaches this stub's enable record.
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                boolean mask = vulkan != null ? vulkan.celtium$depthWritesLive()
                        : enabledCap(stub, GL_DEPTH_WRITEMASK_CAP);
                MemoryUtil.memPutByte(out, (byte) (mask ? 1 : 0));
                return;
            }
            case 0x0BD0: { // GL_DITHER
                // GL's default is enabled and dithering has no effect in this renderer, so the answer stays true; the
                // case used to carry GL_DEPTH_TEST's label and return the depth test's state.
                MemoryUtil.memPutByte(out, (byte) 1);
                return;
            }
            case 0x0BE2: { // GL_BLEND
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_BLEND) ? 1 : 0));
                return;
            }
            case 0x0B90: { // GL_STENCIL_TEST
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, 0x0B90) ? 1 : 0));
                return;
            }
            case 0x0B71: { // GL_DEPTH_TEST
                // The label here used to name the scissor test while the case answered 0x0B71 (the depth test), so a
                // save/restore of the GL state read the depth test as whatever the scissor test was.
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_DEPTH_TEST) ? 1 : 0));
                return;
            }
            case 0x0C11: { // GL_SCISSOR_TEST
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_SCISSOR_TEST) ? 1 : 0));
                return;
            }
            case 0x8037: { // GL_POLYGON_OFFSET_FILL
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_POLYGON_OFFSET_FILL) ? 1 : 0));
                return;
            }
            case 0x0B44: { // GL_CULL_FACE
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_CULL_FACE) ? 1 : 0));
                return;
            }
            case 0x0B50: { // GL_LIGHTING
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, 0x0B50) ? 1 : 0));
                return;
            }
            case 0x0B60: { // GL_FOG
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_FOG) ? 1 : 0));
                return;
            }
            case 0x0BC0: { // GL_ALPHA_TEST
                // 0x3000 is GL_CLIP_PLANE0, not the alpha test: the query answered "disabled" every time alpha testing
                // was on, and a mod restoring the state it read turned the test off.
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_ALPHA_TEST) ? 1 : 0));
                return;
            }
            case 0x0DE1: { // GL_TEXTURE_2D
                MemoryUtil.memPutByte(out, (byte) (enabledCap(stub, CeltiumGlStub.GL_TEXTURE_2D) ? 1 : 0));
                return;
            }
            default:
                // A query this stub has no record for: the spec says the value is undefined, and writing false over the
                // caller's whole destination would destroy whatever it had laid out next. One byte stays, as before.
                MemoryUtil.memPutByte(out, (byte) 0);
        }
    }

    /** The index the stub uses in {@code enables} for {@code glDepthMask}, which GL has no capability for. */
    static final int GL_DEPTH_WRITEMASK_CAP = 0x0B72;

    /** {@return the live Vulkan backend, or null when another renderer (or none) is installed. */
    static org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkanBackend() {
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        return backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan ? vulkan : null;
    }

    /**
     * {@return the max of the line-width range the queries answer}: the device's limit, read through the
     * backend, or 1.0 before a backend exists - the only width drawn by then, and the truthful range of a
     * device without wide lines. Answering the width currently set instead (the previous shape of the
     * 0x846E case) made the range read as [1, 1] on wider devices; see the backend's
     * {@code celtium$lineWidthRangeMax} for what that cost.
     */
    static float celtium$lineWidthRangeMax() {
        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$lineWidthRangeMax() : 1.0f;
    }

    /**
     * Hands one framebuffer bit-count answer to the format census (QUEUE 405, S6): the answer is this
     * stub's flat value for the delivered frame, and the backend counts it when the current target's
     * pair class carries different bits. Without a Vulkan backend there is no pair class to disagree
     * with, so nothing is counted.
     */
    static void celtium$noteBitsAnswer(int pname, int answeredBits) {
        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
        if (vulkan != null) {
            vulkan.celtium$noteTargetFormatBitsAnswer(pname, answeredBits);
        }
    }

    /** {@return whether a capability this stub records is enabled}. */
    static boolean enabledCap(CeltiumGlStub stub, int cap) {
        switch (cap) {
            case CeltiumGlStub.GL_FOG: return org.soutalab.celtium.vulkan.ImmediateFogState.current().enabled();
            case CeltiumGlStub.GL_BLEND: return stub.raster.blendEnabled();
            case CeltiumGlStub.GL_CULL_FACE: return stub.raster.cullEnabled();
            case CeltiumGlStub.GL_SCISSOR_TEST: return stub.raster.scissorTest();
            case CeltiumGlStub.GL_POLYGON_OFFSET_FILL: return stub.raster.polygonOffsetFillEnabled();
            case CeltiumGlStub.GL_TEXTURE_2D: return stub.textures.enabled2d(stub.textures.activeUnit());
            // The mirror is the authority for these three: vanilla's GlStateManager cache can skip the native
            // glEnable/glDisable entirely, so this stub's own enable record never sees the change - the model state
            // probe measured glIsEnabled(GL_DEPTH_TEST)=false against a captured depthTest=1.
            case CeltiumGlStub.GL_DEPTH_TEST: {
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$depthTestEnabled() : GlsmEnableState.current().enabled(CeltiumGlStub.GL_DEPTH_TEST);
            }
            case CeltiumGlStub.GL_ALPHA_TEST: {
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$alphaTestEnabled() : GlsmEnableState.current().enabled(CeltiumGlStub.GL_ALPHA_TEST);
            }
            case 0x0B50: { // GL_LIGHTING
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$lightingEnabled() : GlsmEnableState.current().enabled(0x0B50);
            }
            default: break;
        }
        return GlsmQueryFacade.current().isEnabled(cap);
    }

    /** Pixel/semantic routing - the layer the texture path plugs into. Nothing routes yet: calls no-op. */
    static void route(CeltiumGlStub.Fn fn, long[] c) {
        // Intentionally empty.
    }
}
