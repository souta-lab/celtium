package org.taumc.celeritas.impl.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * The one texture every glyph is drawn from.
 * <p>
 * Vanilla addresses a glyph in two steps: the per-page unicode sheet binds a different GL texture for each 256-char
 * block, and the ASCII sheet binds yet another. Under {@code unicodeFlag} every character takes the page path, so a
 * Japanese string pays a texture bind per character - and the GL-less renderer then resolves each page's mirror, its
 * descriptor and its upload separately.
 * <p>
 * This class replaces that whole addressing scheme with one image. At build time it rasterises the same resources the
 * vanilla font loads - {@code textures/font/unicode_page_%02x.png} for every page that exists in the resource manager,
 * plus the {@code ascii.png} sheet - into a single {@code BufferedImage}, publishes it through the normal
 * {@code TextureManager} upload path (so it receives an ordinary GL name and the renderer mirrors it exactly like any
 * other texture), and reports the atlas-space UV rectangle for a character. Because every glyph on the sheet lives in
 * the one texture, a whole string draws with one bind and one texture, and the drawn pixels are the bytes this class
 * wrote - there is no per-page texture resolution left to misaddress.
 * <p>
 * Layout: a 16x16 grid of {@link #unicodeDim}-sized page cells, page {@code p} at
 * {@code ((p & 15) * unicodeDim, (p >> 4) * unicodeDim)}, with the ASCII sheet drawn underneath at
 * {@code (0, 16 * unicodeDim)} in its own {@link #asciiDim} square. The UV helpers answer the same rectangle for the
 * same character in both coordinate systems.
 */
public final class CeltiumFontAtlas {

    private CeltiumFontAtlas() {
    }

    /** The texture the whole font is bound under once built. */
    private static final ResourceLocation ATLAS_LOCATION = new ResourceLocation("celtium", "textures/font/atlas");

    /** Property switch: the atlas is the default path; {@code =false} restores the per-page path for A/B work. */
    private static final boolean ENABLED = Boolean
            .parseBoolean(System.getProperty("celtium.fontAtlas", "true"));

    /** The unicode page edge length this build measured from the resources (vanilla ships 256). */
    private static int unicodeDim = 256;

    /** The ASCII sheet edge length this build measured (vanilla ships 128). */
    private static int asciiDim = 128;

    /** Atlas pixel dimensions: 16 page columns and rows of {@link #unicodeDim}, plus the ASCII strip. */
    private static int atlasW;
    private static int atlasH;

    /** The published texture object and the GL name it was given, or 0 while unbuilt. */
    private static AbstractTexture atlasTexture;
    private static int atlasName;

    /** The image the atlas texture was last uploaded from - kept so {@link #noteMirrorSource} can count it. */
    private static BufferedImage atlasImage;

    private static boolean buildFailed;

    /**
     * {@return whether the atlas path is the active one}. When the flag is off or the build failed, every caller takes
     * the per-page path it took before.
     */
    public static boolean active() {
        if (!ENABLED || buildFailed) {
            return false;
        }
        if (atlasTexture == null) {
            build();
        }
        return atlasTexture != null;
    }

    /** {@return the location {@code bindTexture} resolves the atlas under}. */
    public static ResourceLocation location() {
        return ATLAS_LOCATION;
    }

    /**
     * {@return the UV rectangle of unicode glyph {@code ch} in atlas space}.
     * <p>
     * The geometry is the vanilla {@code renderUnicodeChar} layout, shifted by the page's atlas origin:
     * {@code f = glyphWidth[ch] >>> 4} (the glyph's left edge inside its 16px cell),
     * {@code width = (glyphWidth[ch] & 15) + 1 - f - 0.02F} (the ink width), cell
     * {@code ((ch % 16) * 16 + f, ((ch & 255) >> 4) * 16)} inside its 256-char page, page at
     * {@code ((page & 15) * unicodeDim, (page >> 4) * unicodeDim)}.
     */
    public static void unicodeUV(char ch, byte[] glyphWidth, float[] out) {
        int i = glyphWidth[ch] & 255;
        float f = i >>> 4;
        float f1 = (i & 15) + 1;
        float f4 = f1 - f - 0.02F;
        int page = ch >> 8;
        float x = (page & 15) * unicodeDim + (ch % 16 * 16) + f;
        float y = (page >> 4) * unicodeDim + (ch & 255) / 16 * 16;
        out[0] = x / atlasW;
        out[1] = y / atlasH;
        out[2] = (x + f4) / atlasW;
        out[3] = (y + 15.98F) / atlasH;
    }

    /**
     * {@return the UV rectangle of ASCII glyph {@code index} (the 0-255 character index into the sheet) in atlas
     * space}. The sheet is drawn at {@code (0, 16 * unicodeDim)} at {@link #asciiDim} pixels; vanilla's
     * {@code renderDefaultChar} computes texels on a 128-unit sheet, so each unit scales by
     * {@code asciiDim / 128}.
     */
    public static void asciiUV(int index, int width, float[] out) {
        float scale = asciiDim / 128.0F;
        float f = (float) width - 0.01F;
        float x = (index % 16 * 8) * scale;
        float y = 16 * unicodeDim + (index / 16 * 8) * scale;
        out[0] = x / atlasW;
        out[1] = y / atlasH;
        out[2] = (x + (f - 1.0F) * scale) / atlasW;
        out[3] = (y + 7.99F * scale) / atlasH;
    }

    /** {@return the advance vanilla's {@code renderUnicodeChar} returns for {@code ch}}. */
    public static float unicodeAdvance(byte[] glyphWidth, char ch) {
        int i = glyphWidth[ch] & 255;
        return ((i & 15) + 1 - (i >>> 4)) / 2.0F + 1.0F;
    }

    /**
     * Rasterises the atlas and hands it to the texture manager.
     * <p>
     * Every page that exists in the resource manager is drawn; missing pages leave their cell blank, which is exactly
     * what the per-page path drew for a glyph on an absent page. The publish goes through
     * {@code TextureManager.loadTexture} + {@code AbstractTexture.loadTexture}, so the GL name, the announce and the
     * mirror all follow the path any other texture takes.
     */
    private static void build() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            buildFailed = true;
            return;
        }
        int measuredPage = 256;
        for (int p = 0; p < 256; p++) {
            ResourceLocation pageLoc = new ResourceLocation(
                    String.format("textures/font/unicode_page_%02x.png", p));
            try (InputStream stream = mc.getResourceManager().getResource(pageLoc).getInputStream()) {
                measuredPage = ImageIO.read(stream).getWidth();
                break;
            } catch (Exception ignored) {
                // A page that is not there just stays blank; try the next.
            }
        }
        int measuredAscii = 128;
        try (InputStream stream = mc.getResourceManager()
                .getResource(new ResourceLocation("textures/font/ascii.png")).getInputStream()) {
            measuredAscii = ImageIO.read(stream).getWidth();
        } catch (Exception ignored) {
            // 128 is the vanilla sheet; a missing one draws a blank strip like a missing page.
        }
        unicodeDim = measuredPage;
        asciiDim = measuredAscii;
        atlasW = 16 * unicodeDim;
        atlasH = 16 * unicodeDim + asciiDim;
        BufferedImage image = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        for (int p = 0; p < 256; p++) {
            ResourceLocation pageLoc = new ResourceLocation(
                    String.format("textures/font/unicode_page_%02x.png", p));
            try (InputStream stream = mc.getResourceManager().getResource(pageLoc).getInputStream()) {
                BufferedImage page = ImageIO.read(stream);
                g.drawImage(page, (p & 15) * unicodeDim, (p >> 4) * unicodeDim, null);
            } catch (Exception ignored) {
            }
        }
        try (InputStream stream = mc.getResourceManager()
                .getResource(new ResourceLocation("textures/font/ascii.png")).getInputStream()) {
            BufferedImage ascii = ImageIO.read(stream);
            g.drawImage(ascii, 0, 16 * unicodeDim, null);
        } catch (Exception ignored) {
        }
        g.dispose();
        atlasImage = image;
        TextureManager tm = mc.getTextureManager();
        if (tm == null) {
            buildFailed = true;
            return;
        }
        atlasTexture = new AtlasTexture(image);
        tm.loadTexture(ATLAS_LOCATION, atlasTexture);
        atlasName = atlasTexture.getGlTextureId();
        System.out.println("[Celtium] font atlas built: " + atlasW + "x" + atlasH
                + " unicodeDim=" + unicodeDim + " asciiDim=" + asciiDim + " texture=" + atlasName);
    }

    /** {@return the GL name the atlas texture received}, or 0 while unbuilt. */
    public static int textureName() {
        return atlasName;
    }

    /**
     * The texture object the atlas publishes under {@link #ATLAS_LOCATION}. An {@link AbstractTexture} (rather than a
     * {@code DynamicTexture}) so its {@code loadTexture} calls {@code TextureUtil.uploadTextureImageAllocate} - the
     * same entry the announce hook mirrors - with the atlas's own image.
     */
    private static final class AtlasTexture extends AbstractTexture {
        private final BufferedImage content;

        private AtlasTexture(BufferedImage content) {
            this.content = content;
        }

        @Override
        public void loadTexture(net.minecraft.client.resources.IResourceManager resourceManager) {
            deleteGlTexture();
            this.getGlTextureId();
            TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), this.content, false, false);
        }
    }
}
