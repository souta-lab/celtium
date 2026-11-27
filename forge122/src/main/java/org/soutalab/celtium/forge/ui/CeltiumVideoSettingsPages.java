package org.soutalab.celtium.forge.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.Display;
import org.soutalab.celtium.forge.ui.OptionEntry.ConfigTarget;
import org.soutalab.celtium.forge.ui.OptionEntry.CycleOption;
import org.soutalab.celtium.forge.ui.OptionEntry.OptionGroup;
import org.soutalab.celtium.forge.ui.OptionEntry.OptionPage;
import org.soutalab.celtium.forge.ui.OptionEntry.SliderOption;
import org.soutalab.celtium.forge.ui.OptionEntry.ToggleOption;
import org.taumc.celeritas.impl.compat.modernui.MuiGuiScaleHook;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Defines Celtium's 5 video settings pages:
 * General, Quality, Performance, Lighting &amp; Culling, and Shaders (S5-b entry stub).
 */
public final class CeltiumVideoSettingsPages {
    private static final Set<OptionFlag> NONE = Collections.emptySet();
    private static final Set<OptionFlag> RENDERER_RELOAD = EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD);
    private static final Set<OptionFlag> RENDERER_UPDATE = EnumSet.of(OptionFlag.REQUIRES_RENDERER_UPDATE);
    private static final Set<OptionFlag> ASSET_RELOAD = EnumSet.of(OptionFlag.REQUIRES_ASSET_RELOAD);

    private CeltiumVideoSettingsPages() {
    }

    public static List<OptionPage> createPages(CeltiumConfig celtiumOpts, MinecraftOptionsStorage vanillaOpts) {
        return List.of(
                general(vanillaOpts),
                quality(celtiumOpts, vanillaOpts),
                performance(celtiumOpts),
                lightingAndCulling(celtiumOpts),
                shaders()
        );
    }

    private static OptionPage general(MinecraftOptionsStorage vanillaOpts) {
        OptionGroup rendering = new OptionGroup("celtium.options.groups.rendering", List.of(
                new SliderOption(
                        "options.renderDistance",
                        "celtium.options.view_distance.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.HIGH,
                        RENDERER_RELOAD,
                        2, 32, 1,
                        v -> I18n.format("options.chunks", v),
                        () -> vanillaOpts.getData().renderDistanceChunks,
                        v -> vanillaOpts.getData().renderDistanceChunks = v
                ),
                new SliderOption(
                        "options.gamma",
                        "celtium.options.brightness.tooltip",
                        ConfigTarget.VANILLA,
                        null,
                        NONE,
                        0, 100, 1,
                        CeltiumVideoSettingsPages::formatBrightness,
                        () -> MinecraftOptionsStorage.gammaToPercent(vanillaOpts.getData().gammaSetting),
                        v -> vanillaOpts.getData().gammaSetting = MinecraftOptionsStorage.percentToGamma(v)
                )
        ));

        OptionGroup window = new OptionGroup("celtium.options.groups.window", List.of(
                new SliderOption(
                        "options.guiScale",
                        "celtium.options.gui_scale.tooltip",
                        ConfigTarget.VANILLA,
                        null,
                        NONE,
                        0, Math.max(1, MuiGuiScaleHook.getMaxGuiScale()), 1,
                        v -> v == 0 ? I18n.format("options.guiScale.auto") : v + "x",
                        () -> vanillaOpts.getData().guiScale,
                        v -> {
                            vanillaOpts.getData().guiScale = v;
                            Minecraft mc = Minecraft.getMinecraft();
                            mc.resize(mc.displayWidth, mc.displayHeight);
                        }
                ),
                new ToggleOption(
                        "options.fullscreen",
                        "celtium.options.fullscreen.tooltip",
                        ConfigTarget.VANILLA,
                        null,
                        NONE,
                        () -> vanillaOpts.getData().fullScreen,
                        v -> {
                            vanillaOpts.getData().fullScreen = v;
                            Minecraft client = Minecraft.getMinecraft();
                            if (client.isFullScreen() != v) {
                                client.toggleFullscreen();
                                vanillaOpts.getData().fullScreen = client.isFullScreen();
                            }
                        }
                ),
                new ToggleOption(
                        "options.vsync",
                        "celtium.options.v_sync.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.VARIES,
                        NONE,
                        () -> vanillaOpts.getData().enableVsync,
                        v -> {
                            vanillaOpts.getData().enableVsync = v;
                            Display.setVSyncEnabled(v);
                            // Under the GL-less window there is no GL context; notify the Vulkan swapchain
                            // directly so VSync takes effect without a restart.
                            org.soutalab.celtium.vulkan.VulkanSwapchain.setVSyncRequested(v);
                        }
                ),
                new SliderOption(
                        "options.framerateLimit",
                        "celtium.options.fps_limit.tooltip",
                        ConfigTarget.VANILLA,
                        null,
                        NONE,
                        10, 260, 10,
                        CeltiumVideoSettingsPages::formatFpsLimit,
                        () -> vanillaOpts.getData().limitFramerate,
                        v -> vanillaOpts.getData().limitFramerate = v
                )
        ));

        OptionGroup indicators = new OptionGroup("celtium.options.groups.indicators", List.of(
                new ToggleOption(
                        "options.viewBobbing",
                        "celtium.options.view_bobbing.tooltip",
                        ConfigTarget.VANILLA,
                        null,
                        NONE,
                        () -> vanillaOpts.getData().viewBobbing,
                        v -> vanillaOpts.getData().viewBobbing = v
                ),
                new CycleOption<>(
                        "options.attackIndicator",
                        "celtium.options.attack_indicator.tooltip",
                        ConfigTarget.VANILLA,
                        null,
                        NONE,
                        List.of(0, 1, 2),
                        v -> switch (v) {
                            case 1 -> I18n.format("options.attack.crosshair");
                            case 2 -> I18n.format("options.attack.hotbar");
                            default -> I18n.format("options.off");
                        },
                        () -> vanillaOpts.getData().attackIndicator,
                        v -> vanillaOpts.getData().attackIndicator = v
                )
        ));

        return new OptionPage("general", "celtium.options.pages.general", List.of(rendering, window, indicators));
    }

    private static OptionPage quality(CeltiumConfig celtiumOpts, MinecraftOptionsStorage vanillaOpts) {
        OptionGroup graphics = new OptionGroup("celtium.options.groups.graphics", List.of(
                new ToggleOption(
                        "options.graphics",
                        "celtium.options.graphics_quality.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.HIGH,
                        RENDERER_RELOAD,
                        () -> vanillaOpts.getData().fancyGraphics,
                        v -> vanillaOpts.getData().fancyGraphics = v
                )
        ));

        OptionGroup details = new OptionGroup("celtium.options.groups.details", List.of(
                new CycleOption<>(
                        "options.renderClouds",
                        "celtium.options.clouds_quality.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.LOW,
                        NONE,
                        List.of(0, 1, 2),
                        v -> switch (v) {
                            case 1 -> I18n.format("options.clouds.fast");
                            case 2 -> I18n.format("options.clouds.fancy");
                            default -> I18n.format("options.off");
                        },
                        () -> vanillaOpts.getData().clouds,
                        v -> vanillaOpts.getData().clouds = v
                ),
                new CycleOption<>(
                        "soundCategory.weather",
                        "celtium.options.weather_quality.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.MEDIUM,
                        NONE,
                        Arrays.asList(CeltiumConfig.GraphicsQuality.values()),
                        q -> I18n.format(q.translationKey()),
                        () -> celtiumOpts.quality.weatherQuality,
                        v -> celtiumOpts.quality.weatherQuality = v
                ),
                new CycleOption<>(
                        "celtium.options.leaves_quality.name",
                        "celtium.options.leaves_quality.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.MEDIUM,
                        RENDERER_RELOAD,
                        Arrays.asList(CeltiumConfig.GraphicsQuality.values()),
                        q -> I18n.format(q.translationKey()),
                        () -> celtiumOpts.quality.leavesQuality,
                        v -> celtiumOpts.quality.leavesQuality = v
                ),
                new CycleOption<>(
                        "options.particles",
                        "celtium.options.particle_quality.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.MEDIUM,
                        NONE,
                        List.of(0, 1, 2),
                        v -> switch (v) {
                            case 1 -> I18n.format("options.particles.decreased");
                            case 2 -> I18n.format("options.particles.minimal");
                            default -> I18n.format("options.particles.all");
                        },
                        () -> vanillaOpts.getData().particleSetting,
                        v -> vanillaOpts.getData().particleSetting = v
                ),
                new CycleOption<>(
                        "options.ao",
                        "celtium.options.smooth_lighting.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.LOW,
                        RENDERER_RELOAD,
                        List.of(0, 1, 2),
                        v -> switch (v) {
                            case 1 -> I18n.format("options.ao.min");
                            case 2 -> I18n.format("options.ao.max");
                            default -> I18n.format("options.ao.off");
                        },
                        () -> vanillaOpts.getData().ambientOcclusion,
                        v -> vanillaOpts.getData().ambientOcclusion = v
                ),
                new SliderOption(
                        "celtium.options.biome_blend.name",
                        "celtium.options.biome_blend.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        RENDERER_RELOAD,
                        0, 14, 1,
                        v -> v == 0 ? I18n.format("options.off")
                                : I18n.format("celtium.options.biome_blend.value", v),
                        () -> celtiumOpts.quality.legacyBiomeBlendRadius,
                        v -> celtiumOpts.quality.legacyBiomeBlendRadius = v
                ),
                new ToggleOption(
                        "options.entityShadows",
                        "celtium.options.entity_shadows.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.LOW,
                        NONE,
                        () -> vanillaOpts.getData().entityShadows,
                        v -> vanillaOpts.getData().entityShadows = v
                ),
                new ToggleOption(
                        "celtium.options.vignette.name",
                        "celtium.options.vignette.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        NONE,
                        () -> celtiumOpts.quality.enableVignette,
                        v -> celtiumOpts.quality.enableVignette = v
                )
        ));

        OptionGroup mipmaps = new OptionGroup("celtium.options.groups.mipmaps", List.of(
                new SliderOption(
                        "options.mipmapLevels",
                        "celtium.options.mipmap_levels.tooltip",
                        ConfigTarget.VANILLA,
                        OptionImpact.MEDIUM,
                        ASSET_RELOAD,
                        0, 4, 1,
                        v -> v == 0 ? I18n.format("options.off") : v + "x",
                        () -> vanillaOpts.getData().mipmapLevels,
                        v -> vanillaOpts.getData().mipmapLevels = v
                )
        ));

        OptionGroup sorting = new OptionGroup("celtium.options.groups.sorting", List.of(
                new ToggleOption(
                        "celtium.options.translucent_face_sorting.name",
                        "celtium.options.translucent_face_sorting.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.VARIES,
                        RENDERER_RELOAD,
                        () -> celtiumOpts.performance.useTranslucentFaceSorting,
                        v -> celtiumOpts.performance.useTranslucentFaceSorting = v
                )
        ));

        return new OptionPage("quality", "celtium.options.pages.quality", List.of(graphics, details, mipmaps, sorting));
    }

    private static OptionPage performance(CeltiumConfig celtiumOpts) {
        int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        OptionGroup chunkUpdates = new OptionGroup("celtium.options.groups.chunk_updates", List.of(
                new SliderOption(
                        "celtium.options.chunk_update_threads.name",
                        "celtium.options.chunk_update_threads.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.HIGH,
                        RENDERER_RELOAD,
                        0, maxThreads, 1,
                        v -> v == 0 ? I18n.format("celtium.options.quality.default")
                                : I18n.format("celtium.options.chunk_update_threads.value", v),
                        () -> celtiumOpts.performance.chunkBuilderThreads,
                        v -> celtiumOpts.performance.chunkBuilderThreads = v
                ),
                new ToggleOption(
                        "celtium.options.always_defer_chunk_updates.name",
                        "celtium.options.always_defer_chunk_updates.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.HIGH,
                        RENDERER_UPDATE,
                        () -> celtiumOpts.performance.alwaysDeferChunkUpdates,
                        v -> celtiumOpts.performance.alwaysDeferChunkUpdates = v
                )
        ));

        OptionGroup renderingCulling = new OptionGroup("celtium.options.groups.rendering_culling", List.of(
                new ToggleOption(
                        "celtium.options.use_block_face_culling.name",
                        "celtium.options.use_block_face_culling.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.MEDIUM,
                        RENDERER_RELOAD,
                        () -> celtiumOpts.performance.useBlockFaceCulling,
                        v -> celtiumOpts.performance.useBlockFaceCulling = v
                ),
                new ToggleOption(
                        "celtium.options.use_fog_occlusion.name",
                        "celtium.options.use_fog_occlusion.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.MEDIUM,
                        RENDERER_UPDATE,
                        () -> celtiumOpts.performance.useFogOcclusion,
                        v -> celtiumOpts.performance.useFogOcclusion = v
                ),
                new ToggleOption(
                        "celtium.options.use_entity_culling.name",
                        "celtium.options.use_entity_culling.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.MEDIUM,
                        NONE,
                        () -> celtiumOpts.performance.useEntityCulling,
                        v -> celtiumOpts.performance.useEntityCulling = v
                ),
                new ToggleOption(
                        "celtium.options.animate_only_visible_textures.name",
                        "celtium.options.animate_only_visible_textures.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.HIGH,
                        RENDERER_UPDATE,
                        () -> celtiumOpts.performance.animateOnlyVisibleTextures,
                        v -> celtiumOpts.performance.animateOnlyVisibleTextures = v
                )
        ));

        OptionGroup cpuSaving = new OptionGroup("celtium.options.groups.cpu_saving", List.of(
                new SliderOption(
                        "celtium.options.cpu_render_ahead_limit.name",
                        "celtium.options.cpu_render_ahead_limit.tooltip",
                        ConfigTarget.CELTIUM,
                        null,
                        NONE,
                        0, 9, 1,
                        v -> I18n.format("celtium.options.cpu_render_ahead_limit.value", v),
                        () -> celtiumOpts.advanced.cpuRenderAheadLimit,
                        v -> celtiumOpts.advanced.cpuRenderAheadLimit = v
                ),
                new ToggleOption(
                        "celtium.options.idle_frame_cap.name",
                        "celtium.options.idle_frame_cap.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        NONE,
                        () -> celtiumOpts.performance.useIdleFrameCap,
                        v -> celtiumOpts.performance.useIdleFrameCap = v
                ),
                new SliderOption(
                        "celtium.options.unfocused_frame_limit.name",
                        "celtium.options.unfocused_frame_limit.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        NONE,
                        10, 260, 10,
                        CeltiumVideoSettingsPages::formatFpsLimit,
                        () -> celtiumOpts.performance.unfocusedFrameLimit,
                        v -> celtiumOpts.performance.unfocusedFrameLimit = v
                ),
                new SliderOption(
                        "celtium.options.idle_frame_limit.name",
                        "celtium.options.idle_frame_limit.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        NONE,
                        10, 260, 10,
                        CeltiumVideoSettingsPages::formatFpsLimit,
                        () -> celtiumOpts.performance.idleFrameLimit,
                        v -> celtiumOpts.performance.idleFrameLimit = v
                ),
                new SliderOption(
                        "celtium.options.idle_delay_seconds.name",
                        "celtium.options.idle_delay_seconds.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        NONE,
                        30, 1800, 30,
                        v -> I18n.format("celtium.options.idle_delay_seconds.value", v),
                        () -> celtiumOpts.performance.idleDelaySeconds,
                        v -> celtiumOpts.performance.idleDelaySeconds = v
                )
        ));

        return new OptionPage("performance", "celtium.options.pages.performance",
                List.of(chunkUpdates, renderingCulling, cpuSaving));
    }

    private static OptionPage lightingAndCulling(CeltiumConfig celtiumOpts) {
        OptionGroup dynamicLights = new OptionGroup("celtium.options.groups.lighting", List.of(
                new ToggleOption(
                        "celtium.options.dynamic_lights.name",
                        "celtium.options.dynamic_lights.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.LOW,
                        NONE,
                        () -> celtiumOpts.lighting.enableDynamicLights,
                        v -> celtiumOpts.lighting.enableDynamicLights = v
                )
        ));

        OptionGroup foliageCulling = new OptionGroup("celtium.options.groups.foliage_culling", List.of(
                new CycleOption<>(
                        "celtium.options.leaf_culling_mode.name",
                        "celtium.options.leaf_culling_mode.tooltip",
                        ConfigTarget.CELTIUM,
                        OptionImpact.MEDIUM,
                        RENDERER_RELOAD,
                        Arrays.asList(CeltiumConfig.LeafCullingMode.values()),
                        m -> I18n.format(m.translationKey()),
                        () -> celtiumOpts.quality.leafCullingMode,
                        v -> celtiumOpts.quality.leafCullingMode = v
                )
        ));

        return new OptionPage("lighting_culling", "celtium.options.pages.lighting_culling",
                List.of(dynamicLights, foliageCulling));
    }

    private static OptionPage shaders() {
        return new OptionPage(
                "shaders",
                "celtium.options.pages.shaders",
                Collections.emptyList(),
                "celtium.options.shaders.placeholder"
        );
    }

    private static String formatBrightness(int v) {
        if (v == 0) {
            return I18n.format("options.gamma.min");
        } else if (v == 100) {
            return I18n.format("options.gamma.max");
        } else {
            return v + "%";
        }
    }

    private static String formatFpsLimit(int v) {
        return v >= 260 ? I18n.format("options.framerateLimit.max") : v + " FPS";
    }
}
