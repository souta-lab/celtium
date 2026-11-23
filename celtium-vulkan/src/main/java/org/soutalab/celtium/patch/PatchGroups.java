package org.soutalab.celtium.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The patch-group table: which mixin belongs to which group, what a group costs when it is not applied, and which
 * groups may never be quarantined. Inherited from VulkanMod-Next's grouped patch toggles (survey D19, quarantine
 * half only): a launch that fails inside a class patch is repaired on the next launch by naming the group, instead
 * of presenting a half-patched renderer or dying the same way every start.
 * <p>
 * Two rules shape the table, and both come from the mixin layer rather than from taste:
 * <ul>
 *   <li><b>The unit is a feature, not a single mixin.</b> One failing mixin aborts the whole target class
 *   ({@code MixinApplicatorStandard.apply} rethrows for a required injector, and
 *   {@code TargetClassContext.applyMixins} stops), so a group is either entirely registered or entirely absent.
 *   Half a feature is worse than none of it.</li>
 *   <li><b>Keys are relative names below {@code org.taumc.celeritas.mixin}, never simple names.</b> Three simple
 *   names already collide - {@code RenderItemMixin}, {@code TextureUtilMixin}, {@code TextureAtlasSpriteMixin} -
 *   so a simple-name key would silently file one of each pair under the other's group.</li>
 * </ul>
 * The table holds <b>strings only</b>. A {@code Class<?>} literal would load the very mixin class the gate is
 * deciding not to register, which would make the gate meaningless; {@code scripts/check_patch_groups.py} is what
 * keeps the strings honest (it checks that every discovered mixin is listed once, that every listed name is a real
 * mixin, and that no collision is addressed by a simple name).
 * <p>
 * <b>Essential</b> means "quarantine would be worse than the crash": the group is either the renderer itself, or it
 * carries a type that non-mixin code casts to - {@code terrain_geometry} via {@code BakedQuadMixin}'s
 * {@code BakedQuadView} ({@code common/.../model/quad/BakedQuadView.java:21}), {@code textures} via
 * {@code SpriteExtension} ({@code impl/render/terrain/sprite/SpriteUtil.java:8}) and {@code TextureMapExtension}
 * ({@code impl/render/terrain/compile/VintageChunkBuildContext.java:54}) - or it has not been read for that question
 * yet ({@code entities}, which the design leaves at "to be audited"). Failures there stay fatal and
 * say so, exactly as the post-chain stage gate ({@code StageGate}) declines to quarantine the presenter.
 * <p>
 * Kill-switch naming: the dotted-hierarchy form {@code -Dceltium.patch.<group>=off}, matching
 * {@code celtium.vulkan.stage.<id>} rather than the camelCase of {@code celtium.vulkan.pipelineCacheFile}. Decided
 * here in 356-A so the ledger does not have to choose; {@code -D} overrides never write a file.
 */
public final class PatchGroups {

    /** Package-relative prefix of every name in this table. */
    public static final String MIXIN_PACKAGE = "org.taumc.celeritas.mixin.";

    /** The fallback group: an unlisted name lands here, and this group is never quarantined. */
    public static final String MECHANISM = "mechanism";

    /** One patch group: the mixins that must be applied together and what is lost without them. */
    public static final class Group {
        private final String id;
        private final String title;
        private final boolean essential;
        private final String cost;
        private final List<String> members;

        private Group(String id, String title, boolean essential, String cost, List<String> members) {
            this.id = id;
            this.title = title;
            this.essential = essential;
            this.cost = cost;
            this.members = Collections.unmodifiableList(members);
        }

        /** {@return the group id as it appears in the kill switch ({@code celtium.patch.<id>}) and in diagnostics}. */
        public String id() {
            return this.id;
        }

        /** {@return the human-readable group name, for the log line and a future screen}. */
        public String title() {
            return this.title;
        }

        /** {@return true when the group may never be switched off or quarantined}. */
        public boolean essential() {
            return this.essential;
        }

        /** {@return one sentence on what is lost when this group is not applied}. */
        public String cost() {
            return this.cost;
        }

        /** {@return the group's mixins, as relative names below {@link #MIXIN_PACKAGE}}. */
        public List<String> members() {
            return this.members;
        }
    }

    private static final Map<String, Group> GROUPS = new LinkedHashMap<String, Group>();
    private static final Map<String, String> GROUP_OF_MIXIN = new LinkedHashMap<String, String>();

    static {
        table();
    }

    private PatchGroups() {
    }

    private static void group(String id, String title, boolean essential, String cost, String... members) {
        List<String> list = new ArrayList<String>(members.length);
        Collections.addAll(list, members);
        Group group = new Group(id, title, essential, cost, list);
        GROUPS.put(id, group);
        for (String member : members) {
            GROUP_OF_MIXIN.put(member, id);
        }
    }

