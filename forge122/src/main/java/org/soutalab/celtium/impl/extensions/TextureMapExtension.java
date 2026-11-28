package org.soutalab.celtium.impl.extensions;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.soutalab.celtium.forge.util.QuadTree;

public interface TextureMapExtension {
    QuadTree<TextureAtlasSprite> celeritas$getQuadTree();

    TextureAtlasSprite celeritas$findFromUV(float u, float v);
}
