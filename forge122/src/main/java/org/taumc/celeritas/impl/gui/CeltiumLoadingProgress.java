package org.taumc.celeritas.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.fml.common.ProgressManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileReader;
import java.util.Iterator;
import java.util.Properties;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Draws the mod-loading progress on the thread that owns it and presents it through the renderer, because
 * {@code SplashProgress} is structurally absent under this renderer: Forge's splash needs a shared GL context on the
 * display's drawable, and there is no drawable to share a context with when the window has no GL at all. The frame
 * itself mirrors {@code SplashProgress.run}'s own layout - the same centered (320,240) ortho space, the
 * splash.properties palette, the 512-pixel logo, the first-plus-last-two ProgressManager bars and the memory bar -
 * so what the user sees is the same screen Forge would have drawn, fed by the same ProgressManager data.
 * <p>
 * Everything else about loading is unchanged - a failed or absent renderer only ever skips the overlay, never
 * aborts loading.
 */
public final class CeltiumLoadingProgress {

    private CeltiumLoadingProgress() {
    }

    /** Back-offs for the classpath fetches while the default pack is not registered yet. */
    private static long logoRetryAfterNanos;
    private static long forgeRetryAfterNanos;
    /** The framebuffer bounce, kept across frames so a progress tick does not allocate a target every time. */
    private static Framebuffer splashTarget;
    private static int splashWidth;
    private static int splashHeight;
    private static long nextDrawNanos;
    /** ~30 fps - the cadence Forge's own splash drew at, so the overlay costs what the real one did. */
    private static final long DRAW_INTERVAL_NANOS = 33_000_000L;
    private static int failureLogs;
    /** The frame index the real splash rotates and frame-picks the forge logo by; kept at the same cadence. */
    private static int angle;
    /** The splash.properties palette, loaded once; absent or unreadable values keep Forge's own defaults. */
    private static Properties splashProps;
    private static boolean splashPropsRead;

