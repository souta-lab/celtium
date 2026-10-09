package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Offers every finished immediate-mode batch to Vulkan before OpenGL draws it.
 * <p>
 * {@code WorldVertexBufferUploader.func_181679_a} is the single draw entry of Minecraft's immediate path:
 * {@code Tessellator.func_78381_a()} ends here, and so does every renderer built on {@code BufferBuilder} - entities,
 * tile entities, items and the hand, particles, the GUI, the sky, clouds and weather. Capturing it is what makes the
 * Forge API swap possible without changing a single mod.
 * <p>
 * The batch is only *offered*: when the backend takes it the original body is cancelled, and when it refuses the
 * original body runs and OpenGL draws exactly as before. Nothing public is changed.
 * <p>
 * Private members are reached through the {@link BufferBuilderAccessor} mixin rather than reflection, and the vertex
 * count through the public {@code getVertexCount()}. The first revision reflected per batch
 * ({@code getDeclaredField}/{@code setAccessible}, {@code Class.forName}/{@code getMethod}/{@code invoke}), which
 * measured 29252 ns per offered batch; the accessor removes the lookup at compile time and the refmap remaps it like
 * any other mixin reference.
 */
@Mixin(WorldVertexBufferUploader.class)
public abstract class WorldVertexBufferUploaderMixin {
    /** Layout ids, decided by comparing element lists rather than class names or strides. */
    private static int celtium$compositeDumps;

    private static int celtium$nullFormatDumps;
    private static int celtium$compositeRefusals;
    private static int celtium$positionTexRefusals;
    /** Uploads seen per mapped format id, before any accept/refuse decision. */
    private static final int[] celtium$arrivalsByFormat = new int[9];
    private static int celtium$arrivalTotal;

