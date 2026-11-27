package org.soutalab.celtium.vulkan.lane2;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The K6 gate of the BufferBuilder FFM bridge (design V040-FINAL-PLAN sections 4.2 and 5.4):
 * the non-terrain lane reads a finished {@code BufferBuilder}'s vertex bytes in place -
 * zero copies - through the accessor view of its private buffer. That is only sound while
 * the buffer the accessor sees is the buffer the vertices were actually written into. A mod
 * that breaks {@code BufferBuilder} by reflection (swapping the backing buffer, resetting
 * its fields, or subclassing it with its own shadow storage) silently invalidates the view,
 * so the bridge probes the shape once per runtime class and falls back to the one-copy path
 * for any class whose shape does not check out.
 *
 * <p>The probe is behavioural and name-free on purpose: field <em>names</em> differ between
 * the development and production mappings, so nothing here looks a name up. What it checks:
 * <ul>
 *   <li><b>Shadow storage</b> (per class, cached): a subclass of the vanilla builder that
 *       declares its own {@link ByteBuffer} field keeps vertex bytes where the accessor -
 *       bound to the vanilla field - cannot see them. Detected by field <em>type</em>,
 *       walking the hierarchy below the vanilla class.</li>
 *   <li><b>Accounting</b> (per offer): the accessor's buffer exists, is direct (the FFM
 *       address read requires it), and its capacity covers the accounted vertex bytes
 *       ({@code vertexCount * stride}); the format view exists and the draw mode is a GL
 *       mode. A buffer too small for the accounting is a swapped or reset buffer.</li>
 * </ul>
 *
 * <p>Any failed check decides {@link CopyPath#ONE_COPY_SIMD} for that offer (and, for the
 * class-level shadow check, for the class). The decision and its reason are counted, so a
 * leg can read the K6 verdict off the counters instead of trusting that the fallback would
 * have worked.
 */
public final class BufferBuilderIntegrityProbe {

    /** Which way a finished batch's bytes may be read. */
    public enum CopyPath {
        /** Read the builder's buffer in place through its memory address. */
        ZERO_COPY,
        /** Copy the accounted bytes once into scratch and read the copy. */
        ONE_COPY_SIMD
    }

    /** Reason bits of a {@link #inspect} verdict; zero means every check passed. */
    public static final int REASON_SHADOW_STORAGE = 1;
    public static final int REASON_NO_BUFFER = 2;
    public static final int REASON_BUFFER_NOT_DIRECT = 4;
    public static final int REASON_BUFFER_TOO_SMALL = 8;
    public static final int REASON_NO_FORMAT = 16;
    public static final int REASON_BAD_DRAW_MODE = 32;

    private static final ConcurrentHashMap<Class<?>, Boolean> SHADOW_BY_CLASS =
            new ConcurrentHashMap<>();

    private static long zeroCopyDecisions;
    private static long oneCopyDecisions;

    private BufferBuilderIntegrityProbe() {
    }

    /**
     * Whether {@code runtimeClass} (a {@code BufferBuilder} or subclass) declares its own
     * {@link ByteBuffer} field below {@code vanillaClass}. The vanilla class's own field is
     * the accessor's field and is not a shadow. Cached per class.
     */
    public static boolean hasShadowStorage(Class<?> runtimeClass, Class<?> vanillaClass) {
        return SHADOW_BY_CLASS.computeIfAbsent(runtimeClass,
                type -> scanForShadow(type, vanillaClass));
    }

    private static boolean scanForShadow(Class<?> runtimeClass, Class<?> vanillaClass) {
        for (Class<?> type = runtimeClass;
             type != null && type != vanillaClass && type != Object.class;
             type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (field.getType() == ByteBuffer.class) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The full probe for one finished batch.
     *
     * @param shadowStorage {@link #hasShadowStorage} for the builder's runtime class
     * @param buffer the accessor's buffer view, or {@code null}
     * @param vertexCount the batch's accounted vertices
     * @param stride the vertex format's byte width
     * @param formatPresent whether the accessor's format view exists
     * @param drawMode the accessor's draw mode (a GL primitive mode, 0..7)
     * @return the reason bitmask; zero decides {@link CopyPath#ZERO_COPY}
     */
    public static int inspectReasons(boolean shadowStorage, ByteBuffer buffer,
                                     int vertexCount, int stride,
                                     boolean formatPresent, int drawMode) {
        int reasons = 0;
        if (shadowStorage) {
            reasons |= REASON_SHADOW_STORAGE;
        }
        if (buffer == null) {
            reasons |= REASON_NO_BUFFER;
        } else {
            if (!buffer.isDirect()) {
                reasons |= REASON_BUFFER_NOT_DIRECT;
            }
            long accounted = (long) vertexCount * (long) stride;
            if (vertexCount > 0 && buffer.capacity() < accounted) {
                reasons |= REASON_BUFFER_TOO_SMALL;
            }
        }
        if (!formatPresent) {
            reasons |= REASON_NO_FORMAT;
        }
        if (drawMode < 0 || drawMode > 7) {
            reasons |= REASON_BAD_DRAW_MODE;
        }
        return reasons;
    }

    /** The copy path one finished batch must take, from its probe inputs. */
    public static CopyPath decide(boolean shadowStorage, ByteBuffer buffer,
                                  int vertexCount, int stride,
                                  boolean formatPresent, int drawMode) {
        int reasons = inspectReasons(shadowStorage, buffer, vertexCount, stride,
                formatPresent, drawMode);
        if (reasons == 0) {
            zeroCopyDecisions++;
            return CopyPath.ZERO_COPY;
        }
        oneCopyDecisions++;
        return CopyPath.ONE_COPY_SIMD;
    }

    /** Offers decided zero-copy since the counters started (process lifetime). */
    public static long zeroCopyDecisions() {
        return zeroCopyDecisions;
    }

    /** Offers diverted to the one-copy path - the K6 breakage count. */
    public static long oneCopyDecisions() {
        return oneCopyDecisions;
    }
}
