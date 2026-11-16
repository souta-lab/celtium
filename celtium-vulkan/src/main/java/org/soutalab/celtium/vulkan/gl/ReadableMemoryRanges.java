package org.soutalab.celtium.vulkan.gl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.lwjgl.system.MemoryUtil;

/**
 * OS-independent range normalization, containment checks, and platform mapping providers for the client-pointer
 * fault guard in {@link CeltiumGlStub}.
 * <p>
 * This is a guard against dereferencing unmapped or uncommitted process memory, not an ownership or lifetime record.
 */
final class ReadableMemoryRanges {

    /** Half-open readable address range {@code [start, end)}. */
    record Range(long start, long end) {}

    /** Supplies snapshots of currently readable process memory ranges. */
    interface Provider {
        /** {@return whether this platform/provider can enumerate readable mappings}. */
        boolean available();

        /** Reads a fresh list of readable ranges; throws on platform probe failure. */
        Range[] read() throws Exception;
    }

    /** Abstracts Win32 {@code VirtualQuery} so the region walker and struct decoder can be unit-tested off-Windows. */
    @FunctionalInterface
    interface VirtualQueryFn {
        long query(long address, long mbiAddress, long mbiSize);
    }

    private static final int MEM_COMMIT = 0x1000;
    private static final int PAGE_READONLY = 0x02;
    private static final int PAGE_READWRITE = 0x04;
    private static final int PAGE_WRITECOPY = 0x08;
    private static final int PAGE_EXECUTE_READ = 0x20;
    private static final int PAGE_EXECUTE_READWRITE = 0x40;
    private static final int PAGE_EXECUTE_WRITECOPY = 0x80;
    private static final int PAGE_GUARD = 0x100;

    private static final int MBI_SIZE_64 = 48;
    private static final int MBI_SIZE_32 = 28;
    private static final long MAX_USER_ADDRESS_64 = 0x00080000_00000000L;
    private static final long MAX_USER_ADDRESS_32 = 0x1_0000_0000L;
    private static final int MAX_VIRTUAL_QUERY_STEPS = 1_000_000;

    private static final Range[] EMPTY = new Range[0];

    private ReadableMemoryRanges() {}

