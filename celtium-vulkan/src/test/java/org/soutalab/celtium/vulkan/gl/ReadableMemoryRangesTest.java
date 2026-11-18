package org.soutalab.celtium.vulkan.gl;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import org.soutalab.celtium.vulkan.gl.ReadableMemoryRanges.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies OS-independent readable-range math, Win32 {@code VirtualQuery} region walking/protection filtering,
 * Linux {@code /proc/self/maps} parsing, and {@link CeltiumGlStub} client-pointer acceptance/refusal through the
 * mapping-provider seam.
 */
class ReadableMemoryRangesTest {
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_QUADS = 0x0007;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_UNSIGNED_INT = 0x1405;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_NORMAL_ARRAY = 0x8075;
    private static final int GL_COLOR_ARRAY = 0x8076;
    private static final int GL_TEXTURE_COORD_ARRAY = 0x8078;

    private static final int MEM_COMMIT = 0x1000;
    private static final int MEM_RESERVE = 0x2000;
    private static final int MEM_FREE = 0x10000;
    private static final int PAGE_NOACCESS = 0x01;
    private static final int PAGE_READONLY = 0x02;
    private static final int PAGE_READWRITE = 0x04;
    private static final int PAGE_WRITECOPY = 0x08;
    private static final int PAGE_EXECUTE = 0x10;
    private static final int PAGE_EXECUTE_READ = 0x20;
    private static final int PAGE_EXECUTE_READWRITE = 0x40;
    private static final int PAGE_EXECUTE_WRITECOPY = 0x80;
    private static final int PAGE_GUARD = 0x100;
    private static final int PAGE_NOCACHE = 0x200;

    private static final class RecordingBackend
            implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
        record Submission(int stride, int formatId, int vertexCount, int mode, float firstX) {}

        final List<Submission> submissions = new ArrayList<>();

        @Override
        public String getName() {
            return "test-recording";
        }

