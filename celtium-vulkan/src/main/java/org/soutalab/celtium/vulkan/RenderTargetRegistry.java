package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Which real image each framebuffer identity is drawn into, at which generation, in which layout - and who clears it.
 * <p>
 * R5 of {@code docs/history/refactoring-plan.md}: "main／default／chain／post の実 image が明示され、alias/layout/clear の所有が
 * 一意". This class is the one place that answers the three questions the backend has been answering from scattered
 * fields:
 * <ol>
 *   <li><b>alias</b> - a GL framebuffer identity ({@code 0} for the default framebuffer, the game's own main identity,
 *   and every other identity a mod or the game binds) maps to exactly one {@link ImageSlot}, and that mapping is
 *   recorded with the generation it was made under. Two identities never share a slot silently: {@link #slotFor}
 *   returns {@link ImageSlot#CHAIN_PAIR} for every non-zero non-main identity today, and {@link #sharedSlots()} names
 *   the identities that share one, which is the measurement B10/BH-14 needs rather than an assumption.</li>
 *   <li><b>layout</b> - each slot carries the layout its image is currently in, and the generation at which that
 *   layout was recorded. A layout noted for an older generation is stale by construction and is reported as such
 *   instead of being read as current.</li>
 *   <li><b>clear</b> - the clear a frame issues belongs to the slot of the identity that was bound when the game
 *   issued it, and at most one slot owns the colour clear of a frame. {@link #noteClear} refuses (and counts) a clear
 *   aimed at a slot whose content another identity already occupies this frame, which is the rule that keeps a late
 *   clear from erasing work an earlier window of the same frame did.</li>
 * </ol>
 * <p>
 * Deliberately pure state: no call into the backend, no Vulkan handle it cannot name from the values passed in, and no
 * allocation on the per-bind path beyond the bounded census. The backend keeps the handles; this class keeps the
 * bookkeeping, so the wiring is a delegation per method rather than a behaviour change.
 * <p>
 * Not owned here: the colour-texture map and the publication counters. R1b moved those to
 * {@code PublishedFrameTextures}, and duplicating them here would give the same fact two owners.
 */
public final class RenderTargetRegistry {

    /** Identity shared by raw framebuffer bind and colour-texture adoption. */
    public static long rawFramebufferIdentity(int framebuffer) {
        return framebuffer == 0 ? 0L : (1L << 40) | (framebuffer & 0xFFFFFFFFL);
    }

    /**
     * {@code -Dceltium.vulkan.rawFboTextureIdentity=true} (off by default, unverified): a raw framebuffer with a colour
     * texture draws into an image keyed by that texture rather than by the framebuffer. GL keeps one storage per
     * texture, so a framebuffer whose attachment is swapped (Xaero's radar icons: {@code setFramebufferTexture} moves
     * one framebuffer between the icon atlas and its own texture) must not land both in one image; see
     * docs/evidence/xaero-radar-icons-2026-11-05.md.
     */
    public static final boolean RAW_TEXTURE_IDENTITY = Boolean.getBoolean("celtium.vulkan.rawFboTextureIdentity");

    /** The identity of the image a raw framebuffer's colour texture owns under {@link #RAW_TEXTURE_IDENTITY}. */
    public static long rawColorTextureIdentity(int colorTexture) {
        return (1L << 41) | (colorTexture & 0xFFFFFFFFL);
    }

    /** {@return the identity a raw framebuffer's draws go to}: its colour texture's under the switch, else its own. */
    public static long rawTargetIdentity(int framebuffer, int colorTexture) {
        if (framebuffer == 0) {
            return 0L;
        }
        return RAW_TEXTURE_IDENTITY && colorTexture != 0 ? rawColorTextureIdentity(colorTexture)
                : rawFramebufferIdentity(framebuffer);
    }

    /** The four real images a framebuffer identity can be drawn into. */
    public enum ImageSlot {
        /** The delivered frame image the present path blits from (the game's main framebuffer). */
        MAIN("main"),
        /** The pair for draws with the default framebuffer bound (identity 0). */
        DEFAULT_PAIR("default-pair"),
        /** The pair for the game's other framebuffers - the entity-outline silhouette and the splash's own FBO. */
        CHAIN_PAIR("chain-pair"),
        /** An image a post chain owns or adopted; it is not shared with a framebuffer identity. */
        POST("post");

        private final String label;

        ImageSlot(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        /** {@return the slot a non-main identity is routed to}: identity 0 is the default pair, everything else the
         * chain pair. This is the rule the backend applies today, stated once so a per-identity target (BH-14) has one
         * place to change. */
        public static ImageSlot forNonMainIdentity(long identity) {
            return identity == 0L ? DEFAULT_PAIR : CHAIN_PAIR;
        }
    }

    /** How many distinct identities the census records before it stops growing. */
    private static final int CENSUS_LIMIT = 12;

    // ---------------------------------------------------------------- alias

    /** The identity the game's main framebuffer has, or {@link Long#MIN_VALUE} before the first main bind. */
    private long mainIdentity = Long.MIN_VALUE;
    /** The identity bound right now, and whether the renderer treats it as main. */
    private long currentIdentity = -1L;
    private boolean currentIsMain = true;
    /** The frame-target generation: bumped whenever the frame image is replaced. */
    private long generation;
    /** identity -> the slot it was routed to, with the generation that routing was recorded under. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Alias> aliases =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private long lastAliasIdentity = Long.MIN_VALUE;
    private Alias lastAlias;
    /** The distinct identities the game has bound, bounded; the census the routing rule is read against. */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet bindCensus =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private int bindCensusOverflow;
    private String censusSummary = "none";

    /** One identity's routing: the slot, whether it was main, and the generation it was recorded at. */
    private static final class Alias {
        ImageSlot slot;
        boolean main;
        long generation;

        Alias(ImageSlot slot, boolean main, long generation) {
            this.slot = slot;
            this.main = main;
            this.generation = generation;
        }
    }

    /**
     * Records a framebuffer bind and answers which slot its draws belong to.
     * <p>
     * The first bind of a main identity fixes {@link #mainIdentity}; every later bind is compared against it. The
     * census is bounded and its overflow counted, so "the run only ever bound two identities" is a measurement rather
     * than an artefact of the bound.
     */
    public ImageSlot noteBind(long identity, boolean isMain, String routeLabel) {
        this.currentIdentity = identity;
        this.currentIsMain = isMain;
        if (isMain) {
            this.mainIdentity = identity;
        }
        ImageSlot slot = isMain ? ImageSlot.MAIN : ImageSlot.forNonMainIdentity(identity);
        Alias existing = (identity == this.lastAliasIdentity && this.lastAlias != null)
                ? this.lastAlias : this.aliases.get(identity);
        if (existing != null) {
            existing.slot = slot;
            existing.main = isMain;
            existing.generation = this.generation;
        } else {
            existing = new Alias(slot, isMain, this.generation);
            this.aliases.put(identity, existing);
        }
        this.lastAliasIdentity = identity;
        this.lastAlias = existing;
        if (this.bindCensus.size() < CENSUS_LIMIT) {
            if (this.bindCensus.add(identity) && this.bindCensus.size() == CENSUS_LIMIT) {
                // The summary is built once, at the point the census stops growing, so the per-bind path stays cheap.
                this.censusSummary = "distinct=" + this.bindCensus.size() + " overflow=" + this.bindCensusOverflow
                        + " main=" + hex(this.mainIdentity) + " lastRoute=" + routeLabel;
            }
        } else if (!this.bindCensus.contains(identity)) {
            this.bindCensusOverflow++;
        }
        return slot;
    }

    /** {@return the identity the game's main framebuffer has}, or {@link Long#MIN_VALUE} when none was bound yet. */
    public long mainIdentity() {
        return this.mainIdentity;
    }

    /** {@return the identity bound right now}. */
    public long currentIdentity() {
        return this.currentIdentity;
    }

    /** {@return whether the current bind is the renderer's main target}. */
    public boolean currentIsMain() {
        return this.currentIsMain;
    }

    /**
     * {@return the slot an identity's draws belong to}, as it was routed when it was bound.
     * <p>
     * An identity that was never bound answers with the routing rule's own answer rather than {@code null}: a caller
     * that asks about an identity it has not seen is asking a question about the rule, not about history.
     */
    public ImageSlot slotFor(long identity) {
        if (identity == this.lastAliasIdentity && this.lastAlias != null) {
            return this.lastAlias.slot;
        }
        Alias alias = this.aliases.get(identity);
        if (alias != null) {
            this.lastAliasIdentity = identity;
            this.lastAlias = alias;
            return alias.slot;
        }
        return ImageSlot.forNonMainIdentity(identity);
    }

    /**
     * The GL framebuffer-object name an identity was reported under.
     * <p>
     * The identity is the framebuffer's object identity (the mixin's {@code System.identityHashCode}), which survives
     * across bindings; the GL name is what a raw {@code glBindFramebuffer(GL_READ_FRAMEBUFFER, name)} binds when a mod
     * reads pixels, and the stub only keeps that name - so a readback that asks "which image did name N's draws go
     * into" resolves through this map. A name reused by a new framebuffer overwrites, which is the answer the new
     * reader wants.
     */
    private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap framebufferNames =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    /**
     * Records that the framebuffer with GL name {@code framebufferObject} is the object whose identity is
     * {@code identity}. Identity 0 (the unbind) and name 0 (the window) are not recorded.
     */
    public void noteFramebufferName(long identity, int framebufferObject) {
        if (identity == 0L || framebufferObject == 0) {
            return;
        }
        this.framebufferNames.put(framebufferObject, identity);
    }

    /** {@return the identity reported for a GL framebuffer name}, or {@link Long#MIN_VALUE} when none was recorded. */
    public long identityForFramebufferName(int framebufferObject) {
        // Identities are never zero, so the primitive map's absent value (0) is unambiguous here.
        long identity = this.framebufferNames.get(framebufferObject);
        return identity == 0L ? Long.MIN_VALUE : identity;
    }

    /**
     * {@return the identities that share one slot}, as {@code slot=[id, id, ...]} entries.
     * <p>
     * This is the BH-14 measurement: a slot with two identities is an image two framebuffers are drawing into, and
     * their sizes are not both representable. An empty answer means every bound identity has an image of its own.
     */
    public String sharedSlots() {
        Map<ImageSlot, List<Long>> bySlot = new EnumMap<ImageSlot, List<Long>>(ImageSlot.class);
        for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<Alias> entry : this.aliases.long2ObjectEntrySet()) {
            List<Long> ids = bySlot.get(entry.getValue().slot);
            if (ids == null) {
                ids = new ArrayList<Long>();
                bySlot.put(entry.getValue().slot, ids);
            }
            ids.add(Long.valueOf(entry.getLongKey()));
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<ImageSlot, List<Long>> entry : bySlot.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(entry.getKey().label()).append('=').append(entry.getValue());
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    /** {@return the bounded census line}: distinct identities, overflow, the main identity and the last route. */
    public String censusSummary() {
        return this.censusSummary;
    }

    // ---------------------------------------------------------------- layout

    /** Per-slot layout and generation it was noted at (Long.MIN_VALUE when unrecorded). */
    private final int[] slotLayout = new int[ImageSlot.values().length];
    private final long[] slotLayoutGen = createUnrecordedGens(ImageSlot.values().length);
    private int staleLayoutReads;

    private static long[] createUnrecordedGens(int length) {
        long[] gens = new long[length];
        java.util.Arrays.fill(gens, Long.MIN_VALUE);
        return gens;
    }

    /** Records the layout a slot's image is in, at the current generation. */
    public void noteLayout(ImageSlot slot, int layout) {
        int ord = slot.ordinal();
        this.slotLayout[ord] = layout;
        this.slotLayoutGen[ord] = this.generation;
    }

    /**
     * {@return the layout a slot's image is in}, or {@code -1} when none was recorded for this generation.
     * <p>
     * A layout noted under an older generation is <em>stale</em> - the image was replaced since - and answers -1 with
     * {@link #staleLayoutReads} counted, rather than handing back a layout that describes a destroyed image. That is
     * the failure B10 produced: a layout recorded for the previous frame target read as if it described the new one.
     */
    public int layoutOf(ImageSlot slot) {
        int ord = slot.ordinal();
        long gen = this.slotLayoutGen[ord];
        if (gen == Long.MIN_VALUE) {
            return -1;
        }
        if (gen != this.generation) {
            this.staleLayoutReads++;
            return -1;
        }
        return this.slotLayout[ord];
    }

    /** {@return how many layout reads were refused as stale}. */
    public int staleLayoutReads() {
        return this.staleLayoutReads;
    }

    // ---------------------------------------------------------------- generation

    /**
     * The latch the model-draw gate reads: whether the frame target's identity is known.
     * <p>
     * Moved here mechanically from the backend (refactoring plan R5). It is <b>not</b> cleared when the target is
     * replaced, which is today's behaviour: after a resize it can read true while the new target's identity is not
     * known yet. That is the same class as B10 - a fact fixed at the old target's time read as if it described the new
     * one - and clearing it in {@link #noteFrameTargetReplaced()} is a behaviour change that gets its own commit and
     * its own evidence, not a ride on this extraction.
     */
    private boolean targetKnown;

    /** Records that the frame target's identity has been established. */
    public void noteTargetKnown() {
        this.targetKnown = true;
    }

    /** {@return whether the frame target's identity is known}. */
    public boolean targetKnown() {
        return this.targetKnown;
    }

    /**
     * Per pair slot: every framebuffer the slot holds, keyed by the render pass each was built for, and how many
     * were created. One handle per render pass rather than one handle per slot: passes that alternate two render
     * passes used to replace the single cached entry every pass, and each replacement paid a device-idle wait to
     * retire the old handle - measured as ~5 settleGpu calls per frame on leg ind2-amd. Compatible framebuffers are
     * all over the same attachments, so keeping them only costs handles.
     */
    private final Long2LongOpenHashMap[] pairFramebuffers =
            new Long2LongOpenHashMap[ImageSlot.values().length];
    private final int[] pairFramebufferCreationCounts = new int[ImageSlot.values().length];

    /** {@return the pair slot's framebuffer built for this render pass, or 0 when it has none}. */
    public long pairFramebuffer(ImageSlot slot, long renderPass) {
        Long2LongOpenHashMap entry = this.pairFramebuffers[slot.ordinal()];
        return entry == null ? 0L : entry.get(renderPass);
    }

    /** {@return every framebuffer handle the slot holds}, for the destroy paths. */
    public java.util.Collection<Long> pairFramebufferHandles(ImageSlot slot) {
        Long2LongOpenHashMap entry = this.pairFramebuffers[slot.ordinal()];
        return entry == null ? java.util.Collections.<Long>emptyList() : entry.values();
    }

    /** {@return how many framebuffers were created for the slot}. */
    public int pairFramebufferCreations(ImageSlot slot) {
        return this.pairFramebufferCreationCounts[slot.ordinal()];
    }

    /** Records a framebuffer for (slot, renderPass), counting it when it adds a new handle. */
    public void notePairFramebuffer(ImageSlot slot, long handle, long renderPass) {
        int ord = slot.ordinal();
        Long2LongOpenHashMap entry = this.pairFramebuffers[ord];
        if (entry == null) {
            entry = new Long2LongOpenHashMap();
            this.pairFramebuffers[ord] = entry;
        }
        if (!entry.containsKey(renderPass)) {
            this.pairFramebufferCreationCounts[ord]++;
        }
        entry.put(renderPass, handle);
    }

    /** Forgets the slot's framebuffers, after they have been destroyed. */
    public void clearPairFramebuffer(ImageSlot slot) {
        this.pairFramebuffers[slot.ordinal()] = null;
    }

    // ---------------------------------------------------------------- per-identity chain-pair records
    //
    // The chain pair is one image per framebuffer identity now (it used to be a single image every non-main
    // identity shared - which is how two framebuffers overwrote each other's content: measured as the black world
    // of legs bisect4/5 and the command-buffer fixture's missing quad of leg fboalias-amd). The records a slot
    // used to hold therefore key by the identity that owns the pair.

    /** identity -> render pass -> framebuffer, for the chain pairs. */
    private final Long2ObjectOpenHashMap<Long2LongOpenHashMap> chainPairFramebuffers =
            new Long2ObjectOpenHashMap<Long2LongOpenHashMap>();
    private long lastChainFbIdentity = Long.MIN_VALUE;
    private long lastChainFbRenderPass = Long.MIN_VALUE;
    private long lastChainFbHandle;
    private final Long2IntOpenHashMap chainPairFramebufferCreationCounts = new Long2IntOpenHashMap();
    /** identity -> {frame serial of its last clear, the content identity it then held}. */
    private final Long2ObjectOpenHashMap<long[]> chainPairClears = new Long2ObjectOpenHashMap<long[]>();
    /** identity -> {layout, generation noted at}. */
    private final Long2ObjectOpenHashMap<long[]> chainPairLayouts = new Long2ObjectOpenHashMap<long[]>();

    /** {@return the identity's chain-pair framebuffer built for this render pass, or 0 when it has none}. */
    public long chainPairFramebuffer(long identity, long renderPass) {
        if (identity == this.lastChainFbIdentity && renderPass == this.lastChainFbRenderPass
                && this.lastChainFbHandle != 0L) {
            return this.lastChainFbHandle;
        }
        Long2LongOpenHashMap entry = this.chainPairFramebuffers.get(identity);
        long handle = entry == null ? 0L : entry.get(renderPass);
        if (handle != 0L) {
            this.lastChainFbIdentity = identity;
            this.lastChainFbRenderPass = renderPass;
            this.lastChainFbHandle = handle;
        }
        return handle;
    }

    /** {@return every framebuffer handle one identity's chain pair holds}. */
    public java.util.Collection<Long> chainPairFramebufferHandles(long identity) {
        Long2LongOpenHashMap entry = this.chainPairFramebuffers.get(identity);
        return entry == null ? java.util.Collections.<Long>emptyList() : entry.values();
    }

    /** {@return every framebuffer handle every chain pair holds}, for the destroy-all paths. */
    public java.util.Collection<Long> allChainPairFramebufferHandles() {
        java.util.List<Long> handles = new ArrayList<Long>();
        for (Long2LongOpenHashMap entry : this.chainPairFramebuffers.values()) {
            it.unimi.dsi.fastutil.longs.LongIterator it = entry.values().iterator();
            while (it.hasNext()) {
                handles.add(Long.valueOf(it.nextLong()));
            }
        }
        return handles;
    }

    /** {@return how many framebuffers one identity's chain pair has created}. */
    public int chainPairFramebufferCreations(long identity) {
        return this.chainPairFramebufferCreationCounts.get(identity);
    }

    /** {@return how many framebuffers every chain pair has created in total}, for the summary line. */
    public int chainPairFramebufferCreationsTotal() {
        int total = 0;
        IntIterator it = this.chainPairFramebufferCreationCounts.values().iterator();
        while (it.hasNext()) {
            total += it.nextInt();
        }
        return total;
    }

    /** Records a framebuffer for (identity, renderPass), counting it when it adds a new handle. */
    public void noteChainPairFramebuffer(long identity, long handle, long renderPass) {
        Long2LongOpenHashMap entry = this.chainPairFramebuffers.get(identity);
        if (entry == null) {
            entry = new Long2LongOpenHashMap();
            this.chainPairFramebuffers.put(identity, entry);
        }
        if (!entry.containsKey(renderPass)) {
            this.chainPairFramebufferCreationCounts.addTo(identity, 1);
        }
        entry.put(renderPass, handle);
        this.lastChainFbIdentity = identity;
        this.lastChainFbRenderPass = renderPass;
        this.lastChainFbHandle = handle;
    }

    /** Forgets one identity's chain-pair framebuffers, after they have been destroyed. */
    public void clearChainPairFramebuffer(long identity) {
        this.chainPairFramebuffers.remove(identity);
        if (this.lastChainFbIdentity == identity) {
            this.lastChainFbIdentity = Long.MIN_VALUE;
            this.lastChainFbRenderPass = Long.MIN_VALUE;
            this.lastChainFbHandle = 0L;
        }
    }

    /** Drops per-target bookkeeping once its owner has deleted the framebuffer. */
    public void forgetChainPair(long identity) {
        this.chainPairClears.remove(identity);
        this.chainPairLayouts.remove(identity);
        this.chainPairFramebufferCreationCounts.remove(identity);
        this.aliases.remove(identity);
        if (this.lastAliasIdentity == identity) {
            this.lastAliasIdentity = Long.MIN_VALUE;
            this.lastAlias = null;
        }
        if (this.lastChainFbIdentity == identity) {
            this.lastChainFbIdentity = Long.MIN_VALUE;
            this.lastChainFbRenderPass = Long.MIN_VALUE;
            this.lastChainFbHandle = 0L;
        }
        this.framebufferNames.int2LongEntrySet().removeIf(entry -> entry.getLongValue() == identity);
    }

    /** Forgets every chain-pair framebuffer, after they have been destroyed. */
    public void clearAllChainPairFramebuffers() {
        this.chainPairFramebuffers.clear();
        this.lastChainFbIdentity = Long.MIN_VALUE;
        this.lastChainFbRenderPass = Long.MIN_VALUE;
        this.lastChainFbHandle = 0L;
    }

    /** {@return the frame serial the identity's chain pair was last cleared in}, or a value no serial can equal. */
    public long chainPairClearedFrame(long identity) {
        long[] entry = this.chainPairClears.get(identity);
        return entry == null ? -1L : entry[0];
    }

    /** Records that the identity's chain pair was cleared in this frame, for the given content identity. */
    public void noteChainPairCleared(long identity, long frameSerial, long contentIdentity) {
        long[] entry = this.chainPairClears.get(identity);
        if (entry == null) {
            this.chainPairClears.put(identity, new long[] { frameSerial, contentIdentity });
            return;
        }
        entry[0] = frameSerial;
        entry[1] = contentIdentity;
    }

    /** {@return the identity whose content the identity's chain pair held when it was last cleared}. */
    public long chainPairClearContentIdentity(long identity) {
        long[] entry = this.chainPairClears.get(identity);
        return entry == null ? Long.MIN_VALUE : entry[1];
    }

    /** Records the layout one identity's chain-pair image is in, at the current generation. */
    public void noteChainPairLayout(long identity, int layout) {
        long[] entry = this.chainPairLayouts.get(identity);
        if (entry == null) {
            this.chainPairLayouts.put(identity, new long[] { layout, this.generation });
            return;
        }
        entry[0] = layout;
        entry[1] = this.generation;
    }

    /** {@return the layout the identity's chain-pair image is in}, or -1 when none current was recorded. */
    public int chainPairLayoutOf(long identity) {
        long[] entry = this.chainPairLayouts.get(identity);
        if (entry == null) {
            return -1;
        }
        if (entry[1] != this.generation) {
            this.staleLayoutReads++;
            return -1;
        }
        return (int) entry[0];
    }

    /** Records the identity adopted as the main frame target, which the bind path can also discover later. */
    public void noteMainIdentity(long identity) {
        this.mainIdentity = identity;
    }

    /**
     * Whether a frame-target replacement is in progress: announced when the bind path sees the framebuffer changing,
     * settled by the first ensure that finds the new target ready. The two setters are named because the flag had
     * three writers in the backend and the two clears were proven equivalent before the move.
     */
    private boolean replacementInProgress;

    /** Records that a frame-target replacement has been announced. */
    public void noteReplacementAnnounced() {
        this.replacementInProgress = true;
    }

    /** Records that the replacement has settled: the target is ready. */
    public void noteReplacementSettled() {
        this.replacementInProgress = false;
    }

    /** {@return whether a frame-target replacement is in progress}. */
    public boolean replacementInProgress() {
        return this.replacementInProgress;
    }

    /** {@return the frame-target generation}: bumped by the backend whenever it replaces the frame image. */
    public long generation() {
        return this.generation;
    }

    /**
     * Bumps the generation because the frame image was replaced.
     * <p>
     * Every layout and alias recorded under the previous generation is stale from this point; they are dropped rather
     * than carried, so a later read cannot mistake them for the new image's state. The aliases are kept but re-stamped
     * lazily by the next bind, because an identity keeps its routing across a replacement.
     */
    public void noteFrameTargetReplaced() {
        this.generation++;
        java.util.Arrays.fill(this.slotLayoutGen, Long.MIN_VALUE);
        this.chainPairLayouts.clear();
    }

    // ---------------------------------------------------------------- clear ownership

    /** The clear latched for an identity: mask, colour, depth, and the slot it was latched for. */
    private int latchedMask;
    private float latchedRed;
    private float latchedGreen;
    private float latchedBlue;
    private float latchedAlpha;
    private double latchedDepth = 1.0D;
    private ImageSlot latchedSlot;
    private long latchedGeneration = -1L;
    private boolean hasLatchedClear;
    /** The identity the latched clear names - {@link #latchedSlot} alone cannot tell one chain pair from another. */
    private long latchedIdentity = Long.MIN_VALUE;
    /** The frame whose clears have already been attributed, and which slot owns this frame's colour clear. */
    private long clearFrame = -1L;
    private ImageSlot colourClearOwner;
    private final int[] clearsBySlot = new int[ImageSlot.values().length];
    private int latchedClearsApplied;
    private int liveClearsApplied;
    private int clearCollisions;

    /**
     * Latches the clear the game issued, for the identity that was bound when it issued it.
     * <p>
     * The game clears a framebuffer between binding it and binding the next one, so this is the only moment the clear
     * can be attributed to the image it names. The slot is resolved through {@link #slotFor}, so the attribution is
     * the same rule the draws follow.
     */
    public void latchClear(long identity, int mask, float red, float green, float blue, float alpha, double depth) {
        this.latchedMask = mask;
        this.latchedRed = red;
        this.latchedGreen = green;
        this.latchedBlue = blue;
        this.latchedAlpha = alpha;
        this.latchedDepth = depth;
        this.latchedSlot = slotFor(identity);
        this.latchedIdentity = identity;
        this.latchedGeneration = this.generation;
        this.hasLatchedClear = true;
    }

    /** {@return whether a latched clear is waiting to be applied}. */
    public boolean hasLatchedClear() {
        return this.hasLatchedClear;
    }

    /** {@return the slot the latched clear belongs to}, or {@code null} when none is latched. */
    public ImageSlot latchedSlot() {
        return this.latchedSlot;
    }

    /** {@return the identity the latched clear names}, or {@link Long#MIN_VALUE} when none is latched. */
    public long latchedIdentity() {
        return this.latchedIdentity;
    }

    /** {@return the latched clear's buffer mask}. */
    public int latchedMask() {
        return this.latchedMask;
    }

    /** {@return the latched clear's colour as {r, g, b, a}}. */
    public float[] latchedColour() {
        return new float[] {this.latchedRed, this.latchedGreen, this.latchedBlue, this.latchedAlpha};
    }

    /** {@return the latched clear's depth value}. */
    public double latchedDepth() {
        return this.latchedDepth;
    }

    /**
     * Begins a frame's clear ownership: one frame, one owner of the colour clear.
     * <p>
     * Called once per frame before any clear is attributed. A latched clear from the previous frame that was never
     * applied is dropped and counted, because applying it in this frame would clear an image with a colour the game
     * asked for while a different framebuffer was bound.
     */
    public void beginFrame(long frameId) {
        if (this.clearFrame == frameId) {
            return;
        }
        this.clearFrame = frameId;
        this.colourClearOwner = null;
        if (this.hasLatchedClear && this.latchedGeneration != this.generation) {
            this.hasLatchedClear = false;
            this.latchedSlot = null;
            this.latchedIdentity = Long.MIN_VALUE;
        }
    }

    /**
     * Records that a clear landed on a slot, and refuses one that would erase another identity's content.
     *
     * @param slot    the slot the clear names
     * @param colour  whether the clear includes the colour buffer (a depth-only clear never collides)
     * @param latched whether it came from the latched game clear rather than a live read
     * @return {@code true} when the clear is the frame's first colour clear for that slot, {@code false} when it was
     *         refused because another identity already owns this frame's colour clear
     */
    public boolean noteClear(ImageSlot slot, boolean colour, boolean latched) {
        if (colour) {
            if (this.colourClearOwner != null && this.colourClearOwner != slot) {
                this.clearCollisions++;
                return false;
            }
            this.colourClearOwner = slot;
        }
        this.clearsBySlot[slot.ordinal()]++;
        if (latched) {
            this.latchedClearsApplied++;
            this.hasLatchedClear = false;
            this.latchedSlot = null;
            this.latchedIdentity = Long.MIN_VALUE;
        } else {
            this.liveClearsApplied++;
        }
        return true;
    }

    /** {@return the slot that owns this frame's colour clear}, or {@code null} when none has cleared yet. */
    public ImageSlot colourClearOwner() {
        return this.colourClearOwner;
    }

    /** {@return how many clears landed on each slot}, as {@code slot=n} entries. */
    public String clearsBySlot() {
        ImageSlot[] slots = ImageSlot.values();
        StringBuilder sb = new StringBuilder("{");
        boolean any = false;
        for (int i = 0; i < slots.length; i++) {
            int count = this.clearsBySlot[i];
            if (count == 0) {
                continue;
            }
            if (any) {
                sb.append(", ");
            }
            sb.append(slots[i].name()).append('=').append(count);
            any = true;
        }
        return sb.append('}').toString();
    }

    /** {@return how many clears came from the latch and how many were read live}. */
    public String clearAttribution() {
        return "latched=" + this.latchedClearsApplied + " live=" + this.liveClearsApplied
                + " collisions=" + this.clearCollisions;
    }

    /** {@return how many clears were refused because another identity owned the frame's colour clear}. */
    public int clearCollisions() {
        return this.clearCollisions;
    }

    /**
     * The frame serial each pair was last cleared in, and the identity whose content it then held.
     * <p>
     * The pair clear's decision is a policy question - whether this identity may clear a pair that already holds
     * another identity's content this frame - so the policy stays with the backend, which knows the pair. What moves
     * here is the bookkeeping it reads and writes: the frame a pair was last cleared in, which used to be two bare
     * fields on the backend and is the same fact as the slot's clear ownership.
     */
    private final Map<ImageSlot, long[]> pairClears = new EnumMap<ImageSlot, long[]>(ImageSlot.class);

    /** {@return the frame serial the pair was last cleared in, or a value no frame serial can equal}. */
    public long pairClearedFrame(ImageSlot slot) {
        long[] entry = this.pairClears.get(slot);
        return entry == null ? -1L : entry[0];
    }

    /** Records that the pair was cleared in this frame, for the given content identity. */
    public void notePairCleared(ImageSlot slot, long frameSerial, long contentIdentity) {
        long[] entry = this.pairClears.get(slot);
        if (entry == null) {
            this.pairClears.put(slot, new long[] { frameSerial, contentIdentity });
            return;
        }
        entry[0] = frameSerial;
        entry[1] = contentIdentity;
    }

    /** {@return the identity whose content the pair held when it was last cleared}. */
    public long pairClearContentIdentity(ImageSlot slot) {
        long[] entry = this.pairClears.get(slot);
        return entry == null ? Long.MIN_VALUE : entry[1];
    }

    // ---------------------------------------------------------------- named targets (BH-14 extension)

    /** Kind of a named render target; shadow depth targets carry a comparison sampler. */
    public enum TargetKind {
        COLOR(false, false),
        DEPTH(true, false),
        SHADOW_DEPTH(true, true);

        private final boolean depth;
        private final boolean comparisonSampler;

        TargetKind(boolean depth, boolean comparisonSampler) {
            this.depth = depth;
            this.comparisonSampler = comparisonSampler;
        }

        public boolean isDepth() {
            return this.depth;
        }

        public boolean usesComparisonSampler() {
            return this.comparisonSampler;
        }
    }

    /** Generic image format class for declared named targets. */
    public enum TargetFormatClass {
        RGBA8_UNORM(37, false),
        RGBA16_FLOAT(97, false),
        RGBA32_FLOAT(109, false),
        R11G11B10_FLOAT(122, false),
        DEPTH32_FLOAT(126, true),
        DEPTH24_STENCIL8(129, true);

        // The GL internal formats the D18-A mapping (QUEUE 394) names, pinned against lwjgl-opengl 3.4.3's constants:
        // GL_RGBA8=0x8058, GL_RGBA=0x1908, GL_RGBA16F=0x881A, GL_RGBA32F=0x8814, GL_R11F_G11F_B10F=0x8C3A.
        private static final int GL_RGBA = 0x1908;
        private static final int GL_RGBA8 = 0x8058;
        private static final int GL_RGBA16F = 0x881A;
        private static final int GL_RGBA32F = 0x8814;
        private static final int GL_R11F_G11F_B10F = 0x8C3A;

        private final int vkFormat;
        private final boolean depth;

        TargetFormatClass(int vkFormat, boolean depth) {
            this.vkFormat = vkFormat;
            this.depth = depth;
        }

        public int vkFormat() {
            return this.vkFormat;
        }

        public boolean isDepth() {
            return this.depth;
        }

        /** {@return the bytes one pixel of this class occupies}: the figure the memory arithmetic and the format
         * census report. Packed formats are their storage size, not four channels of their top precision
         * ({@link #R11G11B10_FLOAT} is 32 bits, {@link #DEPTH24_STENCIL8} is 24+8). */
        public int bytesPerPixel() {
            return switch (this) {
                case RGBA8_UNORM, R11G11B10_FLOAT, DEPTH32_FLOAT, DEPTH24_STENCIL8 -> 4;
                case RGBA16_FLOAT -> 8;
                case RGBA32_FLOAT -> 16;
            };
        }

        /**
         * The format class a GL internal format asks for, or {@code null} when celtium has no class for it (D18-A,
         * QUEUE 394).
         * <p>
         * The mapping is deliberately a table and not a guess: these are the internal formats the D18 split's readiness
         * note (item 394, §2.1) enumerates with both a GLenum and a class in this enum - five GLenums onto four classes.
         * {@code GL_RGBA} is the unsized base format, which GL resolves to 8 bits per channel; that is the precision
         * the delivered frame has ({@code CeltiumGlStub} answers {@code GL_RED_BITS}=8 for it).
         * <p>
         * <b>{@code null} is the refusal, and the caller owns the fallback.</b> An unmapped internal format must be
         * allocated as {@link #RGBA8_UNORM} <em>and counted</em> (readiness §2.2 rule 2: no silent defaults), so this
         * method answers "no class" instead of answering {@code RGBA8_UNORM} - a caller that cannot tell the two
         * apart would record a 16-bit request as a default rather than as a dropped requirement. The named and
         * unmapped remainders, each with the reason it is not mapped here, are in {@code TargetFormatMappingTest}:
         * {@code GL_RGB10_A2} (VK_FORMAT_A2B10G10R10_UNORM_PACK32 = 64, no class in this enum), {@code GL_RGBA16}
         * (91), {@code GL_SRGB8_ALPHA8} (43), {@code GL_RGBA8_SNORM}, {@code GL_RGB565}, and the single/dual/RGB
         * channel formats ({@code GL_R8/RG8/R16F/RG16F/R32F/RGB8/RGB16F/RGB}) - the class vocabulary here is
         * RGBA-shaped, and widening it is a separate change, not a mapping.
         * <p>
         * Depth internal formats ({@code GL_DEPTH_COMPONENT24} and the rest of the stub's
         * {@code isDepthInternalFormat} set) return {@code null} on purpose: the depth/stencil format of a frame is
         * chosen by {@link VulkanDepthFormat} from the device's feature set, with this class's
         * {@link #DEPTH32_FLOAT}/{@link #DEPTH24_STENCIL8} as its vocabulary, so a GL depth request is not this
         * table's answer to give.
         */
        public static TargetFormatClass forGlInternalFormat(int glInternalFormat) {
            return switch (glInternalFormat) {
                case GL_RGBA8, GL_RGBA -> RGBA8_UNORM;
                case GL_RGBA16F -> RGBA16_FLOAT;
                case GL_RGBA32F -> RGBA32_FLOAT;
                case GL_R11F_G11F_B10F -> R11G11B10_FLOAT;
                default -> null;
            };
        }
    }

    /** Resolution rule for a named target: either proportional to the frame size or a fixed pixel extent. */
    public record TargetSizing(boolean scaled, float scaleX, float scaleY, int fixedWidth, int fixedHeight) {
        public TargetSizing {
            if (scaled) {
                if (!Float.isFinite(scaleX) || scaleX <= 0.0f || !Float.isFinite(scaleY) || scaleY <= 0.0f) {
                    throw new IllegalArgumentException(
                            "named target scale must be finite and > 0, got (" + scaleX + ", " + scaleY + ")");
                }
            } else if (fixedWidth <= 0 || fixedHeight <= 0) {
                throw new IllegalArgumentException(
                        "named target fixed extent must be > 0, got " + fixedWidth + "x" + fixedHeight);
            }
        }

        public static TargetSizing scaled(float scale) {
            return scaled(scale, scale);
        }

        public static TargetSizing scaled(float scaleX, float scaleY) {
            return new TargetSizing(true, scaleX, scaleY, 0, 0);
        }

        public static TargetSizing fixed(int width, int height) {
            return new TargetSizing(false, 1.0f, 1.0f, width, height);
        }

        public int resolveWidth(int frameWidth) {
            if (!this.scaled) {
                return this.fixedWidth;
            }
            if (frameWidth <= 0) {
                throw new IllegalArgumentException("frameWidth must be > 0, got " + frameWidth);
            }
            return Math.max(1, Math.round(frameWidth * this.scaleX));
        }

        public int resolveHeight(int frameHeight) {
            if (!this.scaled) {
                return this.fixedHeight;
            }
            if (frameHeight <= 0) {
                throw new IllegalArgumentException("frameHeight must be > 0, got " + frameHeight);
            }
            return Math.max(1, Math.round(frameHeight * this.scaleY));
        }
    }

    /** How a named target's contents are initialized before its first write. */
    public enum TargetClearPolicy {
        CLEAR_EACH_FRAME,
        CLEAR_ON_CREATE,
        DONT_CARE
    }

    /** Declaration of an independent named render target. */
    public record NamedTargetSpec(
            String name,
            TargetKind kind,
            TargetFormatClass formatClass,
            TargetSizing sizing,
            TargetClearPolicy clearPolicy,
            float clearRed,
            float clearGreen,
            float clearBlue,
            float clearAlpha,
            float clearDepth) {
        public NamedTargetSpec {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("named target name must not be blank");
            }
            if (kind == null || formatClass == null || sizing == null || clearPolicy == null) {
                throw new IllegalArgumentException("named target kind, formatClass, sizing and clearPolicy are required");
            }
            if (kind.isDepth() != formatClass.isDepth()) {
                throw new IllegalArgumentException("named target kind " + kind
                        + " is incompatible with format class " + formatClass);
            }
            if (!Float.isFinite(clearRed) || !Float.isFinite(clearGreen) || !Float.isFinite(clearBlue)
                    || !Float.isFinite(clearAlpha) || !Float.isFinite(clearDepth)
                    || clearDepth < 0.0f || clearDepth > 1.0f) {
                throw new IllegalArgumentException("named target clear values must be finite and depth in [0, 1]");
            }
        }

        public static NamedTargetSpec color(String name, TargetFormatClass formatClass, TargetSizing sizing,
                                            TargetClearPolicy clearPolicy) {
            return new NamedTargetSpec(name, TargetKind.COLOR, formatClass, sizing, clearPolicy,
                    0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }

        public static NamedTargetSpec color(String name, TargetFormatClass formatClass, TargetSizing sizing,
                                            TargetClearPolicy clearPolicy, float red, float green, float blue,
                                            float alpha) {
            return new NamedTargetSpec(name, TargetKind.COLOR, formatClass, sizing, clearPolicy,
                    red, green, blue, alpha, 1.0f);
        }

        public static NamedTargetSpec depth(String name, TargetFormatClass formatClass, TargetSizing sizing,
                                            TargetClearPolicy clearPolicy) {
            return new NamedTargetSpec(name, TargetKind.DEPTH, formatClass, sizing, clearPolicy,
                    0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }

        public static NamedTargetSpec shadowDepth(String name, TargetFormatClass formatClass, TargetSizing sizing,
                                                  TargetClearPolicy clearPolicy) {
            return new NamedTargetSpec(name, TargetKind.SHADOW_DEPTH, formatClass, sizing, clearPolicy,
                    0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }
    }

    /** Allocation and layout bookkeeping for one declared named target. */
    public static final class NamedTargetState {
        private final NamedTargetSpec spec;
        private long image;
        private long view;
        private long sampler;
        private int width;
        private int height;
        private int layout;
        private int recordedLayout;
        private long generation;
        private long clearedFrame = -1L;

        NamedTargetState(NamedTargetSpec spec, long image, long view, long sampler, int width, int height,
                         int layout, long generation) {
            this.spec = spec;
            this.image = image;
            this.view = view;
            this.sampler = sampler;
            this.width = width;
            this.height = height;
            this.layout = layout;
            this.recordedLayout = layout;
            this.generation = generation;
        }

        public NamedTargetSpec spec() {
            return this.spec;
        }

        public long image() {
            return this.image;
        }

        public long view() {
            return this.view;
        }

        public long sampler() {
            return this.sampler;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        /** The confirmed layout: the one the last accepted submission left this image in. */
        public int layout() {
            return this.layout;
        }

        /**
         * The layout the open recording leaves this image in - what a barrier recorded next must chain from.
         * Equals {@link #layout()} while no open recording names this target.
         */
        public int recordedLayout() {
            return this.recordedLayout;
        }

        public long generation() {
            return this.generation;
        }

        public long clearedFrame() {
            return this.clearedFrame;
        }
    }

    private final Map<String, NamedTargetSpec> namedTargetSpecs = new java.util.LinkedHashMap<>();
    private final Map<String, NamedTargetState> namedTargetStates = new java.util.LinkedHashMap<>();

    /** Declares an independent named target; duplicate names are rejected. */
    public void declareNamedTarget(NamedTargetSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("named target spec must not be null");
        }
        if (this.namedTargetSpecs.containsKey(spec.name())) {
            throw new IllegalArgumentException("duplicate named target: " + spec.name());
        }
        this.namedTargetSpecs.put(spec.name(), spec);
    }

    public boolean hasNamedTarget(String name) {
        return name != null && this.namedTargetSpecs.containsKey(name);
    }

    public NamedTargetSpec namedTarget(String name) {
        return name == null ? null : this.namedTargetSpecs.get(name);
    }

    public java.util.Collection<NamedTargetSpec> namedTargets() {
        return java.util.Collections.unmodifiableCollection(this.namedTargetSpecs.values());
    }

    public int namedTargetCount() {
        return this.namedTargetSpecs.size();
    }

    /** Records an allocated Vulkan image/view/sampler for a declared named target. */
    public NamedTargetState noteNamedTargetAllocated(String name, long image, long view, long sampler,
                                                     int width, int height, int layout) {
        NamedTargetSpec spec = this.namedTargetSpecs.get(name);
        if (spec == null) {
            throw new IllegalArgumentException("undeclared named target: " + name);
        }
        if (image == 0L || view == 0L || sampler == 0L || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("named target allocation requires non-zero handles and extent");
        }
        NamedTargetState existing = this.namedTargetStates.get(name);
        long nextGeneration = existing == null ? 1L : existing.generation + 1L;
        NamedTargetState state = new NamedTargetState(spec, image, view, sampler, width, height, layout, nextGeneration);
        this.namedTargetStates.put(name, state);
        // The allocation fact replaces the image itself: a recorded transition that named the old image
        // must not land on the new one at commit.
        dropNamedTargetRecordings(name);
        return state;
    }

    public NamedTargetState namedTargetState(String name) {
        return name == null ? null : this.namedTargetStates.get(name);
    }

    /**
     * Layout transitions recorded into one command buffer and not yet accepted by the queue, per named target:
     * the layout the recording leaves the target in. This is the three-phase discipline
     * {@code VulkanGlTexture} keeps for foreign images (note / commit / abandon), applied to named targets
     * (audit-vulkan-core E2): {@link NamedTargetState#layout()} is the layout the last accepted submission
     * left the image in, and a transition recorded into a buffer whose submission never happens - refused
     * submit, buffer reset, a frame abandoned before submission - must not move it, or the next frame's
     * barrier names an {@code oldLayout} the image is not in.
     */
    private final Map<Long, Map<String, Integer>> namedLayoutRecordings = new java.util.HashMap<>();

    /**
     * Opens the named-target layout recording for a command buffer, dropping any uncommitted recording the
     * same handle still holds - which is exactly the reset/abandon case, as with
     * {@code VulkanGlTexture.beginLayoutRecording}: the previous recording's barriers may never have executed,
     * so its targets' recorded heads fall back to their confirmed layouts.
     */
    public void beginNamedTargetLayoutRecording(long commandBuffer) {
        Map<String, Integer> previous = this.namedLayoutRecordings.remove(commandBuffer);
        if (previous != null) {
            settleNamedTargetRecordedLayouts(previous.keySet());
        }
    }

    /**
     * Notes that the recording into {@code commandBuffer} transitions the named target to {@code layout}.
     * The target's recorded head moves now, so a later barrier in the same recording chains from it; its
     * confirmed layout moves only at {@link #commitNamedTargetLayoutRecording(long)}.
     */
    public void noteNamedTargetLayout(long commandBuffer, String name, int layout) {
        NamedTargetState state = this.namedTargetStates.get(name);
        if (state == null) {
            return;
        }
        this.namedLayoutRecordings
                .computeIfAbsent(commandBuffer, key -> new java.util.LinkedHashMap<>())
                .put(name, Integer.valueOf(layout));
        state.recordedLayout = layout;
    }

    /**
     * Makes every transition recorded into this command buffer fact: the submission that carries them was
     * accepted by the queue. Called at the same point as {@code VulkanGlTexture.commitLayoutRecording} -
     * immediately after the queue accepted the submission, never before it.
     */
    public void commitNamedTargetLayoutRecording(long commandBuffer) {
        Map<String, Integer> recording = this.namedLayoutRecordings.remove(commandBuffer);
        if (recording == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : recording.entrySet()) {
            NamedTargetState state = this.namedTargetStates.get(entry.getKey());
            if (state != null) {
                state.layout = entry.getValue().intValue();
            }
        }
        settleNamedTargetRecordedLayouts(recording.keySet());
    }

    /**
     * Drops the transitions recorded into a command buffer without applying them, because the submission
     * that would have carried them was refused or its recording thrown away.
     */
    public void abandonNamedTargetLayoutRecording(long commandBuffer) {
        Map<String, Integer> recording = this.namedLayoutRecordings.remove(commandBuffer);
        if (recording != null) {
            settleNamedTargetRecordedLayouts(recording.keySet());
        }
    }

    /** Recomputes each named target's recorded head from the recordings still open, falling back to the confirmed layout. */
    private void settleNamedTargetRecordedLayouts(java.util.Set<String> names) {
        for (String name : names) {
            NamedTargetState state = this.namedTargetStates.get(name);
            if (state == null) {
                continue;
            }
            int head = state.layout;
            for (Map<String, Integer> recording : this.namedLayoutRecordings.values()) {
                Integer noted = recording.get(name);
                if (noted != null) {
                    head = noted.intValue();
                }
            }
            state.recordedLayout = head;
        }
    }

    /** Drops any uncommitted recorded transitions naming a target whose allocation fact was just replaced or forgotten. */
    private void dropNamedTargetRecordings(String name) {
        for (Map<String, Integer> recording : this.namedLayoutRecordings.values()) {
            recording.remove(name);
        }
    }

    public void noteNamedTargetCleared(String name, long frameSerial) {
        NamedTargetState state = this.namedTargetStates.get(name);
        if (state != null) {
            state.clearedFrame = frameSerial;
        }
    }

    public void forgetNamedTargetAllocation(String name) {
        if (name != null) {
            dropNamedTargetRecordings(name);
            this.namedTargetStates.remove(name);
        }
    }

    public void clearNamedTargetAllocations() {
        this.namedTargetStates.clear();
        this.namedLayoutRecordings.clear();
    }

    public void clearNamedTargets() {
        this.namedTargetStates.clear();
        this.namedTargetSpecs.clear();
        this.namedLayoutRecordings.clear();
    }

    private static String hex(long value) {
        return value == Long.MIN_VALUE ? "unset" : "0x" + Long.toHexString(value);
    }
}