    /** Describes a format the same way the dump does, so an unmatched shape can be put beside a known one. */
    private static String celtium$describe(VertexFormat format) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < format.getElementCount(); i++) {
            text.append(format.getElement(i)).append(' ');
        }
        return text.toString().trim() + " size=" + format.getSize() + " count=" + format.getElementCount()
                + " indices=" + celtium$indices(format);
    }

    /** The element indices, which the element list's text does not show and which a full comparison includes. */
    private static String celtium$indices(VertexFormat format) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < format.getElementCount(); i++) {
            text.append(format.getElement(i).getIndex()).append(',');
        }
        return text.length() == 0 ? "-" : text.substring(0, text.length() - 1);
    }

    private static String celtium$stack() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < Math.min(trace.length, 14); i++) {
            text.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName())
                    .append(':').append(trace[i].getLineNumber()).append(" <- ");
        }
        return text.toString();
    }

    private static final Map<VertexFormat, Integer> celtium$formatIds =
            Collections.synchronizedMap(new WeakHashMap<VertexFormat, Integer>());

    // cancellable = true is what makes info.cancel() mean anything: with it false - the default - cancel() throws, and
    // this injector's broad catch swallowed that exception, so the original GL draw ran for every batch the backend had
    // already accepted. The Vulkan path and the GL path drew the same geometry, the images matched, and nothing in the
    // comparisons could tell the difference. Counts of "accepted" batches were counts of intent, not of replacement.
    /** How often the vanilla upload body ran to completion, which is the independent GL count. */
    private static int celtium$glUploads;
    /** How often this injector was entered at all, whatever it then decided. */
    private static int celtium$offers;
    /** How often the backend returned true, i.e. claimed the batch. */
    private static int celtium$acceptedByBackend;
    /** How many swallowed failures have been reported, so a recurring one is visible without flooding the log. */
    private static int celtium$swallowed;

    /**
     * Counts the vanilla body finishing.
     * <p>
     * The audit's instrument: with the head injector cancelling accepted batches, this runs only for batches that went to
     * GL. Reading it beside the offer count says whether replacement actually happens, which no image comparison could -
     * the earlier comparisons had GL drawing everything because the cancellation was never effective.
     */
    @Inject(method = "func_181679_a", at = @At("RETURN"), require = 0)
    private void celtium$countGlUpload(BufferBuilder buffer, CallbackInfo info) {
        celtium$glUploads++;
        if (celtium$offers % 500 == 0 && celtium$offers > 0) {
            System.out.println("celtium uploads: offers=" + celtium$offers + " glUploads=" + celtium$glUploads);
        }
    }

    @Inject(method = "func_181679_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$offerBatch(BufferBuilder buffer, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null || buffer == null) {
            return;
        }
        celtium$offers++;
        if (celtium$offers % 1000 == 0) {
            // The decision, from the side that makes it: offers, how many the backend accepted, and how often the
            // vanilla body still completed. The audit asked for exactly this pair, because the image comparisons could
            // never distinguish replacement from GL drawing everything.
            System.out.println("celtium offer decisions: offers=" + celtium$offers + " accepted="
                    + celtium$acceptedByBackend + " glBodyCompleted=" + celtium$glUploads);
        }
        boolean accepted = false;
        try {
            VertexFormat format = celtium$vertexFormat(buffer);
            int stride = format == null ? 0 : format.getSize();
            int vertices = buffer.getVertexCount();
            ByteBuffer data = celtium$byteBuffer(buffer);
            if (data == null || vertices <= 0) {
                return;
            }
            long address = org.lwjgl.system.MemoryUtil.memAddress(data);
            if (address == 0L) {
                return;
            }
            int formatId = celtium$formatId(format);
            // What arrives, not what is accepted. A counter nobody was watching moved when the mapping was fixed - format 1 went
            // to zero while format 6 started being taken - and the way to tell "these batches no longer arrive" from "these
            // batches arrive and are declined" is to count them here, before any decision is made.
            if (formatId >= 0 && formatId < celtium$arrivalsByFormat.length) {
                celtium$arrivalsByFormat[formatId]++;
                celtium$arrivalTotal++;
                if (celtium$arrivalTotal % 500 == 0) {
                    StringBuilder table = new StringBuilder();
                    for (int i = 0; i < celtium$arrivalsByFormat.length; i++) {
                        table.append(i).append(':').append(celtium$arrivalsByFormat[i]).append(' ');
                    }
                    System.out.println("[Celtium] CELTIUM_ARRIVALS total=" + celtium$arrivalTotal
                            + " byFormat=[" + table.toString().trim() + "]");
                }
            }
            if (formatId == 0 && celtium$nullFormatDumps < 2) {
                // Something uploads vertices with no VertexFormat at all, and the immediate path cannot take it because a
                // null format has neither a stride nor a pipeline. Printing where it comes from turns "format 0 is
                // refused" into a named call site (sky, clouds, weather and the water surface are the candidates).
                celtium$nullFormatDumps++;
                StringBuilder elements = new StringBuilder();
                for (int i = 0; i < format.getElementCount(); i++) {
                    elements.append(format.getElement(i)).append(' ');
                }
                // Both sides, in the same shape. Six rules were tried against this shape without success, and the reason may
                // simply be that nobody has seen the two formats side by side: the dump printed the unmatched format's
                // elements and never a known one to compare them with.
                System.out.println("[Celtium] CELTIUM_NULLFORMAT stride=" + stride + " vertices=" + vertices
                        + " elements=[" + elements.toString().trim() + "] formatSize=" + format.getSize()
                        + " elementCount=" + format.getElementCount()
                        + " referencePositionTex=[" + celtium$describe(DefaultVertexFormats.POSITION_TEX) + "]"
                        + " referenceBlock=[" + celtium$describe(DefaultVertexFormats.BLOCK) + "]"
                        + " stack=" + celtium$stack());
            }
            int mode = celtium$drawMode(buffer);
            // The call that decides everything. It was lost when a dead branch was cleaned out of this method, and
            // with it went the only path by which the backend could ever accept a batch: accepted stayed false, the
            // backend was never called, GL drew every batch, and the counters reported zeros while the images matched.
            accepted = backend.submitImmediateBatch(format, stride, formatId, address, vertices, mode);
            if (!accepted && formatId == 6 && celtium$positionTexRefusals < 3) {
                // The sky, the clouds and the water surface are mapped now, so a refusal here is a guard downstream of the
                // mapping. Printing the backend's own reason is what identified the composite's, and it is the same question.
                celtium$positionTexRefusals++;
                System.out.println("[Celtium] CELTIUM_POSITIONTEX_REFUSED reason=" + backend.lastRefusal()
                        + " stride=" + stride + " vertices=" + vertices);
            }
            if (!accepted && formatId == 2 && celtium$compositeRefusals < 3) {
                // The composite batch is the one the present path keys on, and three rounds of reasoning about which guard
                // stops it have produced one wrong answer already. Printing the backend's own reason replaces the reasoning.
                celtium$compositeRefusals++;
                System.out.println("[Celtium] CELTIUM_COMPOSITE_REFUSED reason=" + backend.lastRefusal()
                        + " stride=" + stride + " vertices=" + vertices);
            }
            if (formatId == 2 && celtium$compositeDumps < 1) {
                // Unconditional: this runs for every offered batch, before the backend's acceptance mask decides
                // anything, which is where a dump that must survive a mask has to live. It answers who draws the
                // full-screen composite quad, which decides where that quad belongs.
                celtium$compositeDumps++;
                StringBuilder elements = new StringBuilder();
                for (VertexFormatElement element : format.getElements()) {
                    elements.append(element.getSize()).append('b').append(element.getUsage()).append(' ');
                }
                System.out.println("celtium composite batch: elements=[" + elements.toString().trim() + "] vertices="
                        + vertices + " mode=" + mode + " stride=" + stride + " v0=(pos "
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address) + ","
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address + 4) + " uv "
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address + 12) + ","
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address + 16) + " col 0x"
                        + Integer.toHexString(org.lwjgl.system.MemoryUtil.memGetInt(address + 20)) + ")");
                StackTraceElement[] stack = new Throwable().getStackTrace();
                StringBuilder trace = new StringBuilder();
                for (int i = 1; i < Math.min(stack.length, 14); i++) {
                    trace.append(stack[i].getClassName()).append('.').append(stack[i].getMethodName()).append(':')
                            .append(stack[i].getLineNumber()).append(" <- ");
                }
                System.out.println("celtium composite caller: " + trace);
            }
        } catch (Throwable error) {
            // Never let the offer break the draw: the original body stays in charge when anything goes wrong. But say
            // what happened, once per distinct kind of failure - this catch hid the reason acceptance was zero for
            // several rounds while the counters said "refused" and nothing said why.
            if (celtium$swallowed < 3) {
                celtium$swallowed++;
                StackTraceElement[] trace = error.getStackTrace();
                String where = trace.length > 0 ? trace[0].toString() : "?";
                System.out.println("celtium offer threw: " + error + " at " + where);
            }
            return;
        }
        if (accepted) {
            celtium$acceptedByBackend++;
            // The original body resets the builder after uploading. Cancelling skips that, so it is done here; without
            // it Minecraft would re-upload the same vertices on the next draw.
            try {
                buffer.reset();
            } finally {
                info.cancel();
            }
        }
    }

    /**
     * Identifies the layout by comparing its element list with vanilla's own formats.
     * <p>
     * Class identity would fail for formats a mod builds itself, and a stride alone is ambiguous - 28 bytes is BLOCK,
     * ITEM and the particle layout, which differ in element order. The element instances are shared, so comparing them
     * is exact, and the result is cached per format instance because this runs for every batch.
     */
    private static int celtium$formatId(VertexFormat format) {
        if (format == null) {
            return 0;
        }
        Integer cached = celtium$formatIds.get(format);
        if (cached != null) {
            return cached.intValue();
        }
        int id;
        if (celtium$sameElements(format, DefaultVertexFormats.POSITION_COLOR)) {
            id = 1;
        } else if (celtium$sameElements(format, DefaultVertexFormats.POSITION_TEX_COLOR)) {
            id = 2;        } else if (celtium$sameElements(format, DefaultVertexFormats.BLOCK)) {
            id = 3;
        } else if (celtium$sameElements(format, DefaultVertexFormats.ITEM)) {
            id = 4;
        } else if (celtium$sameElements(format, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP)) {
            id = 5;
        } else if (celtium$sameElements(format, DefaultVertexFormats.POSITION_TEX)
                || format.equals(DefaultVertexFormats.POSITION_TEX)) {
            // The branch that was missing. Position+texture is the shape the sky, the clouds and the water surface upload, it is
            // byte-for-byte the standard POSITION_TEX format, and a pipeline for id 6 already existed in the backend - but this
            // chain never assigned 6, so those batches fell through to "unrecognised" and went to OpenGL. Six mapping rules
            // were tried against the shape before anyone compared it with a known format on one line and then read the chain:
            // there was nothing wrong with the comparison, and nothing for a rule to fix.
            id = 6;
        } else {
            id = 0;
        }
        if (id != 0) {
            // Only hits are cached. A miss depends on the stride this upload happens to carry, and storing it meant the
            // first encounter's verdict was reused for every later one - the second half of why the mapping appeared deaf.
            celtium$formatIds.put(format, Integer.valueOf(id));
        }
        return id;
    }

    private static boolean celtium$sameElements(VertexFormat left, VertexFormat right) {
        List<VertexFormatElement> a = left.getElements();
        List<VertexFormatElement> b = right.getElements();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            VertexFormatElement expected = a.get(i);
            VertexFormatElement actual = b.get(i);
            // By content, not by identity. This compared `left != right`, and the game builds equal element instances rather
            // than reusing the constants, so **no** format the game constructs inline ever matched a standard one - which is
            // why the sky, the clouds, the water surface and the final composite all arrived as format 0, and why six
            // candidate mapping rules appeared not to work: the element comparison in front of them was wrong.
            if (expected.getType() != actual.getType() || expected.getUsage() != actual.getUsage()
                    || expected.getIndex() != actual.getIndex()
                    || expected.getElementCount() != actual.getElementCount()) {
                return false;
            }
        }
        return true;
    }

    private static VertexFormat celtium$vertexFormat(BufferBuilder buffer) {
        try {
            return ((BufferBuilderAccessor) buffer).celtium$vertexFormat();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static ByteBuffer celtium$byteBuffer(BufferBuilder buffer) {
        try {
            return ((BufferBuilderAccessor) buffer).celtium$byteBuffer();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static int celtium$drawMode(BufferBuilder buffer) {
        try {
            return ((BufferBuilderAccessor) buffer).celtium$drawMode();
        } catch (RuntimeException | LinkageError error) {
            return -1;
        }
    }
}