    /**
     * {@return the id of the group the mixin belongs to; an unlisted name falls to {@code mechanism}, the safe side
     * (a group that cannot be switched off), so a table that falls behind a new mixin cannot make that mixin
     * quarantinable by accident}
     */
    public static String of(String mixinRelativeName) {
        String relative = mixinRelativeName == null ? "" : mixinRelativeName;
        if (relative.startsWith(MIXIN_PACKAGE)) {
            relative = relative.substring(MIXIN_PACKAGE.length());
        }
        String group = GROUP_OF_MIXIN.get(relative);
        return group == null ? MECHANISM : group;
    }

    /** {@return true when the group exists and may never be switched off or quarantined}. */
    public static boolean isEssential(String groupId) {
        Group group = GROUPS.get(groupId);
        return group != null && group.essential;
    }

    /** {@return true when the id names a group in this table}. */
    public static boolean isKnown(String groupId) {
        return GROUPS.containsKey(groupId);
    }

    /** {@return the group ids in table order, never null}. */
    public static List<String> groups() {
        return Collections.unmodifiableList(new ArrayList<String>(GROUPS.keySet()));
    }

    /** {@return the group, or null when the id is not in the table}. */
    public static Group get(String groupId) {
        return GROUPS.get(groupId);
    }

    /** {@return the group's title, or the id itself when the group is unknown}. */
    public static String title(String groupId) {
        Group group = GROUPS.get(groupId);
        return group == null ? groupId : group.title;
    }

    /** {@return the group's cost sentence, or an empty string when the group is unknown}. */
    public static String cost(String groupId) {
        Group group = GROUPS.get(groupId);
        return group == null ? "" : group.cost;
    }

