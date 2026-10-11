package org.taumc.celeritas.impl.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.lwjgl.opengl.Display;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.api.options.structure.StandardOptions;
import org.taumc.celeritas.impl.compat.modernui.MuiGuiScaleHook;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;

import java.util.ArrayList;
import java.util.List;

public class SodiumGameOptionPages {
    private static final SodiumGameOptions sodiumOpts = CeleritasVintage.options();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

    private static int computeMaxRangeForRenderDistance(@SuppressWarnings("SameParameterValue") int injectedRenderDistance) {
        return injectedRenderDistance;
    }

    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();
        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.RENDERING)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.RENDER_DISTANCE.cast())
                        .setName(TextComponent.literal(I18n.format("options.renderDistance")))
                        .setTooltip(TextComponent.translatable("sodium.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, computeMaxRangeForRenderDistance(32), 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((options, value) -> options.renderDistanceChunks = value, options -> options.renderDistanceChunks)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.BRIGHTNESS.cast())
                        .setName(TextComponent.translatable("options.gamma"))
                        .setTooltip(TextComponent.translatable("sodium.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((opts, value) -> opts.gammaSetting = (float) (value * 0.01D), (opts) -> (int) (opts.gammaSetting / 0.01D))
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.WINDOW)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.GUI_SCALE.cast())
                        .setName(TextComponent.translatable("options.guiScale"))
                        .setTooltip(TextComponent.translatable("sodium.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, MuiGuiScaleHook.getMaxGuiScale(), 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale = value;

                            Minecraft mc = Minecraft.getMinecraft();
                            mc.resize(mc.displayWidth, mc.displayHeight);
                        }, opts -> opts.guiScale)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.FULLSCREEN.cast())
                        .setName(TextComponent.translatable("options.fullscreen"))
                        .setTooltip(TextComponent.translatable("sodium.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.fullScreen = value;

                            Minecraft client = Minecraft.getMinecraft();

                            if (client.isFullScreen() != opts.fullScreen) {
                                client.toggleFullscreen();

                                // The client might not be able to enter full-screen mode
                                opts.fullScreen = client.isFullScreen();
                            }
                        }, (opts) -> opts.fullScreen)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VSYNC.cast())
                        .setName(TextComponent.translatable("options.vsync"))
                        .setTooltip(TextComponent.translatable("sodium.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.enableVsync = value;
                            Display.setVSyncEnabled(opts.enableVsync);
                            // Under the GL-less window there is no GL context, so the swap interval above changes
                            // nothing: the frame interval is the Vulkan swapchain's present mode, which is a
                            // creation parameter. Telling the swapchain is what makes the option take effect without
                            // a restart (task-56); it rebuilds on the next present with acquire/frame ownership going
                            // through the same out-of-date path a surface change uses.
                            org.soutalab.celtium.vulkan.VulkanSwapchain.setVSyncRequested(opts.enableVsync);
                        }, opts -> opts.enableVsync)
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MAX_FRAMERATE.cast())
                        .setName(TextComponent.translatable("options.framerateLimit"))
                        .setTooltip(TextComponent.translatable("sodium.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.INDICATORS)
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VIEW_BOBBING.cast())
                        .setName(TextComponent.translatable("options.viewBobbing"))
                        .setTooltip(TextComponent.translatable("sodium.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.viewBobbing = value, opts -> opts.viewBobbing)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.ATTACK_INDICATOR.cast())
                        .setName(TextComponent.translatable("options.attackIndicator"))
                        .setTooltip(TextComponent.translatable("sodium.options.attack_indicator.tooltip"))
                        .setControl(opts -> new CyclingControl<>(opts, new Integer[] { 0, 1, 2 }, new TextComponent[] {
                                TextComponent.translatable("options.off"),
                                TextComponent.translatable("options.attack.crosshair"),
                                TextComponent.translatable("options.attack.hotbar") }))
                        .setBinding((opts, value) -> opts.attackIndicator = value, (opts) -> opts.attackIndicator)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.GENERAL, TextComponent.translatable("stat.generalButton"), ImmutableList.copyOf(groups));
    }

    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.GRAPHICS)
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.GRAPHICS_MODE.cast())
                        .setName(TextComponent.translatable("options.graphics"))
                        .setTooltip(TextComponent.translatable("sodium.options.graphics_quality.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.fancyGraphics = value, opts -> opts.fancyGraphics)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.DETAILS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.CLOUDS.cast())
                        .setName(TextComponent.translatable("options.renderClouds"))
                        .setTooltip(TextComponent.translatable("sodium.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[] { 0, 1, 2}, new TextComponent[] {
                                TextComponent.translatable("options.off"),
                                TextComponent.translatable("options.clouds.fast"),
                                TextComponent.translatable("options.clouds.fancy") }))
                        .setBinding((opts, value) -> {
                            opts.clouds = value;
                        }, opts -> {
                            return opts.clouds;
                        })
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setId(StandardOptions.Option.WEATHER.cast())
                        .setName(TextComponent.translatable("soundCategory.weather"))
                        .setTooltip(TextComponent.translatable("sodium.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setId(StandardOptions.Option.LEAVES.cast())
                        .setName(TextComponent.translatable("sodium.options.leaves_quality.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.leaves_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.leavesQuality = value, opts -> opts.quality.leavesQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.PARTICLES.cast())
                        .setName(TextComponent.translatable("options.particles"))
                        .setTooltip(TextComponent.translatable("sodium.options.particle_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[] { 0, 1, 2}, new TextComponent[] {
                                TextComponent.translatable("options.particles.all"),
                                TextComponent.translatable( "options.particles.decreased"),
                                TextComponent.translatable("options.particles.minimal") }))
                        .setBinding((opts, value) -> opts.particleSetting = value, (opts) -> opts.particleSetting)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.SMOOTH_LIGHT.cast())
                        .setName(TextComponent.translatable("options.ao"))
                        .setTooltip(TextComponent.translatable("sodium.options.smooth_lighting.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[] { 0, 1, 2}, new TextComponent[] {
                                TextComponent.translatable("options.ao.off"),
                                TextComponent.translatable("options.ao.min"),
                                TextComponent.translatable("options.ao.max") }))
                        .setBinding((opts, value) -> opts.ambientOcclusion = value, opts -> opts.ambientOcclusion)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.BIOME_BLEND.cast())
                        .setName(TextComponent.translatable("sodium.options.biomeBlendRadius"))
                        .setTooltip(TextComponent.translatable("sodium.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 14, 1, ControlValueFormatter.biomeBlend()))
                        .setBinding((opts, value) -> opts.quality.legacyBiomeBlendRadius = value, opts -> opts.quality.legacyBiomeBlendRadius)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                // BH-33: the native renderer does not carry a chunk fade. Its terrain shader does declare
                // u_ChunkFadeEnabled (offset 60) and u_ChunkFadeParams (offset 64, x = duration ms, y = chunk age ms),
                // but the backend's writeUniforms(RenderRegion, TerrainRenderPass, CameraTransform) has no per-section
                // age to put in y, so it writes 0/0 and every section would fade by the same amount if a duration were
                // written. The real port needs a section-age source at the draw site (a terrain-path task). Until then
                // the option must not claim a duration it cannot carry: both binding directions are pinned to 0 and the
                // tooltip says so, rather than a slider that looks effective and changes nothing.
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CHUNK_FADE_IN_DURATION.cast())
                        .setName(TextComponent.translatable("celeritas.options.chunk_fade_in_duration.name"))
                        // The tooltip is a literal rather than the translated one because the sentence below is the
                        // point of this row now: the option is pinned to 0 and says so, instead of looking effective.
                        .setTooltip(TextComponent.literal(
                                "Chunk fade-in is not carried by the Vulkan renderer yet (it has no per-section chunk"
                                        + " age at the draw site), so the value is fixed at 0."))
                        // A pinned value is not a slider: SliderControl asserts max > min, so min == max threw
                        // "The maximum value must be greater than the minimum value" while the page was being built -
                        // which surfaced as an InvocationTargetException on the settings button's actionPerformed and
                        // dropped the whole Celeritas video-options screen back to vanilla's. A single-value cycling
                        // control says the same thing (the value is fixed at 0) without an illegal range.
                        .setControl(o -> new CyclingControl<>(o, new Integer[] { 0 },
                                new TextComponent[] { TextComponent.literal("0") }))
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.quality.chunkFadeInDuration = 0, opts -> 0)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.ENTITY_SHADOWS.cast())
                        .setName(TextComponent.translatable("options.entityShadows"))
                        .setTooltip(TextComponent.translatable("sodium.options.entity_shadows.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.entityShadows = value, opts -> opts.entityShadows)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.VIGNETTE.cast())
                        .setName(TextComponent.translatable("sodium.options.vignette.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());


        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.MIPMAPS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MIPMAP_LEVEL.cast())
                        .setName(TextComponent.translatable("options.mipmapLevels"))
                        .setTooltip(TextComponent.translatable("sodium.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels = value, opts -> opts.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.SORTING)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.TRANSLUCENT_FACE_SORTING.cast())
                        .setName(TextComponent.translatable("sodium.options.translucent_face_sorting.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.translucent_face_sorting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.VARIES)
                        .setBinding((opts, value) -> opts.performance.useTranslucentFaceSorting = value, opts -> opts.performance.useTranslucentFaceSorting)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(OptionIdentifier.create("celeritas", "fast_block_renderer", boolean.class))
                        .setName(TextComponent.literal("Use Fast Block Renderer"))
                        .setTooltip(TextComponent.literal("Enables the backported faster block renderer from modern Celeritas versions, which has better performance but may look slightly different."))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER = value, opts -> ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.QUALITY, TextComponent.translatable("sodium.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    public static OptionPage advanced() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.CPU_SAVING)
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CPU_FRAMES_AHEAD.cast())
                        .setName(TextComponent.translatable("sodium.options.cpu_render_ahead_limit.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.cpu_render_ahead_limit.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 9, 1, ControlValueFormatter.translateVariable("sodium.options.cpu_render_ahead_limit.value")))
                        .setBinding((opts, value) -> opts.advanced.cpuRenderAheadLimit = value, opts -> opts.advanced.cpuRenderAheadLimit)
                        .build()
                )
                .build());

        return new OptionPage(StandardOptions.Pages.ADVANCED, TextComponent.translatable("sodium.options.pages.advanced"), ImmutableList.copyOf(groups));
    }

    public static OptionStorage<GameSettings> getVanillaOpts() {
        return vanillaOpts;
    }

    public static OptionStorage<SodiumGameOptions> getSodiumOpts() {
        return sodiumOpts;
    }
}