    static Provider defaultProvider() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) {
            return LinuxProcMapsProvider.INSTANCE;
        }
        if (osName.contains("win")) {
            return WindowsVirtualQueryProvider.INSTANCE;
        }
        return UnavailableProvider.INSTANCE;
    }

    /**
     * Sorts valid non-negative ranges and coalesces overlapping or contiguous adjacent ranges so a contiguous run
     * spanning multiple VMAs/regions is represented cleanly and lookup stays O(log n).
     */
    static Range[] normalize(Range[] raw) {
        if (raw == null || raw.length == 0) {
            return EMPTY;
        }
        List<Range> valid = new ArrayList<>(raw.length);
        for (Range range : raw) {
            if (range != null && range.start() >= 0L && range.end() > range.start()) {
                valid.add(range);
            }
        }
        if (valid.isEmpty()) {
            return EMPTY;
        }
        valid.sort(Comparator.comparingLong(Range::start).thenComparingLong(Range::end));
        List<Range> merged = new ArrayList<>(valid.size());
        long curStart = valid.get(0).start();
        long curEnd = valid.get(0).end();
        for (int i = 1; i < valid.size(); i++) {
            Range next = valid.get(i);
            if (next.start() <= curEnd) {
                if (next.end() > curEnd) {
                    curEnd = next.end();
                }
            } else {
                merged.add(new Range(curStart, curEnd));
                curStart = next.start();
                curEnd = next.end();
            }
        }
        merged.add(new Range(curStart, curEnd));
        return merged.toArray(EMPTY);
    }

    /**
     * {@return whether {@code [address, address + bytes)} is completely contained in {@code ranges}}, walking
     * contiguous adjacent ranges if needed. {@code ranges} must be sorted and disjoint (e.g. from {@link #normalize}).
     */
    static boolean contains(Range[] ranges, long address, long bytes) {
        if (ranges == null || ranges.length == 0 || address <= 0L || bytes <= 0L) {
            return false;
        }
        long end;
        try {
            end = Math.addExact(address, bytes);
        } catch (ArithmeticException overflow) {
            return false;
        }
        return containsBounds(ranges, address, end);
    }

    /**
     * {@return whether half-open interval {@code [start, end)} is completely covered by {@code ranges}}.
     * Requires {@code 0 < start < end} and {@code ranges} sorted without overlaps (contiguous adjacent ranges allowed).
     */
    static boolean containsBounds(Range[] ranges, long start, long end) {
        if (ranges == null || ranges.length == 0 || start <= 0L || end <= start) {
            return false;
        }
        int low = 0;
        int high = ranges.length - 1;
        int hit = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Range range = ranges[mid];
            if (start < range.start()) {
                high = mid - 1;
            } else if (start >= range.end()) {
                low = mid + 1;
            } else {
                hit = mid;
                break;
            }
        }
        if (hit < 0) {
            return false;
        }
        long covered = ranges[hit].end();
        for (int i = hit + 1; i < ranges.length && covered < end; i++) {
            Range next = ranges[i];
            if (next.start() > covered) {
                return false;
            }
            if (next.end() > covered) {
                covered = next.end();
            }
        }
        return covered >= end;
    }

    /** Parses Linux {@code /proc/self/maps} lines, keeping readable ({@code r}) mappings. */
    static Range[] parseLinuxProcMaps(BufferedReader reader) throws IOException {
        List<Range> ranges = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            // "7f0a1c000000-7f0a1c021000 rw-p 00000000 00:00 0 ..." - the permission field is the second.
            int dash = line.indexOf('-');
            int space = line.indexOf(' ');
            if (dash <= 0 || space <= dash + 1 || space + 1 >= line.length()) {
                continue;
            }
            if (line.charAt(space + 1) != 'r') {
                continue;
            }
            long start = Long.parseUnsignedLong(line.substring(0, dash).trim(), 16);
            long stop = Long.parseUnsignedLong(line.substring(dash + 1, space).trim(), 16);
            if (stop > start) {
                ranges.add(new Range(start, stop));
            }
        }
        return ranges.toArray(EMPTY);
    }

    /**
     * {@return whether a Win32 {@code MEMORY_BASIC_INFORMATION} region is committed and readable without faulting}.
     * Excludes {@code MEM_FREE}/{@code MEM_RESERVE}, {@code PAGE_NOACCESS}, {@code PAGE_EXECUTE} (execute-only),
     * and {@code PAGE_GUARD} (whose first read raises {@code STATUS_GUARD_PAGE_VIOLATION}).
     */
    static boolean isWindowsCommittedReadable(int state, int protect) {
        if (state != MEM_COMMIT) {
            return false;
        }
        if ((protect & PAGE_GUARD) != 0) {
            return false;
        }
        int baseProtect = protect & 0xFF;
        return baseProtect == PAGE_READONLY
                || baseProtect == PAGE_READWRITE
                || baseProtect == PAGE_WRITECOPY
                || baseProtect == PAGE_EXECUTE_READ
                || baseProtect == PAGE_EXECUTE_READWRITE
                || baseProtect == PAGE_EXECUTE_WRITECOPY;
    }

    /**
     * Walks the process user-mode virtual address space with {@code VirtualQuery} and returns all committed readable
     * ranges.
     */
    static Range[] collectWindowsRanges(VirtualQueryFn virtualQuery, int pointerSize) {
        if (pointerSize != 4 && pointerSize != 8) {
            throw new IllegalArgumentException("Unsupported pointer size: " + pointerSize);
        }
        int mbiSize = pointerSize == 8 ? MBI_SIZE_64 : MBI_SIZE_32;
        long maxUserAddress = pointerSize == 8 ? MAX_USER_ADDRESS_64 : MAX_USER_ADDRESS_32;
        List<Range> ranges = new ArrayList<>();
        ByteBuffer mbi = MemoryUtil.memAlloc(mbiSize);
        try {
            long mbiAddr = MemoryUtil.memAddress(mbi);
            long cursor = 0L;
            for (int step = 0; step < MAX_VIRTUAL_QUERY_STEPS && cursor >= 0L && cursor < maxUserAddress; step++) {
                long written = virtualQuery.query(cursor, mbiAddr, mbiSize);
                if (written < mbiSize) {
                    break;
                }
                long base = pointerSize == 8
                        ? MemoryUtil.memGetLong(mbiAddr)
                        : Integer.toUnsignedLong(MemoryUtil.memGetInt(mbiAddr));
                long regionSize = pointerSize == 8
                        ? MemoryUtil.memGetLong(mbiAddr + 24L)
                        : Integer.toUnsignedLong(MemoryUtil.memGetInt(mbiAddr + 12L));
                int state = MemoryUtil.memGetInt(mbiAddr + (pointerSize == 8 ? 32L : 16L));
                int protect = MemoryUtil.memGetInt(mbiAddr + (pointerSize == 8 ? 36L : 20L));
                if (base < 0L || base >= maxUserAddress || regionSize <= 0L) {
                    break;
                }
                long regionEnd = regionSize > maxUserAddress - base ? maxUserAddress : base + regionSize;
                if (isWindowsCommittedReadable(state, protect) && regionEnd > base) {
                    ranges.add(new Range(base, regionEnd));
                }
                if (regionEnd <= cursor) {
                    break;
                }
                cursor = regionEnd;
            }
        } finally {
            MemoryUtil.memFree(mbi);
        }
        return ranges.toArray(EMPTY);
    }

    private enum UnavailableProvider implements Provider {
        INSTANCE;

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Range[] read() {
            return EMPTY;
        }
    }

    private enum LinuxProcMapsProvider implements Provider {
        INSTANCE;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Range[] read() throws IOException {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream("/proc/self/maps"), StandardCharsets.US_ASCII))) {
                return parseLinuxProcMaps(reader);
            }
        }
    }

    /**
     * Windows {@code kernel32.dll!VirtualQuery} provider backed by JDK 22+ {@code java.lang.foreign} downcall handles.
     * Resolved reflectively so the module compiles cleanly with {@code options.release = 21}.
     */
    private enum WindowsVirtualQueryProvider implements Provider {
        INSTANCE;

        private static final class Binding {
            final MethodHandle virtualQuery;
            final int pointerSize;
            final Throwable failure;

            Binding(MethodHandle virtualQuery, int pointerSize, Throwable failure) {
                this.virtualQuery = virtualQuery;
                this.pointerSize = pointerSize;
                this.failure = failure;
            }
        }

        private static volatile Binding cachedBinding;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Range[] read() throws Exception {
            Binding binding = binding();
            if (binding.failure != null) {
                if (binding.failure instanceof Exception exception) {
                    throw exception;
                }
                throw new IllegalStateException("Windows VirtualQuery binding failed", binding.failure);
            }
            return collectWindowsRanges((address, mbiAddr, mbiSize) -> {
                try {
                    long ret = (long) binding.virtualQuery.invokeExact(address, mbiAddr, mbiSize);
                    return binding.pointerSize == 4 ? (ret & 0xFFFFFFFFL) : ret;
                } catch (Throwable error) {
                    throw new IllegalStateException("VirtualQuery invocation failed", error);
                }
            }, binding.pointerSize);
        }

        private static Binding binding() {
            Binding existing = cachedBinding;
            if (existing != null) {
                return existing;
            }
            synchronized (WindowsVirtualQueryProvider.class) {
                existing = cachedBinding;
                if (existing != null) {
                    return existing;
                }
                Binding created = initBinding();
                cachedBinding = created;
                return created;
            }
        }

        private static Binding initBinding() {
            try {
                Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
                Class<?> segmentClass = Class.forName("java.lang.foreign.MemorySegment");
                Class<?> linkerClass = Class.forName("java.lang.foreign.Linker");
                Class<?> lookupClass = Class.forName("java.lang.foreign.SymbolLookup");
                Class<?> layoutClass = Class.forName("java.lang.foreign.MemoryLayout");
                Class<?> valueLayoutClass = Class.forName("java.lang.foreign.ValueLayout");
                Class<?> funcDescClass = Class.forName("java.lang.foreign.FunctionDescriptor");
                Class<?> optionClass = Class.forName("java.lang.foreign.Linker$Option");

                Object arena = arenaClass.getMethod("ofAuto").invoke(null);
                Object linker = linkerClass.getMethod("nativeLinker").invoke(null);
                Object addressLayout = valueLayoutClass.getField("ADDRESS").get(null);
                int pointerSize = (int) ((Long) layoutClass.getMethod("byteSize").invoke(addressLayout)).longValue();
                if (pointerSize != 4 && pointerSize != 8) {
                    return new Binding(null, pointerSize,
                            new IllegalStateException("Unsupported native pointer size: " + pointerSize));
                }
                Object sizeTLayout = valueLayoutClass.getField(pointerSize == 8 ? "JAVA_LONG" : "JAVA_INT").get(null);

                Object lookup = lookupClass.getMethod("libraryLookup", String.class, arenaClass)
                        .invoke(null, "kernel32.dll", arena);
                Optional<?> found = (Optional<?>) lookupClass.getMethod("find", String.class)
                        .invoke(lookup, "VirtualQuery");
                if (found.isEmpty()) {
                    return new Binding(null, pointerSize,
                            new UnsatisfiedLinkError("VirtualQuery not found in kernel32.dll"));
                }
                Object symbol = found.get();

                Object argLayouts = Array.newInstance(layoutClass, 3);
                Array.set(argLayouts, 0, addressLayout);
                Array.set(argLayouts, 1, addressLayout);
                Array.set(argLayouts, 2, sizeTLayout);
                Object descriptor = funcDescClass.getMethod("of", layoutClass, argLayouts.getClass())
                        .invoke(null, sizeTLayout, argLayouts);

                Object emptyOptions = Array.newInstance(optionClass, 0);
                MethodHandle downcall = (MethodHandle) linkerClass
                        .getMethod("downcallHandle", segmentClass, funcDescClass, emptyOptions.getClass())
                        .invoke(linker, symbol, descriptor, emptyOptions);

                MethodHandles.Lookup mhLookup = MethodHandles.lookup();
                MethodHandle ofAddress = mhLookup.findStatic(segmentClass, "ofAddress",
                        MethodType.methodType(segmentClass, long.class));
                MethodHandle adapted = MethodHandles.filterArguments(downcall, 0, ofAddress, ofAddress);
                adapted = MethodHandles.explicitCastArguments(adapted,
                        MethodType.methodType(long.class, long.class, long.class, long.class));
                return new Binding(adapted, pointerSize, null);
            } catch (Throwable error) {
                return new Binding(null, 0, error);
            }
        }
    }
}
