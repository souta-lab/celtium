package org.taumc.celeritas.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The GL-less loading screen's progress view.
 * <p>
 * Forge's {@code SplashProgress} owns a second thread with a shared GL context, which cannot exist on a
 * {@code GLFW_NO_API} window - so under GL-less the launch shows one static Mojang frame for the whole mod load.
 * The state it polled is not thread-bound, though: {@code ProgressManager} runs on the loading thread, which is the
 * same thread {@code Minecraft.drawSplashScreen} already drew and presented on once. Each {@code step}/{@code push} is
 * therefore a safe redraw point on the GL thread, and every bar it reports is drawn and presented here on the same
 * thread - the second context was only ever needed to draw from a different thread, which this removes the need for.
 * <p>
 * The frame mirrors {@code Minecraft.drawSplashScreen}: the same orthographic setup, framebuffer bounce and present,
 * plus the live progress bars under the logo. Everything else about loading is unchanged - a failed or absent
 * renderer only ever skips the overlay, never aborts loading.
 */
public final class CeltiumLoadingProgress {

    private CeltiumLoadingProgress() {
    }

    /** Cached across frames: vanilla's method re-uploads the logo every call, which is the only upload it needed. */
    private static DynamicTexture logoTexture;
    private static ResourceLocation logoLocation;
    /** Back-off for the logo fetch while the default pack is not registered yet. */
    private static long logoRetryAfterNanos;
    /** The framebuffer bounce, kept across frames so a progress tick does not allocate a target every time. */
    private static Framebuffer splashTarget;
    private static int splashWidth;
    private static int splashHeight;
    private static long nextDrawNanos;
    /** ~30 fps - the cadence Forge's own splash drew at, so the overlay costs what the real one did. */
    private static final long DRAW_INTERVAL_NANOS = 33_000_000L;
    private static int failureLogs;

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
        if (mc == null) {
            return;
        }
        // Loader.loadMods runs inside beginMinecraftLoading, before Minecraft.init has made the texture manager or
        // the font renderer - and the scan/construct phase is most of the load for a big pack. The overlay still
        // draws there: a flat background and the live bars, with the logo and stage text joining as soon as the
        // resources they need exist.
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

