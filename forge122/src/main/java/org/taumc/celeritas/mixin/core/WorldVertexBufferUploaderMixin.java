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
    private static int celtium$positionRefusals;
    private static int celtium$compositeLayoutRefusals;
    /** Bounded prints for the layouts whose refusal used to have to be derived from a number: 10 and the unmapped case. */
    private static int celtium$positionNormalRefusals;
    private static int celtium$unmappedRefusals;
    /** Uploads seen per mapped format id, before any accept/refuse decision. */
    /** Refusals by "format reason": the family-specific prints did not cover every format. */
    private static final java.util.LinkedHashMap<String, Integer> celtium$refusalsByFormat =
            new java.util.LinkedHashMap<String, Integer>();
    private static final int[] celtium$arrivalsByFormat =
            new int[org.soutalab.celtium.vulkan.VulkanImmediateFormats.COUNT];
    private static int celtium$arrivalTotal;
    /** The distinct shapes arriving with no mapped id, counted by element signature. */
    private static final java.util.Map<String, Integer> celtium$unmappedShapes =
            new java.util.LinkedHashMap<String, Integer>();

    /** How many distinct unmapped layouts are described once each, with the standard format carrying the same list. */
    private static final int SIGNATURE_REPORTS = 8;
    private static int celtium$signatureReports;

    /**
     * {@return the element list written the way {@code VertexFormatElement.toString()} writes it}.
     * <p>
     * One element is {@code count,usage,type}, elements separated by a space - the text a reader of the log already knows
     * from the format dumps, and the text {@link org.soutalab.celtium.vulkan.VulkanImmediateFormats#layout} is keyed on. The
     * mapping used to compare element objects one at a time in a chain of branches, which is the shape that could not tell
     * the 24-byte normal layout from the 28-byte one: the list was the same and the width was never asked about.
     */
    private static String celtium$signature(VertexFormat format) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < format.getElementCount(); i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(format.getElement(i));
        }
        return text.toString();
    }

    /**
     * {@return the id of the layout, or 0 when no known layout has this element list and width}.
     * <p>
     * The width is passed with the list because two of vanilla's own formats differ in nothing else: {@code
     * POSITION_TEX_COLOR_NORMAL} is 28 bytes and {@code POSITION_TEX_NORMAL} is 24, and the second used to be answered with
     * the first's id and then refused by the backend for carrying the wrong stride.
     */
    private static int celtium$mappedId(VertexFormat format) {
        return org.soutalab.celtium.vulkan.VulkanImmediateFormats.layout(celtium$signature(format), format.getSize(),
                format.getElementCount());
    }

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
            System.out.println("[Celtium] CELTIUM_REFUSALS_BY_FORMAT " + celtium$refusalsByFormat);
        }
        boolean accepted = false;
        int celtium$decidedFormat = -1;
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
            celtium$decidedFormat = formatId;
            // Inside glNewList the geometry is being compiled into a display list rather than drawn. Recording the call site
            // then is what gives that list an identity: the id alone shifts when something else allocates first.
            int celtium$listIndex = celtium$listIndex();
            if (celtium$listIndex > 0) {
                StackTraceElement[] celtium$stack = new Throwable().getStackTrace();
                String celtium$origin = "unknown";
                // Eight frames: the first three name the machinery (uploader, Tessellator, TexturedQuad) and the next
                // three the model pipeline (ModelBox, compileDisplayList, ModelRenderer.render). The two after that name
                // the content - ItemRenderer for the hand, RenderPlayer for a player model part - which is what the
                // cancellation rule needs to tell apart, since the hand is drawn natively and the player model is not.
                int celtium$taken = 0;
                for (StackTraceElement celtium$frame : celtium$stack) {
                    String celtium$name = celtium$frame.getClassName();
                    // The injected handler lives inside Minecraft's own class, so a package test is not enough: its name carries
                    // the mixin's marker and has to be skipped explicitly (everything reported the handler before this).
                    if (celtium$name.startsWith("org.taumc.celeritas") || celtium$name.startsWith("org.soutalab.celtium")
                            || celtium$name.startsWith("org.spongepowered") || celtium$name.contains("$celeritas$")
                            || celtium$name.contains("$celtium$")
                            // The mixin's marker is in the METHOD name, not the class: the injected handler lives inside
                            // Minecraft's own WorldVertexBufferUploader and reports as a frame of that class, which is
                            // why every origin came back as the handler itself.
                            || celtium$frame.getMethodName().contains("$celeritas$")
                            || celtium$frame.getMethodName().contains("$celtium$")) {
                        continue;
                    }
                    // Three frames, not one: the immediate caller is the uploader and its caller the Tessellator, so a
                    // single frame named the machinery rather than the content. The chain is what identifies the list.
                    if (celtium$taken == 0) {
                        celtium$origin = celtium$name + '.' + celtium$frame.getMethodName();
                    } else {
                        celtium$origin = celtium$origin + " <- " + celtium$name + '.' + celtium$frame.getMethodName();
                    }
                    celtium$taken++;
                    if (celtium$taken >= 8) {
                        break;
                    }
                }
                org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originIfAbsent(celtium$listIndex, celtium$origin);
                celtium$noteCompileTexture(celtium$listIndex);
            } else if (celtium$listIndex != 0) {
                // GL_LIST_INDEX did not answer at all. That is an identity that could not be established, so it is
                // counted as unknown and kept out of the per-list census; filing it under 0 would name a list that no
                // replay can ever reach, which is what every model quad did while the stub answered 0 for everything.
                org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originUnknown(celtium$listIndex,
                        "list-index-query-failed");
            }
            // What arrives, not what is accepted. A counter nobody was watching moved when the mapping was fixed - format 1 went
            // to zero while format 6 started being taken - and the way to tell "these batches no longer arrive" from "these
            // batches arrive and are declined" is to count them here, before any decision is made.
            if (formatId >= 0 && formatId < celtium$arrivalsByFormat.length) {
                celtium$arrivalsByFormat[formatId]++;
                celtium$arrivalTotal++;
                if (formatId == 0) {
                    // The largest category still going to OpenGL, identified rather than counted: the shapes that reach the
                    // mapping and are not recognised, by their element signature. Format 0 means "no id", so the only way to
                    // name what it is made of is to describe each arrival.
                    String key = celtium$describeUnmapped(format);
                    Integer seen = celtium$unmappedShapes.get(key);
                    celtium$unmappedShapes.put(key, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
                }
                if (celtium$arrivalTotal % 500 == 0) {
                    StringBuilder table = new StringBuilder();
                    for (int i = 0; i < celtium$arrivalsByFormat.length; i++) {
                        table.append(i).append(':').append(celtium$arrivalsByFormat[i]).append(' ');
                    }
                    System.out.println("[Celtium] CELTIUM_ARRIVALS total=" + celtium$arrivalTotal
                            + " byFormat=[" + table.toString().trim() + "]");
                    for (java.util.Map.Entry<String, Integer> shape : celtium$unmappedShapes.entrySet()) {
                        System.out.println("[Celtium] CELTIUM_UNMAPPED count=" + shape.getValue()
                                + " shape=[" + shape.getKey() + "]");
                    }
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
            long batchCallStarted = System.nanoTime();
            long batchCallCpuStarted = org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.threadCpuNanos();
            try {
                accepted = backend.submitImmediateBatch(format, stride, formatId, address, vertices, mode);
                if (!accepted) {
                    // Why a batch was not taken, by format. The refusals were recorded in the backend, but only some families
                    // printed them, so a format could arrive hundreds of times, be accepted for a handful and be refused for the
                    // rest with no line saying why - measured as format 1 arriving 826 times and being accepted 26.
                    String reason = backend.lastRefusal();
                    if (reason == null || reason.isEmpty()) reason = "no reason recorded";
                    if (reason.length() > 70) reason = reason.substring(0, 70);
                    String key = celtium$decidedFormat + " " + reason;
                    Integer had = celtium$refusalsByFormat.get(key);
                    if (had != null) {
                        celtium$refusalsByFormat.put(key, Integer.valueOf(had.intValue() + 1));
                    } else if (celtium$refusalsByFormat.size() < 24) {
                        celtium$refusalsByFormat.put(key, Integer.valueOf(1));
                    } else {
                        Integer other = celtium$refusalsByFormat.get("other");
                        celtium$refusalsByFormat.put("other", Integer.valueOf(other == null ? 1 : other.intValue() + 1));
                    }
                }
            } finally {
                long batchCallTook = System.nanoTime() - batchCallStarted;
                long batchCallCpuEnded = org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.threadCpuNanos();
                if (batchCallCpuStarted >= 0 && batchCallCpuEnded >= 0) {
                    org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallCpuNanos += batchCallCpuEnded - batchCallCpuStarted;
                }
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallNanos += batchCallTook;
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallCount++;
                if (batchCallTook > org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallWorstNanos) {
                    org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallWorstNanos = batchCallTook;
                }
                if (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallOpeningCount < 200) {
                    org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallOpeningCount++;
                    org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallOpeningNanos += batchCallTook;
                }
            }
            if (!accepted && formatId == 9 && celtium$compositeLayoutRefusals < 3) {
                // The composite's own layout: mapped, in the mask, in range, with the right stride - so what refuses it is
                // further down again, and the backend will say what.
                celtium$compositeLayoutRefusals++;
                System.out.println("[Celtium] CELTIUM_COMPOSITE_LAYOUT_REFUSED reason=" + backend.lastRefusal()
                        + " stride=" + stride + " vertices=" + vertices);
            }
            if (!accepted && formatId == 7 && celtium$positionRefusals < 3) {
                // Position-only is mapped and in the acceptance mask, so a refusal is a guard further down. The same
                // instrument that named the previous two guards answers this one.
                celtium$positionRefusals++;
                System.out.println("[Celtium] CELTIUM_POSITION_REFUSED reason=" + backend.lastRefusal()
                        + " stride=" + stride + " vertices=" + vertices);
            }
            if (!accepted && formatId == org.soutalab.celtium.vulkan.VulkanImmediateFormats.POSITION_NORMAL
                    && celtium$positionNormalRefusals < 3) {
                // The white box Render.renderOffsetAABB draws for a renderer-less entity. It is mapped and in the acceptance
                // mask now, so a refusal here is the backend's own reason rather than the mapping: without this line the
                // failure mode is the one this format had for its whole life - the box simply is not in the picture and the
                // census says "id 0" or a stride, never "POSITION_NORMAL".
                celtium$positionNormalRefusals++;
                System.out.println("[Celtium] CELTIUM_POSITION_NORMAL_REFUSED format="
                        + org.soutalab.celtium.vulkan.VulkanImmediateFormats.nameOf(formatId)
                        + " reason=" + backend.lastRefusal() + " stride=" + stride + " vertices=" + vertices);
            }
            if (!accepted && formatId == 0 && celtium$unmappedRefusals < 3) {
                // The only refusal that has to be read with the format's own name, because there is no id to look up: the
                // backend refuses id 0 from the start of submitImmediateBatch, before any pipeline is chosen. The name is the
                // element list and the width, which is what a reader would otherwise re-derive by hand from the arrival
                // census - and "id 0" is what POSITION_NORMAL reported while it had no branch.
                celtium$unmappedRefusals++;
                System.out.println("[Celtium] CELTIUM_UNMAPPED_REFUSED format="
                        + org.soutalab.celtium.vulkan.VulkanImmediateFormats.nameOf(0) + " reason=" + backend.lastRefusal()
                        + " layout=[" + celtium$describeUnmapped(format) + "] vertices=" + vertices);
            }
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
     * Identifies the layout by comparing its element list and its width with vanilla's own formats.
     * <p>
     * Class identity would fail for formats a mod builds itself, and a stride alone is ambiguous - 28 bytes is BLOCK, the
     * particle layout and {@code POSITION_TEX_COLOR_NORMAL}, which differ in element order and in element count. The
     * element instances are shared, so comparing their text and their count is exact, and the result is cached per format
     * instance because this runs for every batch.
     * <p>
     * The mapping is the table in {@link org.soutalab.celtium.vulkan.VulkanImmediateFormats#layout}, which lists every
     * format {@code DefaultVertexFormats} declares with the id it has and the width it has it at. The widths there are the
     * runtime ones - vanilla's {@code VertexFormat.addElement} pads nothing, so the normal layouts are 16 and 24 bytes
     * rather than the multiples of four a stride table would guess. Two facts about that table are worth having here,
     * because both were live defects:
     * <ul>
     * <li>{@code POSITION_NORMAL} - the white box {@code Render.renderOffsetAABB} draws for an entity whose renderer is
     * missing - had no branch at all and fell to 0. It is 16 bytes (position 12, normal 3, padding 1), which is why no
     * stride-shaped rule could have found it either.</li>
     * <li>{@code POSITION_TEX_NORMAL} (24 bytes: position, texture, normal, padding) has the same element list as {@code
     * POSITION_TEX_COLOR_NORMAL} (28 bytes: position, texture, colour, normal, padding) minus its colour - so the 24-byte
     * format was answered with id 8 and refused for carrying 24 bytes where 28 were expected. The game uses it in four
     * places: the fishing line, both fireballs, the dragon fireball and the painting.</li>
     * </ul>
     */
    private static int celtium$formatId(VertexFormat format) {
        if (format == null) {
            return 0;
        }
        Integer cached = celtium$formatIds.get(format);
        if (cached != null) {
            return cached.intValue();
        }
        int id = celtium$mappedId(format);
        if (id == 0) {
            // The list and the width together did not name a layout. If one of vanilla's own constants has this element list
            // then the table and the game disagree about the width, which is a defect in this file rather than an unmapped
            // mod format - and it is exactly the shape of the id-8/id-9 error, which nothing reported while it was live.
            celtium$reportSignatureMismatch(format);
        }
        if (id != 0) {
            // Only hits are cached. A miss depends on the stride this upload happens to carry, and storing it meant the
            // first encounter's verdict was reused for every later one - the second half of why the mapping appeared deaf.
            celtium$formatIds.put(format, Integer.valueOf(id));
        }
        return id;
    }

    /**
     * {@return the text an unmapped format is filed under}, which is the element list and the width.
     * <p>
     * The width is in the key because it is part of what a layout is: two entries whose lists differ only in element order
     * would otherwise share a line, and the 24/28-byte pair is exactly that case.
     */
    private static String celtium$describeUnmapped(VertexFormat format) {
        if (format == null) {
            return "null format";
        }
        return celtium$signature(format) + " | size=" + format.getSize() + " count=" + format.getElementCount();
    }

    /**
     * Prints the once-per-process warning that a format carrying a vanilla element list was not mapped.
     * <p>
     * Bounded to {@link #SIGNATURE_REPORTS} lines: the arrival census already counts every unmapped shape, so what this adds
     * is the comparison against the standard formats - which one has this element list, and at what width - and that is the
     * same answer for every batch of a shape.
     */
    private static void celtium$reportSignatureMismatch(VertexFormat format) {
        if (celtium$signatureReports >= SIGNATURE_REPORTS) {
            return;
        }
        VertexFormat[] known = {
                DefaultVertexFormats.POSITION_COLOR, DefaultVertexFormats.POSITION_TEX_COLOR,
                DefaultVertexFormats.BLOCK, DefaultVertexFormats.ITEM,
                DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP, DefaultVertexFormats.POSITION_TEX,
                DefaultVertexFormats.POSITION, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL,
                DefaultVertexFormats.POSITION_TEX_NORMAL, DefaultVertexFormats.POSITION_NORMAL,
                DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR,
        };
        String signature = celtium$signature(format);
        StringBuilder sameList = new StringBuilder();
        for (VertexFormat candidate : known) {
            if (celtium$sameElements(format, candidate)) {
                if (sameList.length() > 0) {
                    sameList.append(", ");
                }
                sameList.append(org.soutalab.celtium.vulkan.VulkanImmediateFormats.nameOf(
                                celtium$mappedId(candidate)))
                        .append(" at ").append(candidate.getSize()).append(" bytes");
            }
        }
        celtium$signatureReports++;
        System.out.println("[Celtium] CELTIUM_UNMAPPED_LAYOUT arrivals=" + celtium$signatureReports
                + " width=" + format.getSize() + " elements=[" + signature + "]"
                + " sameElementList=[" + (sameList.length() == 0 ? "none of the standard formats" : sameList) + "]"
                + " => " + org.soutalab.celtium.vulkan.VulkanImmediateFormats.describeUnknown(signature,
                        format.getSize(), format.getElementCount()));
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

    /**
     * {@return the display list GL is compiling into, 0 when none is, and {@link Integer#MIN_VALUE} when GL did not
     * answer}.
     * <p>
     * The distinction matters under the GL-less stub: 0 is a real answer ("nothing is being compiled", which is true for
     * every immediate batch) while a failed query is an identity that could not be established. The old catch collapsed
     * both into 0, so a broken read looked exactly like the ordinary no-list case.
     */
    private static int celtium$listIndex() {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_LIST_INDEX);
        } catch (RuntimeException | LinkageError error) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Records the texture the list is being compiled under, once per list.
     * <p>
     * The same GL state the quad capture reads per quad, taken from the uploader side: the stub answers both from the
     * single {@code glBindTexture} record, so this is the compile-time half of the texture identity the replay-time read
     * in the {@code callList} hook pairs with. Read once per list - a batch arriving inside {@code glNewList} is the
     * moment the geometry is compiled, and the texture does not change between the batches of one part.
     */
    private static void celtium$noteCompileTexture(int listIndex) {
        try {
            if (org.soutalab.celtium.vulkan.VulkanDisplayListCensus.compileTextureKnown(listIndex)) {
                return;
            }
            int unit = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            org.soutalab.celtium.vulkan.VulkanDisplayListCensus.compileTexture(listIndex, texture, unit);
        } catch (RuntimeException | LinkageError error) {
            // Record the failure rather than leaving the list to be retried on every batch, and rather than reporting a
            // name of 0: the census prints it as "unknown".
            org.soutalab.celtium.vulkan.VulkanDisplayListCensus.compileTexture(listIndex, Integer.MIN_VALUE,
                    Integer.MIN_VALUE);
        }
    }
}
