package org.taumc.celeritas.lwjgl;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * LWJGLService for tests without a GL context: real memory through Unsafe, and buffer calls recorded in
 * {@link #LOG} instead of reaching GL. Any other GL call throws.
 * <p>
 * Taken in upstream's shape (item 398, from item 337's audit of upstream's "Reuse the indirect command
 * buffer when a region batch is rebuilt"): upstream added this fake so batch-cache behaviour could be
 * unit-tested without a GL context, wired in by putting {@code lwjglCommon} on the test classpath and
 * setting {@code -Dceleritas.lwjglService} for the {@code test} task in {@code common/build.gradle}.
 * Celtium's {@link LWJGLService} has a few more memory entry points than the upstream revision this was
 * copied from, so those are served from Unsafe as well; everything else keeps upstream's contract.
 */
public final class FakeGlLWJGLService {
    private static final Unsafe UNSAFE = unsafe();

    /** Buffer calls as "gen 1", "data 1", "del 1". */
    public static final List<String> LOG = new ArrayList<>();

    private static int nextName = 1;
    private static int boundBuffer;

    private FakeGlLWJGLService() {
    }

    public static void reset() {
        LOG.clear();
        nextName = 1;
    }

    public static LWJGLService create() {
        return (LWJGLService) Proxy.newProxyInstance(LWJGLService.class.getClassLoader(),
                new Class<?>[] { LWJGLService.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isDegraded" -> false;
                    case "isOpenGLVersionSupported" -> true;
                    case "isExtensionSupported" -> false;
                    case "getPointerSize" -> 8;
                    case "nmemAlloc" -> UNSAFE.allocateMemory((long) args[0]);
                    case "nmemCalloc" -> {
                        long bytes = (long) args[0] * (long) args[1];
                        long address = UNSAFE.allocateMemory(bytes);
                        UNSAFE.setMemory(address, bytes, (byte) 0);
                        yield address;
                    }
                    case "nmemAlignedAlloc" -> UNSAFE.allocateMemory((long) args[args.length - 1]);
                    case "nmemRealloc" -> UNSAFE.reallocateMemory((long) args[0], (long) args[1]);
                    case "nmemFree", "nmemAlignedFree" -> {
                        UNSAFE.freeMemory((long) args[0]);
                        yield null;
                    }
                    case "memSet" -> {
                        UNSAFE.setMemory((long) args[0], (long) args[2], (byte) (int) args[1]);
                        yield null;
                    }
                    case "memPutByte" -> {
                        UNSAFE.putByte((long) args[0], (byte) args[1]);
                        yield null;
                    }
                    case "memPutShort" -> {
                        UNSAFE.putShort((long) args[0], (short) args[1]);
                        yield null;
                    }
                    case "memPutInt" -> {
                        UNSAFE.putInt((long) args[0], (int) args[1]);
                        yield null;
                    }
                    case "memPutFloat" -> {
                        UNSAFE.putFloat((long) args[0], (float) args[1]);
                        yield null;
                    }
                    case "memPutLong", "memPutAddress" -> {
                        UNSAFE.putLong((long) args[0], (long) args[1]);
                        yield null;
                    }
                    case "memGetByte" -> UNSAFE.getByte((long) args[0]);
                    case "memGetShort" -> UNSAFE.getShort((long) args[0]);
                    case "memGetInt" -> UNSAFE.getInt((long) args[0]);
                    case "memGetFloat" -> UNSAFE.getFloat((long) args[0]);
                    case "memGetLong", "memGetAddress" -> UNSAFE.getLong((long) args[0]);
                    case "glGenBuffers" -> {
                        int name = nextName++;
                        LOG.add("gen " + name);
                        yield name;
                    }
                    case "glDeleteBuffers" -> {
                        LOG.add("del " + args[0]);
                        yield null;
                    }
                    case "glBindBuffer" -> {
                        boundBuffer = (int) args[1];
                        yield null;
                    }
                    case "glBufferData" -> {
                        LOG.add("data " + boundBuffer);
                        yield null;
                    }
                    case "memByteBuffer" -> byteBufferAt((long) args[0], (int) args[1]);
                    case "memAddress" -> addressOf((java.nio.Buffer) args[0])
                            + (args.length > 1 ? (int) args[1] : ((java.nio.Buffer) args[0]).position());
                    case "memCopy" -> {
                        if (args[0] instanceof Number) {
                            UNSAFE.copyMemory((long) args[0], (long) args[1], (long) args[2]);
                        } else {
                            java.nio.ByteBuffer src = (java.nio.ByteBuffer) args[0];
                            java.nio.ByteBuffer dst = (java.nio.ByteBuffer) args[1];
                            UNSAFE.copyMemory(addressOf(src) + src.position(), addressOf(dst) + dst.position(),
                                    src.remaining());
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeGlLWJGLService";
                    default -> throw new UnsupportedOperationException("FakeGlLWJGLService: " + method.getName());
                });
    }

    /**
     * A direct buffer over memory this fake allocated, built the only way available without LWJGL on the
     * classpath: an uninitialised {@code DirectByteBuffer} whose {@code Buffer} fields are set through Unsafe.
     * The memory is not registered with a cleaner - it belongs to the {@code NativeBuffer} (or the test) that
     * allocated it, and that owner frees it. Item 730's deferred-copy tests read and write through this view,
     * which is what the fake's memory cases exist for.
     */
    private static java.nio.ByteBuffer byteBufferAt(long address, int capacity) {
        try {
            Class<?> direct = Class.forName("java.nio.DirectByteBuffer");
            Object buffer = UNSAFE.allocateInstance(direct);
            Class<?> base = Class.forName("java.nio.Buffer");
            UNSAFE.putLong(buffer, UNSAFE.objectFieldOffset(base.getDeclaredField("address")), address);
            UNSAFE.putInt(buffer, UNSAFE.objectFieldOffset(base.getDeclaredField("capacity")), capacity);
            UNSAFE.putInt(buffer, UNSAFE.objectFieldOffset(base.getDeclaredField("limit")), capacity);
            UNSAFE.putInt(buffer, UNSAFE.objectFieldOffset(base.getDeclaredField("position")), 0);
            UNSAFE.putInt(buffer, UNSAFE.objectFieldOffset(base.getDeclaredField("mark")), -1);
            return (java.nio.ByteBuffer) buffer;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** The native address a direct buffer wraps, read from the {@code Buffer} field through Unsafe. */
    private static long addressOf(java.nio.Buffer buffer) {
        try {
            Class<?> base = Class.forName("java.nio.Buffer");
            return UNSAFE.getLong(buffer, UNSAFE.objectFieldOffset(base.getDeclaredField("address")));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