        @Override
        public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                           CameraTransform occlusionCamera, CameraTransform camera, Object view, boolean profile) {
        }

        @Override
        public boolean submitImmediateBatch(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                            int vertexCount, int primitiveMode) {
            float firstX = MemoryUtil.memGetFloat(vertexAddress);
            this.submissions.add(new Submission(vertexStride, formatId, vertexCount, primitiveMode, firstX));
            return true;
        }

        @Override
        public void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results,
                                 Runnable graphUpdateTrigger) {
        }

        @Override
        public void updateRegions() {
        }

        @Override
        public void updateTimings() {
        }

        @Override
        public Object2LongMap<TerrainRenderPass> snapshotTimings() {
            return Object2LongMaps.emptyMap();
        }

        @Override
        public void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination) {
        }

        @Override
        public String getTransferQueueDebugString() {
            return "";
        }

        @Override
        public void delete() {
        }
    }

    private static final class StubHarness {
        final CeltiumGlStub stub;
        final Method dispatch;
        final Map<?, ?> ids;
        final Field installedField;

        StubHarness(ReadableMemoryRanges.Provider provider) throws Exception {
            this.stub = new CeltiumGlStub(provider);
            this.dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
            this.dispatch.setAccessible(true);
            Field idsField = CeltiumGlStub.class.getDeclaredField("IDS");
            idsField.setAccessible(true);
            this.ids = (Map<?, ?>) idsField.get(null);
            this.installedField = CeltiumGlStub.class.getDeclaredField("installed");
            this.installedField.setAccessible(true);
        }

        void call(String name, long... args) throws Exception {
            long[] slots = new long[16];
            System.arraycopy(args, 0, slots, 0, args.length);
            this.dispatch.invoke(this.stub, this.ids.get(name), slots);
        }
    }

    @Test
    void normalizeSortsCoalescesAdjacentAndFiltersInvalidRanges() {
        Range[] normalized = ReadableMemoryRanges.normalize(new Range[] {
                null,
                new Range(-10L, 100L),
                new Range(500L, 500L),
                new Range(600L, 500L),
                new Range(3000L, 4000L),
                new Range(1000L, 2000L),
                new Range(2000L, 2500L),
                new Range(2200L, 2600L),
                new Range(4001L, 5000L)
        });
        assertEquals(3, normalized.length);
        assertEquals(new Range(1000L, 2600L), normalized[0]);
        assertEquals(new Range(3000L, 4000L), normalized[1]);
        assertEquals(new Range(4001L, 5000L), normalized[2]);
    }

    @Test
    void containsHandlesBoundariesGapsMultiRegionWalkAndOverflow() {
        Range[] normalized = ReadableMemoryRanges.normalize(new Range[] {
                new Range(0x1000L, 0x2000L),
                new Range(0x2000L, 0x3000L),
                new Range(0x4000L, 0x5000L)
        });

        assertTrue(ReadableMemoryRanges.contains(normalized, 0x1000L, 0x2000L));
        assertTrue(ReadableMemoryRanges.contains(normalized, 0x1800L, 0x1000L));
        assertTrue(ReadableMemoryRanges.contains(normalized, 0x4000L, 0x1000L));

        assertFalse(ReadableMemoryRanges.contains(normalized, 0x0FFFL, 2L));
        assertFalse(ReadableMemoryRanges.contains(normalized, 0x2FFFL, 2L));
        assertFalse(ReadableMemoryRanges.contains(normalized, 0x3000L, 0x1000L));
        assertFalse(ReadableMemoryRanges.contains(normalized, 0x2000L, 0x2001L));
        assertFalse(ReadableMemoryRanges.contains(normalized, 0L, 16L));
        assertFalse(ReadableMemoryRanges.contains(normalized, -100L, 16L));
        assertFalse(ReadableMemoryRanges.contains(normalized, 0x1000L, 0L));
        assertFalse(ReadableMemoryRanges.contains(normalized, 0x1000L, -4L));
        assertFalse(ReadableMemoryRanges.contains(normalized, Long.MAX_VALUE - 8L, 16L));

        // Even without pre-coalescing, sorted contiguous ranges must walk cleanly across region boundaries.
        Range[] uncoalesced = new Range[] {
                new Range(0x1000L, 0x2000L),
                new Range(0x2000L, 0x3000L),
                new Range(0x3000L, 0x3800L),
                new Range(0x3801L, 0x4000L)
        };
        assertTrue(ReadableMemoryRanges.containsBounds(uncoalesced, 0x1800L, 0x3800L));
        assertFalse(ReadableMemoryRanges.containsBounds(uncoalesced, 0x1800L, 0x3801L));
        assertFalse(ReadableMemoryRanges.containsBounds(uncoalesced, 0x3800L, 0x3802L));
    }

    @Test
    void windowsProtectionFilterRequiresCommittedNonGuardReadableMemory() {
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_READONLY));
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_READWRITE));
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_WRITECOPY));
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_EXECUTE_READ));
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_EXECUTE_READWRITE));
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_EXECUTE_WRITECOPY));
        assertTrue(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_READWRITE | PAGE_NOCACHE));

        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_RESERVE, PAGE_READWRITE));
        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_FREE, PAGE_READWRITE));
        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_NOACCESS));
        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_EXECUTE));
        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_READWRITE | PAGE_GUARD));
        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, PAGE_READONLY | PAGE_GUARD));
        assertFalse(ReadableMemoryRanges.isWindowsCommittedReadable(MEM_COMMIT, 0));
    }

    @Test
    void windowsVirtualQueryWalkerCollectsAndCoalesces64BitAnd32BitRegions() {
        record MbiEntry(long base, long size, int state, int protect) {}
        List<MbiEntry> layout = List.of(
                new MbiEntry(0x00000L, 0x10000L, MEM_FREE, PAGE_NOACCESS),
                new MbiEntry(0x10000L, 0x04000L, MEM_COMMIT, PAGE_READONLY),
                new MbiEntry(0x14000L, 0x04000L, MEM_COMMIT, PAGE_READWRITE),
                new MbiEntry(0x18000L, 0x01000L, MEM_COMMIT, PAGE_READWRITE | PAGE_GUARD),
                new MbiEntry(0x19000L, 0x03000L, MEM_RESERVE, PAGE_READWRITE),
                new MbiEntry(0x1C000L, 0x02000L, MEM_COMMIT, PAGE_EXECUTE_READ),
                new MbiEntry(0x1E000L, 0x01000L, MEM_COMMIT, PAGE_EXECUTE)
        );

        ReadableMemoryRanges.VirtualQueryFn query64 = (address, mbiAddr, mbiSize) -> {
            for (MbiEntry entry : layout) {
                if (address >= entry.base() && address < entry.base() + entry.size()) {
                    MemoryUtil.memPutLong(mbiAddr, entry.base());
                    MemoryUtil.memPutLong(mbiAddr + 24L, entry.size());
                    MemoryUtil.memPutInt(mbiAddr + 32L, entry.state());
                    MemoryUtil.memPutInt(mbiAddr + 36L, entry.protect());
                    return mbiSize;
                }
            }
            return 0L;
        };

        Range[] collected64 = ReadableMemoryRanges.normalize(ReadableMemoryRanges.collectWindowsRanges(query64, 8));
        assertEquals(2, collected64.length);
        assertEquals(new Range(0x10000L, 0x18000L), collected64[0]);
        assertEquals(new Range(0x1C000L, 0x1E000L), collected64[1]);
        assertTrue(ReadableMemoryRanges.contains(collected64, 0x13800L, 0x1000L));
        assertFalse(ReadableMemoryRanges.contains(collected64, 0x17000L, 0x2000L));

        ReadableMemoryRanges.VirtualQueryFn query32 = (address, mbiAddr, mbiSize) -> {
            for (MbiEntry entry : layout) {
                if (address >= entry.base() && address < entry.base() + entry.size()) {
                    MemoryUtil.memPutInt(mbiAddr, (int) entry.base());
                    MemoryUtil.memPutInt(mbiAddr + 12L, (int) entry.size());
                    MemoryUtil.memPutInt(mbiAddr + 16L, entry.state());
                    MemoryUtil.memPutInt(mbiAddr + 20L, entry.protect());
                    return mbiSize;
                }
            }
            return 0L;
        };

        Range[] collected32 = ReadableMemoryRanges.normalize(ReadableMemoryRanges.collectWindowsRanges(query32, 4));
        assertEquals(2, collected32.length);
        assertEquals(new Range(0x10000L, 0x18000L), collected32[0]);
        assertEquals(new Range(0x1C000L, 0x1E000L), collected32[1]);
    }

    private static final int VM_PROT_NONE = 0x0;
    private static final int VM_PROT_READ = 0x1;
    private static final int VM_PROT_WRITE = 0x2;
    private static final int VM_PROT_EXECUTE = 0x4;
    private static final int KERN_INVALID_ADDRESS = 1;

    @Test
    void macProtectionFilterRequiresReadPermission() {
        assertTrue(ReadableMemoryRanges.isMacReadable(VM_PROT_READ));
        assertTrue(ReadableMemoryRanges.isMacReadable(VM_PROT_READ | VM_PROT_WRITE));
        assertTrue(ReadableMemoryRanges.isMacReadable(VM_PROT_READ | VM_PROT_EXECUTE));

        assertFalse(ReadableMemoryRanges.isMacReadable(VM_PROT_NONE));
        assertFalse(ReadableMemoryRanges.isMacReadable(VM_PROT_WRITE));
        assertFalse(ReadableMemoryRanges.isMacReadable(VM_PROT_EXECUTE));
        assertFalse(ReadableMemoryRanges.isMacReadable(VM_PROT_WRITE | VM_PROT_EXECUTE));
    }

    @Test
    void macMachVmRegionWalkerCollectsReadableRegionsOnly() {
        record RegionEntry(long base, long size, int protection) {}
        List<RegionEntry> layout = List.of(
                new RegionEntry(0x00000L, 0x4000L, VM_PROT_READ | VM_PROT_EXECUTE),
                new RegionEntry(0x08000L, 0x2000L, VM_PROT_NONE),
                new RegionEntry(0x0A000L, 0x2000L, VM_PROT_READ | VM_PROT_WRITE),
                new RegionEntry(0x0C000L, 0x1000L, VM_PROT_WRITE),
                new RegionEntry(0x0D000L, 0x1000L, VM_PROT_READ)
        );

        // mach_vm_region semantics: yields the region containing or following the search address.
        ReadableMemoryRanges.MachVmRegionFn region = (addressInOut, sizeOut, protectionOut) -> {
            for (RegionEntry entry : layout) {
                if (addressInOut[0] < entry.base() + entry.size()) {
                    addressInOut[0] = entry.base();
                    sizeOut[0] = entry.size();
                    protectionOut[0] = entry.protection();
                    return 0;
                }
            }
            return KERN_INVALID_ADDRESS;
        };

        Range[] collected = ReadableMemoryRanges.normalize(ReadableMemoryRanges.collectMacRanges(region));
        assertEquals(3, collected.length);
        assertEquals(new Range(0x00000L, 0x04000L), collected[0]);
        assertEquals(new Range(0x0A000L, 0x0C000L), collected[1]);
        assertEquals(new Range(0x0D000L, 0x0E000L), collected[2]);
        assertTrue(ReadableMemoryRanges.contains(collected, 0x0B000L, 0x1000L));
        assertFalse(ReadableMemoryRanges.contains(collected, 0x08000L, 0x1000L));
        assertFalse(ReadableMemoryRanges.contains(collected, 0x0C000L, 0x1000L));
    }

    @Test
    void macMachVmRegionWalkerTerminatesOnFailureAndStuckProbes() {
        ReadableMemoryRanges.MachVmRegionFn failing = (addressInOut, sizeOut, protectionOut) -> KERN_INVALID_ADDRESS;
        assertEquals(0, ReadableMemoryRanges.collectMacRanges(failing).length);

        // A probe that keeps returning the same region must not loop forever; the duplicate it
        // yields before the stuck check fires is coalesced by normalize, as with the Windows walker.
        ReadableMemoryRanges.MachVmRegionFn stuck = (addressInOut, sizeOut, protectionOut) -> {
            addressInOut[0] = 0x1000L;
            sizeOut[0] = 0x1000L;
            protectionOut[0] = VM_PROT_READ;
            return 0;
        };
        Range[] collected = ReadableMemoryRanges.normalize(ReadableMemoryRanges.collectMacRanges(stuck));
        assertEquals(1, collected.length);
        assertEquals(new Range(0x1000L, 0x2000L), collected[0]);
    }

    @Test
    void linuxProcMapsParserKeepsReadableMappingsOnly() throws Exception {
        String maps = """
                00400000-00452000 r-xp 00000000 08:01 123 /bin/java
                00652000-00653000 ---p 00052000 08:01 123 /bin/java
                00653000-00654000 rw-p 00053000 08:01 123 [heap]
                malformed-line
                00700000-00700000 rw-p 00000000 00:00 0
                """;
        Range[] parsed = ReadableMemoryRanges.parseLinuxProcMaps(new BufferedReader(new StringReader(maps)));
        assertEquals(2, parsed.length);
        assertEquals(new Range(0x00400000L, 0x00452000L), parsed[0]);
        assertEquals(new Range(0x00653000L, 0x00654000L), parsed[1]);
    }

    @Test
    void stubAcceptsClientDrawArraysWhenProviderCoversRangeAndRefusesOtherwise() throws Exception {
        ByteBuffer vertices = MemoryUtil.memAlloc(24 * 24);
        long base = MemoryUtil.memAddress(vertices, 0);
        MemoryUtil.memPutFloat(base, 42.5f);
        long splitPoint = base + 256L;
        long totalBytes = 24L * 24L;

        AtomicInteger providerCalls = new AtomicInteger();
        Range[][] currentAnswer = new Range[][] {
                new Range[] { new Range(base, splitPoint), new Range(splitPoint, base + totalBytes) }
        };
        ReadableMemoryRanges.Provider provider = new ReadableMemoryRanges.Provider() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Range[] read() {
                providerCalls.incrementAndGet();
                return currentAnswer[0];
            }
        };

        StubHarness harness = new StubHarness(provider);
        Object previousStub = harness.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        harness.installedField.set(null, harness.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);
        try {
            NativeGeometryBuffer.unbind();
            harness.call("glEnableClientState", GL_VERTEX_ARRAY);
            harness.call("glEnableClientState", GL_NORMAL_ARRAY);
            harness.call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);
            harness.call("glVertexPointer", 3, GL_FLOAT, 24, base);

            // First draw spans two contiguous readable regions -> accepted and cached.
            CeltiumGlStub.celtium$drawArrays(GL_TRIANGLES, 0, 24);
            // Second draw hits cache with zero extra provider calls.
            CeltiumGlStub.celtium$drawArrays(GL_TRIANGLES, 0, 24);

            assertEquals(2, backend.submissions.size());
            assertEquals(9, backend.submissions.get(0).formatId());
            assertEquals(24, backend.submissions.get(0).stride());
            assertEquals(42.5f, backend.submissions.get(0).firstX());
            assertEquals(1, providerCalls.get(), "cached readable ranges must avoid per-draw provider queries");

            // A fresh stub whose provider leaves a 1-byte gap or returns unavailable must refuse both draws.
            StubHarness gappedHarness = new StubHarness(new ReadableMemoryRanges.Provider() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public Range[] read() {
                    providerCalls.incrementAndGet();
                    return new Range[] {
                            new Range(base, splitPoint),
                            new Range(splitPoint + 1L, base + totalBytes)
                    };
                }
            });
            gappedHarness.installedField.set(null, gappedHarness.stub);
            gappedHarness.call("glEnableClientState", GL_VERTEX_ARRAY);
            gappedHarness.call("glEnableClientState", GL_NORMAL_ARRAY);
            gappedHarness.call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);
            gappedHarness.call("glVertexPointer", 3, GL_FLOAT, 24, base);

            CeltiumGlStub.celtium$drawArrays(GL_TRIANGLES, 0, 24);
            CeltiumGlStub.celtium$drawArrays(GL_TRIANGLES, 0, 24);
            assertEquals(2, backend.submissions.size(), "gapped range must be refused");
            assertEquals(2, providerCalls.get(), "cooldown must throttle repeated misses within 100ms");

            StubHarness unavailableHarness = new StubHarness(new ReadableMemoryRanges.Provider() {
                @Override
                public boolean available() {
                    return false;
                }

                @Override
                public Range[] read() {
                    throw new AssertionError("unavailable provider must not be read");
                }
            });
            unavailableHarness.installedField.set(null, unavailableHarness.stub);
            unavailableHarness.call("glEnableClientState", GL_VERTEX_ARRAY);
            unavailableHarness.call("glEnableClientState", GL_NORMAL_ARRAY);
            unavailableHarness.call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);
            unavailableHarness.call("glVertexPointer", 3, GL_FLOAT, 24, base);
            CeltiumGlStub.celtium$drawArrays(GL_TRIANGLES, 0, 24);
            assertEquals(2, backend.submissions.size(), "unavailable provider must refuse client draw");
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            harness.installedField.set(null, previousStub);
            MemoryUtil.memFree(vertices);
        }
    }

    @Test
    void stubAppliesMappingGuardToClientDrawElementsAndSplitArrays() throws Exception {
        ByteBuffer vertices = MemoryUtil.memAlloc(3 * 12);
        ByteBuffer indices = MemoryUtil.memAlloc(3 * 4);
        ByteBuffer splitBuf = MemoryUtil.memAlloc(4 * 48);
        ByteBuffer texBuf = MemoryUtil.memAlloc(4 * 8);
        ByteBuffer colorBuf = MemoryUtil.memAlloc(4 * 4);

        long vAddr = MemoryUtil.memAddress(vertices, 0);
        long iAddr = MemoryUtil.memAddress(indices, 0);
        long sAddr = MemoryUtil.memAddress(splitBuf, 0);
        long tAddr = MemoryUtil.memAddress(texBuf, 0);
        long cAddr = MemoryUtil.memAddress(colorBuf, 0);

        MemoryUtil.memPutFloat(vAddr, 3.0f);
        indices.putInt(0, 0).putInt(4, 1).putInt(8, 2);
        MemoryUtil.memPutFloat(sAddr, 9.0f);

        Range[] allExceptColor = new Range[] {
                new Range(vAddr, vAddr + vertices.capacity()),
                new Range(iAddr, iAddr + indices.capacity()),
                new Range(sAddr, sAddr + splitBuf.capacity()),
                new Range(tAddr, tAddr + texBuf.capacity())
        };
        Range[] onlyVertices = new Range[] {
                new Range(vAddr, vAddr + vertices.capacity())
        };
        Range[][] active = new Range[][] { allExceptColor };

        ReadableMemoryRanges.Provider provider = new ReadableMemoryRanges.Provider() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Range[] read() {
                return active[0];
            }
        };

        StubHarness harness = new StubHarness(provider);
        Object previousStub = harness.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        harness.installedField.set(null, harness.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);
        try {
            NativeGeometryBuffer.unbind();
            harness.call("glEnableClientState", GL_VERTEX_ARRAY);
            harness.call("glVertexPointer", 3, GL_FLOAT, 12, vAddr);

            // Client indices + client vertices both covered -> glDrawElements succeeds.
            CeltiumGlStub.celtium$drawElements(GL_TRIANGLES, 3, GL_UNSIGNED_INT, iAddr);
            assertEquals(1, backend.submissions.size());
            assertEquals(3.0f, backend.submissions.get(0).firstX());

            // Split arrays: color array is NOT covered yet -> refused!
            harness.call("glEnableClientState", GL_NORMAL_ARRAY);
            harness.call("glEnableClientState", GL_COLOR_ARRAY);
            harness.call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);
            harness.call("glVertexPointer", 3, GL_FLOAT, 48, sAddr);
            CeltiumGlStub.celtium$texCoordPointer(2, GL_FLOAT, 8, tAddr);
            CeltiumGlStub.celtium$colorPointer(4, GL_UNSIGNED_BYTE, 4, cAddr);
            CeltiumGlStub.celtium$drawArrays(GL_QUADS, 0, 4);
            assertEquals(1, backend.submissions.size(), "split-array draw with uncovered color pointer must be refused");

            // Fresh stub with all split arrays covered -> accepted!
            StubHarness fullHarness = new StubHarness(new ReadableMemoryRanges.Provider() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public Range[] read() {
                    return new Range[] {
                            new Range(sAddr, sAddr + splitBuf.capacity()),
                            new Range(tAddr, tAddr + texBuf.capacity()),
                            new Range(cAddr, cAddr + colorBuf.capacity())
                    };
                }
            });
            fullHarness.installedField.set(null, fullHarness.stub);
            fullHarness.call("glEnableClientState", GL_VERTEX_ARRAY);
            fullHarness.call("glEnableClientState", GL_NORMAL_ARRAY);
            fullHarness.call("glEnableClientState", GL_COLOR_ARRAY);
            fullHarness.call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);
            fullHarness.call("glVertexPointer", 3, GL_FLOAT, 48, sAddr);
            CeltiumGlStub.celtium$texCoordPointer(2, GL_FLOAT, 8, tAddr);
            CeltiumGlStub.celtium$colorPointer(4, GL_UNSIGNED_BYTE, 4, cAddr);
            CeltiumGlStub.celtium$drawArrays(GL_QUADS, 0, 4);
            assertEquals(2, backend.submissions.size());
            assertEquals(9.0f, backend.submissions.get(1).firstX());

            // Fresh stub with only vertices covered and client indices uncovered -> glDrawElements refused.
            StubHarness missingIndicesHarness = new StubHarness(new ReadableMemoryRanges.Provider() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public Range[] read() {
                    return onlyVertices;
                }
            });
            missingIndicesHarness.installedField.set(null, missingIndicesHarness.stub);
            missingIndicesHarness.call("glEnableClientState", GL_VERTEX_ARRAY);
            missingIndicesHarness.call("glVertexPointer", 3, GL_FLOAT, 12, vAddr);
            CeltiumGlStub.celtium$drawElements(GL_TRIANGLES, 3, GL_UNSIGNED_INT, iAddr);
            assertEquals(2, backend.submissions.size(), "glDrawElements with uncovered client indices must be refused");
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            harness.installedField.set(null, previousStub);
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(splitBuf);
            MemoryUtil.memFree(texBuf);
            MemoryUtil.memFree(colorBuf);
        }
    }
}
