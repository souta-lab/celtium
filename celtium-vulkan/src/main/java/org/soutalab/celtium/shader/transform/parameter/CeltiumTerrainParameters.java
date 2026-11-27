/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.transform.parameter;

import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.model.Tri;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;
import org.soutalab.celtium.shader.transform.Patch;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Parameters for transforming terrain shader programs against Celtium's native
 * 16 B PackedAxisQuad / 64 B GeneralQuad + 2 B mc_Entity vertex layout.
 */
public class CeltiumTerrainParameters extends Parameters {
    public CeltiumTerrainParameters(Patch patch) {
        super(patch, null);
    }

    public CeltiumTerrainParameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        super(patch, textureMap);
    }

    @Override
    public TextureStage getTextureStage() {
        return TextureStage.GBUFFERS_AND_SHADOW;
    }
}
