package org.soutalab.celtium.vulkan.hal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Caller-by-size-class histogram of one host-visible {@link SlabAllocator}'s allocations: who asked for
 * memory, in which page-rounded size class, how many of those allocations are still live and how many were
 * ever made. Counting only - this class never influences placement, release or collection; it exists so a
 * slab pool exhaustion can be attributed from the memory summary alone.
 * <p>
 * <b>Why:</b> the Stage 3+4 integration tree exhausted both host-visible ledgers in a single burst at the
 * title transition (ordinary 13,505 live, fixed-page 8,192 live), and the existing census could not say
 * which caller held the fixed-page class: the pool summary aggregates by ledger, and the allocation site
 * census counts {@code vkAllocateMemory} calls, of which a slab pool makes only a handful. The three callers
 * that feed the host-visible pools are {@code VulkanHostBuffer.create} ({@link #SITE_HOST_BUFFER}),
 * {@code VulkanUniformBuffer.create} ({@link #SITE_UNIFORM_BUFFER}) and
 * {@code VulkanGlTexture.ensureStaging} ({@link #SITE_TEXTURE_STAGING}); every other allocation through a
 * host-visible allocator lands in {@link #SITE_UNKNOWN}.
 * <p>
 * <b>Size classes:</b> allocations are classed by their page-rounded (4096) size, the size the ledgers
 * actually reserve. Class {@link #FIXED_PAGE_CLASS} is exactly the fixed-page class - a page-rounded size of
 * exactly {@link FixedPagePool#FIXED_PAGE_SIZE_BYTES}, i.e. raw requests in (61,440, 65,536] - and for that
 * class the histogram also tracks how many of the live allocations the fixed-page ledger itself served, as
 * against the ones that fell back to the ordinary ledger when the fixed pool was full or could not honour
 * the alignment.
 * <p>
 * <b>Live semantics:</b> an allocation counts as live from {@link #noteAllocated} until the collection that
 * follows its release passes the release's timeline value: {@link #noteReleased} only records the pending
 * release, and {@link #collectCompleted(long)} settles it. That matches the ledgers' own
 * {@code liveAllocationCount} (which includes allocations pending a deferred release), so the histogram's
 * live total for an allocator reconciles with the sum of its ledgers' live counts plus its live oversized
 * allocations, and a burst of allocations that were released but not yet collected still shows as held -
 * which is the state a refusal actually happens in.
 * <p>
 * <b>Threading:</b> render thread only, like the owning allocator; no synchronisation.
 */
public final class SlabAllocationHistogram {

    /** Allocations whose caller did not name a site (every caller except the three host-visible feeders). */
    public static final int SITE_UNKNOWN = 0;
    /** {@code VulkanHostBuffer.create}: the per-buffer staging/mirror population. */
    public static final int SITE_HOST_BUFFER = 1;
    /** {@code VulkanUniformBuffer.create}: per-pipeline/program uniform buffers. */
    public static final int SITE_UNIFORM_BUFFER = 2;
    /** {@code VulkanGlTexture.ensureStaging}: texture upload staging buffers. */
    public static final int SITE_TEXTURE_STAGING = 3;

    private static final String[] SITE_NAMES = {"unknown", "hostBuffer", "uniformBuffer", "textureStaging"};

    /** Number of caller sites; valid site values are {@code 0 .. SITE_COUNT - 1}. */
    public static final int SITE_COUNT = SITE_NAMES.length;

    /**
     * Inclusive upper bounds of size classes 0..8, by page-rounded size. Class 9 is anything up to the
     * allocator's slab size, class 10 is oversized (a dedicated slab of its own).
     */
    private static final long[] CLASS_BOUNDS = {
            4096L, 16384L, 32768L, 49152L, 61440L, 65536L, 262144L, 1048576L, 16777216L
    };

    private static final String[] CLASS_NAMES = {
            "le4k", "le16k", "le32k", "le48k", "le60k", "64k", "le256k", "le1m", "le16m", "leSlab", "oversized"
    };

    /** Number of size classes; valid class values are {@code 0 .. CLASS_COUNT - 1}. */
    public static final int CLASS_COUNT = CLASS_NAMES.length;

    /** The class whose page-rounded size is exactly the fixed page size: raw requests in (61440, 65536]. */
    public static final int FIXED_PAGE_CLASS = 5;

    private final long slabSizeBytes;
    private final long[][] live = new long[SITE_COUNT][CLASS_COUNT];
    private final long[][] total = new long[SITE_COUNT][CLASS_COUNT];
    private final long[][] fixedLive = new long[SITE_COUNT][CLASS_COUNT];
    private final List<PendingRelease> pending = new ArrayList<PendingRelease>();
    private long totalAllocations;

    public SlabAllocationHistogram(long slabSizeBytes) {
        this.slabSizeBytes = slabSizeBytes;
    }

    /** The size class of a page-rounded size, against an allocator whose ordinary slab is {@code slabSizeBytes}. */
    public static int classify(long pagedSizeBytes, long slabSizeBytes) {
        for (int sizeClass = 0; sizeClass < CLASS_BOUNDS.length; sizeClass++) {
            if (pagedSizeBytes <= CLASS_BOUNDS[sizeClass]) {
                return sizeClass;
            }
        }
        return pagedSizeBytes <= slabSizeBytes ? 9 : 10;
    }

    /** The size class of a page-rounded size under this histogram's slab size. */
    public int classify(long pagedSizeBytes) {
        return classify(pagedSizeBytes, this.slabSizeBytes);
    }

    /** Records one successful allocation: live and cumulative counts rise at once. */
    public void noteAllocated(int site, long pagedSizeBytes, boolean fixedServed) {
        int siteIndex = siteIndex(site);
        int sizeClass = classify(pagedSizeBytes);
        this.live[siteIndex][sizeClass]++;
        this.total[siteIndex][sizeClass]++;
        if (fixedServed) {
            this.fixedLive[siteIndex][sizeClass]++;
        }
        this.totalAllocations++;
    }

    /**
     * Records one release: the allocation stays counted as live until {@link #collectCompleted(long)} is
     * told a completed value at or past {@code timelineValue}, mirroring the ledgers' deferred release.
     */
    public void noteReleased(int site, long pagedSizeBytes, boolean fixedServed, long timelineValue) {
        this.pending.add(new PendingRelease(siteIndex(site), classify(pagedSizeBytes), fixedServed, timelineValue));
    }

    /** Settles every pending release whose timeline value the completed value has passed. */
    public void collectCompleted(long completedTimelineValue) {
        Iterator<PendingRelease> iterator = this.pending.iterator();
        while (iterator.hasNext()) {
            PendingRelease release = iterator.next();
            if (release.timelineValue <= completedTimelineValue) {
                iterator.remove();
                this.live[release.site][release.sizeClass]--;
                if (release.fixedServed) {
                    this.fixedLive[release.site][release.sizeClass]--;
                }
            }
        }
    }

    /** Live allocations of one caller in one size class, including ones pending a deferred release. */
    public long liveCount(int site, int sizeClass) {
        return this.live[siteIndex(site)][sizeClass];
    }

    /** Allocations ever made by one caller in one size class. */
    public long totalCount(int site, int sizeClass) {
        return this.total[siteIndex(site)][sizeClass];
    }

    /** Live allocations of one caller in one size class that the fixed-page ledger itself served. */
    public long fixedLiveCount(int site, int sizeClass) {
        return this.fixedLive[siteIndex(site)][sizeClass];
    }

    /** Releases recorded but not yet settled by a collection. */
    public int pendingCount() {
        return this.pending.size();
    }

    /** Whether no allocation has ever been recorded. */
    public boolean isEmpty() {
        return this.totalAllocations == 0L;
    }

    /**
     * Appends the non-empty cells as space-separated {@code site:class=live/total} tokens, with a
     * {@code (fixed=N)} suffix on a cell whose live count includes fixed-page-ledger servings. Only cells
     * with at least one allocation ever made are rendered, so an idle pool costs no text.
     */
    public void appendSummary(StringBuilder summary) {
        boolean first = true;
        for (int site = 0; site < SITE_COUNT; site++) {
            for (int sizeClass = 0; sizeClass < CLASS_COUNT; sizeClass++) {
                if (this.total[site][sizeClass] == 0L) {
                    continue;
                }
                if (!first) {
                    summary.append(' ');
                }
                first = false;
                summary.append(SITE_NAMES[site]).append(':').append(CLASS_NAMES[sizeClass])
                        .append('=').append(this.live[site][sizeClass])
                        .append('/').append(this.total[site][sizeClass]);
                if (this.fixedLive[site][sizeClass] > 0L) {
                    summary.append("(fixed=").append(this.fixedLive[site][sizeClass]).append(')');
                }
            }
        }
    }

    private static int siteIndex(int site) {
        return site >= 0 && site < SITE_COUNT ? site : SITE_UNKNOWN;
    }

    /** One recorded release, settled once collection passes its timeline value. */
    private static final class PendingRelease {
        final int site;
        final int sizeClass;
        final boolean fixedServed;
        final long timelineValue;

        PendingRelease(int site, int sizeClass, boolean fixedServed, long timelineValue) {
            this.site = site;
            this.sizeClass = sizeClass;
            this.fixedServed = fixedServed;
            this.timelineValue = timelineValue;
        }
    }
}
