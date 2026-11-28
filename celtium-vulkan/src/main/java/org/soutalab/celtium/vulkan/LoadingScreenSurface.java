package org.soutalab.celtium.vulkan;

import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import org.soutalab.celtium.harness.HarnessConfig;

/**
 * The loading screen's own surface (S3-b addendum 4-1): the seat that owns the native loading screen's
 * state - the {@link NativeLoadingScreen} instance and the one-shot presented-image dump - apart from the
 * terrain backend that used to host it inline. Forge's loader supplies the progress and the splash
 * configuration through {@code CeltiumLoadingProgress} (forge122); this surface draws the frame into its
 * own image and presents it through the window backend's swapchain.
 * <p>
 * The frame is laid out at {@link #layoutSize} rather than at the requested size the caller passes: those
 * are the game's cached display fields, frozen at the requested size for the whole load, while the
 * swapchain this presents through already follows the window. The S3-b dismantling cut this seat out of
 * the monolith ahead of the monolith's removal, and the v0.4.1 clean-room CLS grammar interpreter is
 * planned against this boundary (plan section 8.3, HUD/CLS ruling).
 */
public final class LoadingScreenSurface {
    private static final String FRAME_DUMP_DIR = HarnessConfig.path("frameDumpDir");
    private static final boolean DUMP_LOADING_PROGRESS =
            HarnessConfig.bool("dumpLoadingProgress", false);

    private NativeLoadingScreen screen;
    private boolean dumped;

    /**
     * The size the loading screen is laid out at: the swapchain's extent when it is usable, the requested
     * size otherwise.
     * <p>
     * The request is {@code Minecraft.displayWidth/displayHeight}, which the game initialises to the size
     * it asked the window for and does not refresh while FML loads - the only refresh path
     * ({@code updateDisplay()} reading {@code Display.getWidth()/getHeight()}) runs once before the
     * compositor has settled the window's real size, and then not again until the game loop starts. The
     * swapchain is the opposite: it is rebuilt until its extent matches the surface, and the present blit
     * scales the laid-out image to that whole extent, so laying out at a stale requested size under a
     * differently-sized window presents the splash anisotropically stretched (or clipped, before the first
     * rebuild) for the whole load - the winisol configuration, a 1280x720 request in a 626x1056 pane. The
     * swapchain extent is the area this present actually fills, so it is the layout's measure, and no new
     * native size query is added to the loading thread to get it. When the two agree - the ordinary
     * launch - the result is the requested size unchanged. A non-positive swapchain extent keeps the
     * requested size, as before this decision existed.
     */
    static int[] layoutSize(int requestedWidth, int requestedHeight, int swapchainWidth,
            int swapchainHeight) {
        if (swapchainWidth > 0 && swapchainHeight > 0) {
            return new int[] {swapchainWidth, swapchainHeight};
        }
        return new int[] {requestedWidth, requestedHeight};
    }

    /**
     * Presents one of Forge's loading frames through {@code swapchain}. The caller (the window backend)
     * owns the readiness guards - window-backend state, GPU stall, swapchain rebuild; this method assumes
     * a live swapchain.
     *
     * @param colors background, barBorder, bar, barBackground, memoryGood, memoryWarn, memoryLow (RGB)
     * @return the size the frame was laid out and presented at
     */
    public int[] present(VulkanContext context, VulkanSwapchain swapchain, int width, int height,
            int[] colors, boolean showMemory, boolean rotate, int logoOffset, BufferedImage logo,
            BufferedImage forge, BufferedImage font, String[] titles, String[] messages, int[] steps,
            int[] totals, int angle) {
        int[] size = layoutSize(width, height, swapchain.width(), swapchain.height());
        if (this.screen == null) this.screen = new NativeLoadingScreen(context);
        NativeLoadingScreen.Style style = new NativeLoadingScreen.Style();
        style.background = colors[0];
        style.barBorder = colors[1];
        style.bar = colors[2];
        style.barBackground = colors[3];
        style.memoryGood = colors[4];
        style.memoryWarn = colors[5];
        style.memoryLow = colors[6];
        style.showMemory = showMemory;
        style.rotate = rotate;
        style.logoOffset = logoOffset;
        NativeLoadingScreen.Bar[] bars = new NativeLoadingScreen.Bar[titles.length];
        for (int i = 0; i < bars.length; i++) {
            bars[i] = new NativeLoadingScreen.Bar(titles[i], messages[i], steps[i], totals[i]);
        }
        VulkanGlTexture image = this.screen.update(size[0], size[1], style, logo, forge, font, bars, angle);
        String directory = FRAME_DUMP_DIR;
        if (!this.dumped && directory != null
                && DUMP_LOADING_PROGRESS && steps.length > 0 && steps[0] > 0) {
            swapchain.requestPresentedImageDump(java.nio.file.Paths.get(directory, "forge-loading-progress.png"));
            this.dumped = true;
        }
        swapchain.presentImage(image.image(), image.samplingLayout(), size[0], size[1]);
        return size;
    }

    /**
     * Releases the native screen once loading is finished, when {@code settleGpu} reports the GPU has
     * settled; without a settle the screen is kept, exactly as the backend's inline form did.
     */
    public void finish(BooleanSupplier settleGpu) {
        if (this.screen != null && settleGpu.getAsBoolean()) {
            this.screen.delete();
            this.screen = null;
        }
    }

    /** Unconditional release, for the owning backend's full teardown (the GPU is already settled there). */
    public void delete() {
        if (this.screen != null) {
            this.screen.delete();
            this.screen = null;
        }
    }
}