    /**
     * The table. Group order is the order the deciding code reports in; membership is the design's 356-A split, with
     * the corrections the readiness audit forced: {@code terrain_geometry} and {@code textures} carry interfaces that
     * non-mixin code casts to, so they are essential rather than quarantinable, and {@code entities} (which the design
     * marks "to be audited") stays essential until that read is recorded. Only {@code text}, {@code particles},
     * {@code sky}, {@code shaders} and {@code tweaks} are quarantinable today.
     */
    private static void table() {
        group("mechanism", "renderer core", true,
              "nothing renders at all: this group is the renderer, so there is no configuration left in which celtium draws",
              "core.GlStateManagerMixin",
              "core.GlStateManagerFogMixin",
              "core.GlStateManagerImmediateMixin",
              "core.WorldVertexBufferUploaderMixin",
              "core.ClientArrayBufferOwnershipMixin",
              "core.VertexBufferMixin",
              "core.FramebufferMixin",
              "core.OpenGlHelperMixin",
              "core.GLAllocationMixin",
              "core.SplashProgressMixin",
              "core.LoadingScreenRendererMixin",
              "core.GluProjectionMixin",
              "core.ForgeHooksClientMixin",
              "core.LightmapCoordsMixin",
              "core.DynamicTextureMixin",
              "core.MinecraftMixin",
              "core.MinecraftSkipWorldWithoutCameraMixin",
              "core.ActiveRenderInfoViewPointGuardMixin",
              "core.EntityRendererCameraTransformGuardMixin",
              "core.EntityRendererSkipWorldWithoutCameraMixin",
              "core.EntityRendererMixin",
              "core.terrain.RenderGlobalMixin",
              "core.terrain.ActiveRenderInfoAccessor",
              "core.terrain.BlockColorsAccessor",
              "core.terrain.ChunkProviderClientAccessor",
              "core.terrain.ClientChunkManagerMixin",
              "core.terrain.WorldClientMixin",
              "core.terrain.ChunkEntityMembershipMixin",
              "core.terrain.BlockRendererDispatcherMixin",
              "core.BufferBuilderAccessor",
              "core.EntityRendererLightmapAccessor",
              "core.collections.ClassInheritanceMultiMapMixin",
              "core.frustum.FrustumMixin",
              "core.frustum.ClippingHelperImplMixin"
        );
        group("terrain_geometry", "terrain geometry", true,
              "model and quad capture stops and meshes fall back to the GL replay path, but the group also carries an interface that non-mixin code casts to (see the class javadoc), so removing it is a ClassCastException",
              "core.terrain.BakedQuadFactoryMixin",
              "core.terrain.BakedQuadMixin",
              "core.terrain.SimpleBakedModelBuilderMixin",
              "core.terrain.BiomeColorHelperMixin",
              "core.TexturedQuadMixin",
              "features.render.MultipartBakedModelMixin"
        );
        group("entities", "entities", true,
              "range limiting, the light cache and some TESRs return to the vanilla path; the design marks this group as still to be audited, so it stays essential until that read is done",
              "core.EntityItemRangeMixin",
              "core.EntityLightCacheMixin",
              "core.WorldCombinedLightMixin",
              "core.EntityPlayerMPMixin",
              "core.EntityRendererFogGuardMixin",
              "core.EntityRendererFrameStateProbeMixin",
              "features.options.MixinEntityRenderer",
              "core.TileEntityEndPortalRendererMixin",
              "core.TileEntitySignRendererMixin",
              "features.render.tileentity.TileEntityRenderDispatcherMixin",
              "features.render.tileentity.piston.TileEntityPistonRendererMixin"
        );
        group("textures", "textures", true,
              "parallel decoding and mip generation stop, but two interfaces here are cast by non-mixin code (SpriteUtil, VintageChunkBuildContext), so removing the group is a ClassCastException",
              "core.TextureMapParallelMixin",
              "core.TextureUtilMixin",
              "core.RenderItemMixin",
              "features.mipmaps.TextureAtlasSpriteMixin",
              "features.mipmaps.TextureUtilMixin",
              "features.textures.TextureAtlasMixin",
              "features.textures.TextureAtlasSpriteMixin",
              "features.textures.RenderItemMixin"
        );
        group("model_phase_instrument", "model phase instrument", false,
              "the CELTIUM_MODEL_PHASE phase lines (item 338a) stop; model loading itself is untouched because every injector is require=0 and read-only",
              "core.model.ModelManagerPhaseMixin",
              "core.model.ModelLoaderPhaseMixin",
              "core.model.ModelBakeryPhaseMixin",
              "core.model.BlockModelShapesPhaseMixin",
              "features.textures.TextureMapStitchPhaseMixin"
        );
        group("pack_phase_instrument", "pack phase instrument", false,
              "the wpPackPrepare / wpPackBegin / wpShadowPhase census brackets (item 648) and their item 690 children (wpShadowMatrix / wpShadowFrustum / wpShadowTerrainSetup / wpShadowTargetPrep inside the shadow phase, wpBeginUniforms / wpBeginTargets inside the frame begin) stop; rendering itself is untouched because every injector is require=0 and read-only, and the mixins are @Pseudo so they are silently off without Demonica anyway",
              "core.DemonicaPipelinePrepareTimingMixin",
              "core.DemonicaPipelineBeginTimingMixin",
              "core.DemonicaShadowPhaseTimingMixin",
              "core.DemonicaShadowSetupTimingMixin",
              "core.DemonicaPipelineBeginChildrenTimingMixin"
        );
        group("hud_census_instrument", "hud census instrument", false,
              "the screen bracket stops, so the pass census no longer separates the screen section from the frame residual; rendering itself is untouched because every injector is require=0 and read-only",
              "core.GuiScreenDrawMixin",
              "core.GuiContainerDrawMixin"
        );
        group("upstream_arrival_instrument", "upstream arrival instrument", false,
              "the CELTIUM_UPSTREAM series (item 727) loses its renders / mapRenders stage, so a run can no longer separate the renderer entry from the dispatch; rendering itself is untouched because the injector is require=0 and read-only, and the rest of the series counts from hooks in the mechanism groups",
              "core.RenderItemFrameArrivalMixin"
        );
        group("text", "text", false,
              "the font batcher stops and glyphs fall back to the generic recording through the GL stub (unmeasured)",
              "core.FontRendererMixin",
              "core.GuiIngameMixin",
              "core.GuiIngameForgeNoPlayerGuardMixin",
              "features.options.MixinGuiIngameForge"
        );
        group("particles", "particles", false,
              "particles fall back to the generic recording through the GL stub (unmeasured)",
              "core.ParticleMixin",
              "core.ParticleManagerMixin",
              "core.ParticleManagerAccessor",
              "core.ParticleDrawProbeMixin"
        );
        group("sky", "sky and weather", false,
              "cloud cellisation and rain capture stop (unmeasured)",
              "core.ForgeCloudCellsMixin",
              "core.CloudDrawProbeMixin",
              "core.RainDrawProbeMixin"
        );
        group("shaders", "mod shader chains", false,
              "the chain bridge stops; this is independent of the modPrograms opt-in (unmeasured)",
              "core.ShaderGroupMixin",
              "core.ShaderAccessor",
              "core.ShaderManagerAccessor",
              "core.ShaderUniformAccessor"
        );
        group("tweaks", "speed and diagnostics", false,
              "the light epoch, tick timing, join/watch observation, the funnel summary print, the empty outline composite skip, the EnumFacing.getFacingFromVector replacement, the Demonica shadow frustum init cache (E7 remaining branch, itself behind -Dceltium.shadowFrustumInitCache default off) and the options screen override disappear; the drawn picture is expected to be unchanged, which makes this the first injection candidate",
              "core.WorldLightEpochMixin",
              "core.MinecraftTickTimingMixin",
              "core.MinecraftTickSplitMixin",
              "core.WorldTickSplitMixin",
              "core.ParticleManagerTickSplitMixin",
              "core.ChunkProviderClientTickSplitMixin",
              "core.RenderGlobalTickSplitMixin",
              "core.MinecraftWorldAssignedWatchMixin",
              "core.MinecraftFunnelSummaryMixin",
              "core.NetHandlerJoinProbeMixin",
              "core.IntegratedServerShutdownMixin",
              "core.crash.SplashProgressCallableMixin",
              "core.RenderGlobalOutlineCompositeMixin",
              "core.DemonicaShadowFrustumInitCacheMixin",
              "core.MixinDirection",
              "features.options.MixinGuiOptions"
        );
    }
}
