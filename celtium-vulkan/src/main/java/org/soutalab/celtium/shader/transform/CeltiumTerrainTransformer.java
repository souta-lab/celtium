/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 * Native GLSL AST transformer for Celtium's 16 B PackedAxisQuad / 64 B GeneralQuad
 * + 2 B mc_Entity terrain vertex layout (replaces CeleritasTransformer).
 */
package org.soutalab.celtium.shader.transform;

import org.soutalab.celtium.core.shader.TerrainVertexRequirements;
import org.soutalab.celtium.shader.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class CeltiumTerrainTransformer {

    private static final Pattern MC_ENTITY_PATTERN = Pattern.compile("\\bmc_Entity\\b");
    private static final Pattern NORMAL_PATTERN = Pattern.compile("\\b(?:gl_Normal|vaNormal|iris_Normal)\\b");
    private static final Pattern TANGENT_PATTERN = Pattern.compile("\\bat_tangent\\b");
    private static final Pattern MID_TEX_PATTERN = Pattern.compile("\\b(?:mc_midTexCoord|gl_MultiTexCoord3)\\b");

    private CeltiumTerrainTransformer() {
    }

    /**
     * Inspects a terrain vertex shader source (post-preprocessing) to determine which optional
     * per-vertex attributes the pack reads, driving Celtium's auxiliary-column generation (gap G-5).
     */
    public static TerrainVertexRequirements detectVertexRequirements(String vertexSource) {
        if (vertexSource == null || vertexSource.isEmpty()) {
            return TerrainVertexRequirements.NONE;
        }
        boolean mcEntity = MC_ENTITY_PATTERN.matcher(vertexSource).find();
        boolean normal = NORMAL_PATTERN.matcher(vertexSource).find();
        boolean tangent = TANGENT_PATTERN.matcher(vertexSource).find();
        boolean midTexCoord = MID_TEX_PATTERN.matcher(vertexSource).find();
        return new TerrainVertexRequirements(mcEntity, normal, tangent, midTexCoord);
    }

    /**
     * Header injected ahead of terrain vertex shaders: binds Celtium's native terrain vertex
     * inputs (unpacked from 16 B PackedAxisQuad / 64 B GeneralQuad) without any Celeritas
     * chunk_vertex.glsl dependency.
     */
    public static String terrainVertexHeader() {
        return """
                layout(location = 0) in vec3 celtium_Position;
                layout(location = 1) in vec4 celtium_Color;
                layout(location = 2) in vec2 celtium_TexCoord;
                layout(location = 3) in ivec2 celtium_LightCoord;
                """;
    }

    public static void transform(Transformer transformer, Parameters parameters, int glslVersion) {
        CommonTransformer.transform(transformer, parameters, true, glslVersion);

        switch (parameters.type) {
            case FRAGMENT -> transformFragment(transformer, parameters);
            case GEOMETRY -> transformGeometry(transformer, parameters);
            case VERTEX -> transformVertex(transformer, parameters);
            default -> throw new IllegalStateException(
                    "Unexpected Celtium terrain patching shader type: " + parameters.type);
        }
    }

    public static void transformVertex(Transformer transformer, Parameters parameters) {
        transformer.injectVariable("uniform vec3 u_RegionOffset;");
        transformer.injectVariable("vec4 iris_LightTexCoord;");
        transformer.injectFunction(
                "vec4 iris_ftransform() { return gl_ModelViewProjectionMatrix * celtium_getVertexPosition(); }");
        transformer.injectFunction(
                "vec4 celtium_getVertexPosition() { return vec4(celtium_Position + u_RegionOffset, 1.0); }");
        transformer.injectFunction(
                "void celtium_terrain_init() { iris_LightTexCoord = vec4(vec2(celtium_LightCoord), 0.0, 1.0); }");
        transformer.prependMain("celtium_terrain_init();");
        transformShared(transformer, parameters);

        transformer.replaceExpression(
                "vec4(worldpos, 0.0)",
                "iris_ProjectionMatrix * gbufferModelView * vec4(worldpos, 1.0)");

        Map<String, String> vertexReplacements = new HashMap<>();
        vertexReplacements.put("gl_Vertex", "celtium_getVertexPosition()");
        vertexReplacements.put("gl_MultiTexCoord0", "vec4(celtium_TexCoord, 0.0, 1.0)");
        vertexReplacements.put("gl_MultiTexCoord1", "iris_LightTexCoord");
        vertexReplacements.put("gl_MultiTexCoord2", "iris_LightTexCoord");
        vertexReplacements.forEach(transformer::replaceExpression);

        if (transformer.hasVariable("chunkOffset")) {
            transformer.removeVariable("chunkOffset");
        }

        Map<String, String> vertexRenames = new HashMap<>();
        vertexRenames.put("gl_Color", "celtium_Color");
        vertexRenames.put("ftransform", "iris_ftransform");
        vertexRenames.put("gl_Normal", "iris_Normal");
        vertexRenames.put("chunkOffset", "u_RegionOffset");
        transformer.rename(vertexRenames);

        transformer.injectVariable("layout(location = 4) in vec3 iris_Normal;");
    }

    public static void transformFragment(Transformer transformer, Parameters parameters) {
        transformShared(transformer, parameters);
    }

    private static void transformGeometry(Transformer transformer, Parameters parameters) {
        transformShared(transformer, parameters);
        transformer.replaceExpression(
                "toClipSpace3(mat3(gbufferModelView) * vec3(vertex) + gbufferModelView[3].xyz)",
                "vertex");
    }

    private static void transformShared(Transformer transformer, Parameters parameters) {
        CoreTransformHelper.injectMatrixUniforms(transformer);
    }
}