    private static void drawFrame(Minecraft mc, boolean haveText, boolean haveTextures) throws IOException {
        net.minecraft.client.gui.ScaledResolution resolution =
                new net.minecraft.client.gui.ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        // The logo upload is attempted before the framebuffer binds so a pack that is not registered yet fails
        // cheap: during loadMods the resource manager exists but the default pack is not in it, which is exactly
        // the window the overlay was built for - those frames draw the flat background instead.
        boolean haveLogo = false;
        if (haveTextures) {
            if (logoLocation == null && logoRetryAfterNanos < System.nanoTime()) {
                // The classpath copy: vanilla's splash reads the default resource pack directly, and during
                // loadMods the manager exists without the pack in it (measured: FileNotFoundException every
                // frame). The jar's own asset is the same bytes the pack would serve.
                try (InputStream stream = CeltiumLoadingProgress.class
                        .getResourceAsStream("/assets/minecraft/textures/gui/title/mojang.png")) {
                    if (stream == null) {
                        throw new IOException("mojang.png not on the classpath");
                    }
                    BufferedImage image = ImageIO.read(stream);
                    logoTexture = new DynamicTexture(image);
                    logoLocation = mc.getTextureManager().getDynamicTextureLocation("celtium_mojang_logo",
                            logoTexture);
                } catch (IOException missing) {
                    logoRetryAfterNanos = System.nanoTime() + 1_000_000_000L;
                }
            }
            haveLogo = logoLocation != null;
        }
        if (splashTarget == null || splashWidth != width || splashHeight != height) {
            if (splashTarget != null) {
                splashTarget.deleteFramebuffer();
            }
            splashTarget = new Framebuffer(width * scale, height * scale, true);
            splashWidth = width;
            splashHeight = height;
        }
        // A failure mid-frame must still unbind the bounce target: measured (legs e2e-hurt5/hurt6), an exception
        // between bindFramebuffer and unbindFramebuffer leaves every later game frame drawing into the orphaned
        // target and the run never reaches the world.
        boolean drewBars = false;
        splashTarget.bindFramebuffer(false);
        try {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

            if (haveLogo) {
                mc.getTextureManager().bindTexture(logoLocation);

                Tessellator tessellator = Tessellator.getInstance();
                net.minecraft.client.renderer.BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                buffer.pos(0.0D, mc.displayHeight, 0.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
                buffer.pos(mc.displayWidth, mc.displayHeight, 0.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
                buffer.pos(mc.displayWidth, 0.0D, 0.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
                buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
                tessellator.draw();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                // The centered logo, as Minecraft.draw itself would blit it.
                drawLogo(width, height);
            } else {
                // Before the texture manager exists there is no logo to bind: a flat dark background stands in, the
                // same colour family the Mojang screen uses, and the bars still draw over it.
                net.minecraft.client.gui.Gui.drawRect(0, 0, width, height, 0xFF151515);
            }

            drewBars = drawBars(mc, width, height, haveText);

            GlStateManager.disableLighting();
            GlStateManager.disableFog();
        } finally {
            splashTarget.unbindFramebuffer();
        }
        splashTarget.framebufferRender(width * scale, height * scale);
        // Through Minecraft.updateDisplay, not Display.update directly: the fixture's presented-frame counter and
        // its dump latch live on that method, and a bare Display.update() presents the frame without counting it -
        // measured on leg plain-hurt5, where the mod-loading request stayed pending until the game's own second
        // present and captured a vanilla frame instead of the overlay.
        mc.updateDisplay();
        if (drewBars && ++overlayFrames >= MIN_DUMPPED_FRAME) {
            requestFixtureDump();
        }
    }

    /**
     * The earliest overlay frame worth capturing: deep enough that the fixture's presented-dump lands on an overlay
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

    /** The centred 256x256 logo blit {@code Minecraft.drawSplashScreen} ends with. */
    private static void drawLogo(int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder buffer = tessellator.getBuffer();
        float x = (width - 256) / 2.0F;
        float y = (height - 256) / 2.0F;
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x, y + 256.0F, 0.0D).tex(0.0D, 1.0D).color(255, 255, 255, 255).endVertex();
        buffer.pos(x + 256.0F, y + 256.0F, 0.0D).tex(1.0D, 1.0D).color(255, 255, 255, 255).endVertex();
        buffer.pos(x + 256.0F, y, 0.0D).tex(1.0D, 0.0D).color(255, 255, 255, 255).endVertex();
        buffer.pos(x, y, 0.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
        tessellator.draw();
    }

    /**
     * The live {@code ProgressManager} bars, stacked at the bottom third - the same data Forge's splash drew, read
     * directly since the loading thread already owns it.
     */
    private static boolean drawBars(Minecraft mc, int width, int height, boolean haveText) {
        List<net.minecraftforge.fml.common.ProgressManager.ProgressBar> bars =
                new ArrayList<net.minecraftforge.fml.common.ProgressManager.ProgressBar>();
        try {
            Iterator<net.minecraftforge.fml.common.ProgressManager.ProgressBar> it =
                    net.minecraftforge.fml.common.ProgressManager.barIterator();
            while (it.hasNext()) {
                bars.add(it.next());
            }
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        if (bars.isEmpty()) {
            return false;
        }
        int barWidth = Math.min(width / 2, 300);
        int x0 = (width - barWidth) / 2;
        int y = height - 24 - (bars.size() - 1) * 24;
        for (net.minecraftforge.fml.common.ProgressManager.ProgressBar bar : bars) {
            String title = bar.getTitle();
            String message = bar.getMessage();
            int steps = Math.max(1, bar.getSteps());
            int step = Math.min(steps, bar.getStep());
            // The bar outline and fill: Forge's own look, a dark rail with the step fraction filled.
            net.minecraft.client.gui.Gui.drawRect(x0, y + 10, x0 + barWidth, y + 14, 0xFF222222);
            int fill = (int) ((long) barWidth * step / steps);
            if (fill > 0) {
                net.minecraft.client.gui.Gui.drawRect(x0, y + 10, x0 + fill, y + 14, 0xFF55FF55);
            }
            if (haveText) {
                mc.fontRenderer.drawString(title, x0, y, 0xFFFFFFFF);
                String progress = (message == null || message.isEmpty() ? "" : message + " ") + step + "/" + steps;
                mc.fontRenderer.drawString(progress, x0, y + 16, 0xFFCCCCCC);
            }
            y += 24;
        }
        return true;
    }
}