    private static int colorOf(String key, int fallback) {
        if (!splashPropsRead) {
            splashPropsRead = true;
            splashProps = new Properties();
            File file = new File(Minecraft.getMinecraft().gameDir, "config/splash.properties");
            if (file.isFile()) {
                try (FileReader reader = new FileReader(file)) {
                    splashProps.load(reader);
                } catch (IOException ignored) {
                }
            }
        }
        String value = splashProps == null ? null : splashProps.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.decode(value.trim()) & 0xFFFFFF;
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    private static boolean boolOf(String key, boolean fallback) {
        if (!splashPropsRead) {
            colorOf("background", 0xFFFFFF);
        }
        String value = splashProps == null ? null : splashProps.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    /** A bar transition (push/pop): always draws - a new stage is worth a frame even inside the throttle window. */
    public static void drawNow() {
        draw(true);
    }

    /** A bar step: draws at most every {@link #DRAW_INTERVAL_NANOS}, so a thousand steps do not stall loading. */
    public static void drawIfDue() {
        draw(false);
    }

    private static void draw(boolean force) {
        if (!Boolean.getBoolean("celtium.vulkan.glless")) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // The client thread only: world-gen progress bars fire ProgressManager on the Server thread, and an
        // updateDisplay from there records on the same Vulkan command pool as the render thread - measured as a
        // THREADING ERROR + SIGSEGV in libvulkan_radeon on leg menu3-amd. A server-side tick just skips the draw;
        // the next client-side transition draws the bars' latest state anyway.
        if (mc == null || !mc.isCallingFromMinecraftThread()) {
            return;
        }
        boolean haveText = mc.fontRenderer != null;
        boolean haveTextures = mc.getTextureManager() != null && mc.getResourceManager() != null;
        long now = System.nanoTime();
        if (!force && now < nextDrawNanos) {
            return;
        }
        nextDrawNanos = now + DRAW_INTERVAL_NANOS;
        try {
            drawFrame(mc, haveText, haveTextures);
        } catch (Throwable error) {
            // The overlay is the least important thing loading does: report the first few failures and keep loading.
            if (failureLogs < 5) {
                failureLogs++;
                System.out.println("[Celtium] loading progress draw failed: " + error);
            }
        }
    }

    /**
     * Uploads one classpath asset as a DynamicTexture: the resource manager exists during loadMods but the default
     * pack is not registered in it yet, so getResource throws every frame (measured) - the jar's own asset serves
     * the same bytes.
     */
    private static ResourceLocation uploadClasspathTexture(Minecraft mc, String classpathPath, String name,
            DynamicTexture[] slotTexture, ResourceLocation[] slotLocation) {
        try (InputStream stream = CeltiumLoadingProgress.class.getResourceAsStream(classpathPath)) {
            if (stream == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(stream);
            DynamicTexture texture = new DynamicTexture(image);
            slotTexture[0] = texture;
            slotLocation[0] = mc.getTextureManager().getDynamicTextureLocation(name, texture);
            if ("celtium_forge_logo".equals(name)) {
                // One frame is one square the sprite's width tall, exactly as SplashProgress's Texture counts them.
                forgeFrames = Math.max(1, image.getHeight() / Math.max(1, image.getWidth()));
            }
            return slotLocation[0];
        } catch (IOException missing) {
            return null;
        }
    }

    private static final DynamicTexture[] LOGO_SLOT = new DynamicTexture[1];
    private static final ResourceLocation[] LOGO_LOC = new ResourceLocation[1];
    private static final DynamicTexture[] FORGE_SLOT = new DynamicTexture[1];
    private static final ResourceLocation[] FORGE_LOC = new ResourceLocation[1];
    /** forge.png is a vertical strip of square frames; drawing 0..1 in v stretches the whole strip into stripes. */
    private static int forgeFrames = 1;

    private static void drawFrame(Minecraft mc, boolean haveText, boolean haveTextures) throws IOException {
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        if (haveTextures) {
            if (LOGO_LOC[0] == null && logoRetryAfterNanos < System.nanoTime()) {
                uploadClasspathTexture(mc, "/assets/minecraft/textures/gui/title/mojang.png",
                        "celtium_mojang_logo", LOGO_SLOT, LOGO_LOC);
                if (LOGO_LOC[0] == null) {
                    logoRetryAfterNanos = System.nanoTime() + 1_000_000_000L;
                }
            }
            if (FORGE_LOC[0] == null && forgeRetryAfterNanos < System.nanoTime()) {
                String forgeTex = splashProps == null ? null : splashProps.getProperty("forgeTexture");
                uploadClasspathTexture(mc, "/assets/fml/textures/gui/forge.png", "celtium_forge_logo",
                        FORGE_SLOT, FORGE_LOC);
                if (FORGE_LOC[0] == null) {
                    forgeRetryAfterNanos = System.nanoTime() + 1_000_000_000L;
                }
            }
        }
        if (splashTarget == null || splashWidth != w || splashHeight != h) {
            if (splashTarget != null) {
                splashTarget.deleteFramebuffer();
            }
            splashTarget = new Framebuffer(w, h, true);
            splashWidth = w;
            splashHeight = h;
        }
        // A failure mid-frame must still unbind the bounce target: measured (legs e2e-hurt5/hurt6), an exception
        // between bindFramebuffer and unbindFramebuffer leaves every later game frame drawing into the orphaned
        // target and the run never reaches the world.
        boolean drewBars = false;
        splashTarget.bindFramebuffer(false);
        try {
            // SplashProgress's own coordinate space: a (320,240)-centred ortho that fills the display whatever its
            // aspect is, so the logo, bars and corner art land where the real splash puts them.
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(320 - w / 2.0D, 320 + w / 2.0D, 240 + h / 2.0D, 240 - h / 2.0D, -1.0D, 1.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

            int background = colorOf("background", 0xFFFFFF);
            int fontColor = colorOf("font", 0x000000);
            int barBorder = colorOf("barBorder", 0xC0C0C0);
            int barFill = colorOf("bar", 0xCB3D35);
            int barBackground = colorOf("barBackground", 0xFFFFFF);
            boolean showMemory = boolOf("showMemory", true);
            int logoOffset = 0;
            if (splashProps != null) {
                try {
                    logoOffset = Integer.parseInt(splashProps.getProperty("logoOffset", "0").trim());
                } catch (NumberFormatException ignored) {
                }
            }

            // The background: a full-screen fill in the properties' background colour, the real splash's glClear.
            fillQuad(320 - w / 2.0F, 240 - h / 2.0F, 320 + w / 2.0F, 240 + h / 2.0F, background);

            // The Mojang logo: 512x512 centred, tinted by the background colour exactly as SplashProgress does.
            if (LOGO_LOC[0] != null) {
                mc.getTextureManager().bindTexture(LOGO_LOC[0]);
                tintedTextureQuad(320 - 256, 240 - 256, 320 + 256, 240 + 256, 0.0F, 1.0F, 0.0F, 1.0F,
                        background);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }

            // The bars: the real splash draws the first bar plus the last two, at (320-barWidth/2, 310) stepping 55.
            ProgressManager.ProgressBar first = null, penult = null, last = null;
            Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
            while (it.hasNext()) {
                ProgressManager.ProgressBar bar = it.next();
                if (first == null) {
                    first = bar;
                } else {
                    penult = last;
                    last = bar;
                }
            }
            if (first != null) {
                int barWidth = 400;
                int y0 = 310;
                GlStateManager.pushMatrix();
                GlStateManager.translate(320 - barWidth / 2.0F, y0, 0.0F);
                drewBars |= drawBar(mc, first, barWidth, barBorder, barBackground, barFill, fontColor, haveText);
                if (penult != null) {
                    GlStateManager.translate(0.0F, 55.0F, 0.0F);
                    drewBars |= drawBar(mc, penult, barWidth, barBorder, barBackground, barFill, fontColor, haveText);
                }
                if (last != null) {
                    GlStateManager.translate(0.0F, 55.0F, 0.0F);
                    drewBars |= drawBar(mc, last, barWidth, barBorder, barBackground, barFill, fontColor, haveText);
                }
                GlStateManager.popMatrix();
            }

            // The memory bar on top, when the properties ask for it - the real splash's own meter.
            if (showMemory && haveText) {
                drawMemoryBar(mc, 320 - 200, 20, fontColor);
            }

            // The Forge logo in the bottom-right corner, rotating when the properties ask, at the splash's cadence.
            angle++;
            if (FORGE_LOC[0] != null) {
                int fw = 128;
                // The animated logo: SplashProgress picks frame (angle/5) % frames and maps v into that frame's
                // slice of the strip.
                int frame = (angle / 5) % forgeFrames;
                float fv0 = frame / (float) forgeFrames;
                float fv1 = (frame + 1) / (float) forgeFrames;
                if (boolOf("rotate", false)) {
                    float sh = fw;
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(320 + w / 2.0F - sh - logoOffset,
                            240 + h / 2.0F - sh - logoOffset, 0.0F);
                    GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
                    mc.getTextureManager().bindTexture(FORGE_LOC[0]);
                    tintedTextureQuad(-fw / 2.0F, -fw / 2.0F, fw / 2.0F, fw / 2.0F,
                            0.0F, 1.0F, fv0, fv1, 0xFFFFFF);
                    GlStateManager.popMatrix();
                } else {
                    mc.getTextureManager().bindTexture(FORGE_LOC[0]);
                    tintedTextureQuad(320 + w / 2.0F - fw - logoOffset,
                            240 + h / 2.0F - fw - logoOffset,
                            320 + w / 2.0F - logoOffset, 240 + h / 2.0F - logoOffset,
                            0.0F, 1.0F, fv0, fv1, 0xFFFFFF);
                }
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }

            GlStateManager.disableLighting();
            GlStateManager.disableFog();
        } finally {
            splashTarget.unbindFramebuffer();
        }
        splashTarget.framebufferRender(w, h);
        // Through Minecraft.updateDisplay, not Display.update directly: the fixture's presented-frame counter and
        // its dump latch live on that method, and a bare Display.update() presents the frame without counting it -
        // measured on leg plain-hurt5, where the mod-loading request stayed pending until the game's own second
        // present and captured a vanilla frame instead of the overlay.
        mc.updateDisplay();
        if (drewBars && ++overlayFrames >= MIN_DUMPPED_FRAME) {
            requestFixtureDump();
        }
    }

    /** An untextured quad fill in the (320,240) space - the SplashProgress {@code drawBox}. */
    private static void fillQuad(float x0, float y0, float x1, float y1, int rgb) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(x0, y1, 0.0D).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).endVertex();
        buffer.pos(x1, y1, 0.0D).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).endVertex();
        buffer.pos(x1, y0, 0.0D).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).endVertex();
        buffer.pos(x0, y0, 0.0D).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).endVertex();
        tessellator.draw();
    }

    /** A textured quad tinted by a 0xRRGGBB colour, the way SplashProgress tints the logo by the background. */
    private static void tintedTextureQuad(float x0, float y0, float x1, float y1,
            float u0, float u1, float v0, float v1, int rgb) {
        GlStateManager.color(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F,
                1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x0, y1, 0.0D).tex(u0, v1).endVertex();
        buffer.pos(x1, y1, 0.0D).tex(u1, v1).endVertex();
        buffer.pos(x1, y0, 0.0D).tex(u1, v0).endVertex();
        buffer.pos(x0, y0, 0.0D).tex(u0, v0).endVertex();
        tessellator.draw();
    }

    private static final int BAR_WIDTH = 400;
    private static final int BAR_HEIGHT = 20;
    private static final int TEXT_HEIGHT = 20;

    /**
     * One ProgressBar the way SplashProgress draws it: title above, a bordered box with the fill in the properties'
     * bar colour, and the n/m count centred inside.
     */
    private static boolean drawBar(Minecraft mc, ProgressManager.ProgressBar b, int barWidth, int barBorder,
            int barBackground, int barFill, int fontColor, boolean haveText) {
        GlStateManager.pushMatrix();
        if (haveText) {
            GlStateManager.scale(2.0F, 2.0F, 1.0F);
            String title = b.getTitle() + " - " + b.getMessage();
            mc.fontRenderer.drawString(title, 0, 0, fontColor);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
        }
        GlStateManager.translate(0.0F, TEXT_HEIGHT, 0.0F);
        fillQuad(0, 0, barWidth, BAR_HEIGHT, barBorder);
        GlStateManager.translate(1.0F, 1.0F, 0.0F);
        fillQuad(0, 0, barWidth - 2, BAR_HEIGHT - 2, barBackground);
        int steps = Math.max(1, b.getSteps());
        int step = Math.min(steps, b.getStep());
        fillQuad(0, 0, (barWidth - 2) * (step + 1) / (steps + 1), BAR_HEIGHT - 2, barFill);
        if (haveText) {
            String progress = step + "/" + steps;
            GlStateManager.translate((barWidth - 2) / 2.0F - mc.fontRenderer.getStringWidth(progress), 2.0F, 0.0F);
            GlStateManager.scale(2.0F, 2.0F, 1.0F);
            mc.fontRenderer.drawString(progress, 0, 0, fontColor);
        }
        GlStateManager.popMatrix();
        return true;
    }

    /** SplashProgress's own memory meter: used/total over a usage-coloured fill. */
    private static void drawMemoryBar(Minecraft mc, float x, float y, int fontColor) {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024);
        int totalMemory = (int) (Runtime.getRuntime().totalMemory() / 1024 / 1024);
        int freeMemory = (int) (Runtime.getRuntime().freeMemory() / 1024 / 1024);
        int usedMemory = totalMemory - freeMemory;
        float usedPercent = usedMemory / (float) maxMemory;
        int memoryColor = usedPercent < 0.75F ? colorOf("memoryGood", 0x78CB34)
                : usedPercent < 0.85F ? colorOf("memoryWarn", 0xE6E84A) : colorOf("memoryLow", 0xE42F2F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(2.0F, 2.0F, 1.0F);
        mc.fontRenderer.drawString("Memory Used / Total", 0, 0, fontColor);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + TEXT_HEIGHT, 0.0F);
        fillQuad(0, 0, BAR_WIDTH, BAR_HEIGHT, colorOf("barBorder", 0xC0C0C0));
        GlStateManager.translate(1.0F, 1.0F, 0.0F);
        fillQuad(0, 0, BAR_WIDTH - 2, BAR_HEIGHT - 2, colorOf("barBackground", 0xFFFFFF));
        fillQuad(0, 0, (int) ((BAR_WIDTH - 2) * usedPercent), BAR_HEIGHT - 2, memoryColor);
        String usage = usedMemory + "MB / " + maxMemory + "MB";
        GlStateManager.translate((BAR_WIDTH - 2) / 2.0F - mc.fontRenderer.getStringWidth(usage), 2.0F, 0.0F);
        GlStateManager.scale(2.0F, 2.0F, 1.0F);
        mc.fontRenderer.drawString(usage, 0, 0, fontColor);
        GlStateManager.popMatrix();
    }

    /** The earliest overlay frame worth capturing: deep enough that the fixture's presented-dump lands on an overlay
     * present rather than being held to the game's first (logo-only) present, which is what measured as the held
     * dump resolving on the vanilla frame in leg e2e-hurt10.
     */
    private static final int MIN_DUMPPED_FRAME = 30;
    private static int overlayFrames;

    /**
     * Asks the validation fixture, when it is constructed, to latch a presented-frame dump of the overlay - the
     * {@code loading} stage captures the first present (the static logo), so a progress frame needs its own stage.
     * <p>
     * The lookup deliberately goes through the mod list, not {@code Class.forName}: the overlay draws during mod
     * discovery and construction, when Foundation's class loader has not made mod classes loadable yet, and
     * measured (legs e2e-hurt6 through hurt9) a {@code Class.forName} failure at that point lands the class in
     * {@code invalidClasses} permanently - the fixture's own {@code constructMod} then dies with
     * {@code Failed load class}. Reading the constructed instance poisons nothing: an absent mod is a null entry,
     * never a cached failure.
     */
    private static void requestFixtureDump() {
        try {
            net.minecraftforge.fml.common.ModContainer container =
                    net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("celtium_validation");
            if (container == null) {
                return;
            }
            Object mod = container.getMod();
            if (mod == null) {
                return;
            }
            mod.getClass().getMethod("requestModLoadingDump").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            // No fixture, or one that cannot answer: a measurement lost, never a frame lost.
        }
    }
}
