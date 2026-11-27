/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 * Self-contained GLSL AST and formatting helpers replacing Angelica GLSM utilities and GTNHLib
 * VertexFormatElement attribute indices.
 */
package org.soutalab.celtium.shader.transform;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GlslTransformHelpers {

    // Native attribute locations (replaces GTNHLib VertexFormatElement.Usage + Angelica InstancedAttribs)
    public static final int LOC_POSITION = 0;
    public static final int LOC_COLOR = 1;
    public static final int LOC_PRIMARY_UV = 2;
    public static final int LOC_LIGHTMAP_UV = 3;
    public static final int LOC_NORMAL = 4;
    public static final int LOC_ENTITY_IDS = 10;
    public static final int LOC_TANGENT = 13;

    // Weather instancing (ShaderInstancing.WEATHER), per the weather feed contract ruling
    // (Neon, 2026-11-27; plan section 8.3 item 7). The five per-instance attributes ride one
    // 80-byte instance record on binding 1. NOTE the non-monotonic offset assignment: the
    // COLOR attribute (location 7) sits at byte offset 48 while PARAMS (location 8) sits at
    // offset 32 - the record layout follows the proven glsm/ffp arrangement so reference
    // values compare without conversion. Never derive an offset from a location.
    public static final int LOC_WEATHER_COLUMN_SPAN = 5;
    public static final int LOC_WEATHER_JITTER = 6;
    public static final int LOC_WEATHER_COLOR = 7;
    public static final int LOC_WEATHER_PARAMS = 8;
    public static final int LOC_WEATHER_LAYER = 9;
    public static final int WEATHER_INSTANCE_STRIDE = 80;
    public static final int WEATHER_OFFSET_COLUMN_SPAN = 0;
    public static final int WEATHER_OFFSET_JITTER = 16;
    public static final int WEATHER_OFFSET_PARAMS = 32;
    public static final int WEATHER_OFFSET_COLOR = 48;
    public static final int WEATHER_OFFSET_LAYER = 64;

    /**
     * The weather vertex reconstruction prologue, with semantics identical to the proven
     * glsm/ffp WEATHER_PROLOGUE (LGPL-3.0 geometry formulas, re-declared here so the pack
     * transform stays inside its own closed domain - no glcompat reference). Emitted at the
     * head of a WEATHER vertex main; reads the per-vertex corner, the five per-instance
     * attributes and the four per-frame weather uniforms, and fully writes iris_Vertex,
     * iris_Color, iris_MultiTexCoord0 and iris_MultiTexCoord1.
     */
    public static String weatherPrologue() {
        return """
            vec2 iris_wColumn = iris_InstColumnSpan.xy;
            float iris_wBottom = iris_InstColumnSpan.z;
            float iris_wTop = iris_InstColumnSpan.w;
            float iris_wLightV = floor(iris_InstWeatherParams.x / 65536.0);
            vec2 iris_wLightmap = vec2(iris_InstWeatherParams.x - iris_wLightV * 65536.0, iris_wLightV);
            bool iris_wProvider = iris_InstWeatherParams.w > 0.5;
            vec3 iris_wTranslate = iris_WeatherParams0.xyz;
            float iris_wInvRadius = iris_wProvider ? iris_WeatherParams3.x : iris_WeatherParams0.w;
            vec2 iris_wCameraFrac = iris_WeatherParams1.xy;
            float iris_wPartialTicks = iris_WeatherParams1.z;
            float iris_wAge = iris_wProvider ? iris_WeatherParams2.w : iris_WeatherParams1.w;
            float iris_wTick = iris_wProvider ? iris_WeatherParams2.y : iris_WeatherParams2.x;
            float iris_wStrength = iris_wProvider ? 1.0 : iris_WeatherParams2.z;
            float iris_wColumnLength = length(iris_wColumn);
            vec2 iris_wHalfWidth = vec2(0.0);
            if (iris_wColumnLength > 0.0) {
                iris_wHalfWidth = vec2(-iris_wColumn.y, iris_wColumn.x) * (0.5 / iris_wColumnLength);
            }
            vec2 iris_wCornerXZ = iris_wColumn + vec2(0.5) + (iris_WeatherCorner.x * 2.0 - 1.0) * iris_wHalfWidth;
            float iris_wCornerY = mix(iris_wBottom, iris_wTop, iris_WeatherCorner.y);
            iris_Vertex = vec4(vec3(iris_wCornerXZ.x, iris_wCornerY, iris_wCornerXZ.y) + iris_wTranslate, 1.0);
            float iris_wDistance = length(iris_wColumn + vec2(0.5) - iris_wCameraFrac) * iris_wInvRadius;
            float iris_wFade = 1.0 - iris_wDistance * iris_wDistance;
            float iris_wScroll = (mod(iris_wTick + iris_InstWeatherParams.y, iris_InstWeatherLayer.x) + iris_wPartialTicks) / iris_InstWeatherLayer.x * iris_InstWeatherParams.z;
            vec2 iris_wDrift = vec2(iris_InstJitter.x + iris_wAge * iris_InstWeatherLayer.y * iris_InstJitter.y, iris_InstJitter.z + iris_wAge * iris_InstWeatherLayer.z * iris_InstJitter.w);
            float iris_wAlpha = iris_wFade * iris_InstWeatherLayer.w + 0.5;
            float iris_wTextureY = iris_wCornerY;
            if (!iris_wProvider) {
                iris_wTextureY = mix(iris_wTop, iris_wBottom, iris_WeatherCorner.y);
                iris_wScroll = -iris_wScroll;
            }
            iris_MultiTexCoord0 = vec4(iris_WeatherCorner.x + iris_wDrift.x, iris_wTextureY * 0.25 + iris_wScroll + iris_wDrift.y, 0.0, 1.0);
            iris_MultiTexCoord1 = vec4(iris_wLightmap, 0.0, 1.0);
            vec3 iris_wRgb = floor(clamp(iris_InstWeatherColor.rgb, 0.0, 1.0) * 255.0);
            float iris_wOpacity = iris_wAlpha * iris_InstWeatherColor.a * iris_wStrength;
            float iris_wAlphaByte = iris_wProvider
                ? floor(clamp(iris_wOpacity, 0.0, 1.0) * 255.0)
                : mod(trunc(iris_wOpacity * 255.0), 256.0);
            iris_Color = vec4(iris_wRgb, iris_wAlphaByte) / 255.0;
            """;
    }

    /** Legacy GLSL 120 texture lookup functions -> GLSL 330+ core names. */
    public static final Map<String, String> TEXTURE_RENAMES = Map.ofEntries(
            Map.entry("texture1D", "texture"),
            Map.entry("texture1DProj", "textureProj"),
            Map.entry("texture1DLod", "textureLod"),
            Map.entry("texture1DProjLod", "textureProjLod"),
            Map.entry("texture2D", "texture"),
            Map.entry("texture2DProj", "textureProj"),
            Map.entry("texture2DLod", "textureLod"),
            Map.entry("texture2DProjLod", "textureProjLod"),
            Map.entry("texture3D", "texture"),
            Map.entry("texture3DProj", "textureProj"),
            Map.entry("texture3DLod", "textureLod"),
            Map.entry("texture3DProjLod", "textureProjLod"),
            Map.entry("textureCube", "texture"),
            Map.entry("textureCubeLod", "textureLod"),
            Map.entry("shadow1D", "texture"),
            Map.entry("shadow2D", "texture"),
            Map.entry("shadow1DProj", "textureProj"),
            Map.entry("shadow2DProj", "textureProj"),
            Map.entry("shadow1DLod", "textureLod"),
            Map.entry("shadow2DLod", "textureLod"),
            Map.entry("shadow1DProjLod", "textureProjLod"),
            Map.entry("shadow2DProjLod", "textureProjLod")
    );

    private static final String RESERVED_PREFIX = "celtium_resv_";
    private static final Set<String> RESERVED_WORDS = Set.of(
            "sample", "subroutine", "common", "partition", "active", "filter", "resource", "patch"
    );
    private static final Pattern RESERVED_WORD_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", RESERVED_WORDS) + ")\\b");
    private static final Pattern TEXTURE_VAR_PATTERN = Pattern.compile(
            "(?<![.\\w])\\btexture\\b(?!\\s*\\()");

    private GlslTransformHelpers() {
    }

    /**
     * Renames legacy identifier {@code texture} when used as a variable (not as a function call)
     * so GLSL 330+ parsers do not collide with the built-in {@code texture()} function.
     */
    public static String replaceTexture(String source) {
        if (source == null || !source.contains("texture")) {
            return source;
        }
        return TEXTURE_VAR_PATTERN.matcher(source).replaceAll("iris_texture_var");
    }

    public static String renameReservedWords(String source, int glslVersion) {
        if (source == null || glslVersion < 130) {
            return source;
        }
        Matcher m = RESERVED_WORD_PATTERN.matcher(source);
        if (!m.find()) {
            return source;
        }
        return m.replaceAll(RESERVED_PREFIX + "$1");
    }

    public static void restoreReservedWordsInTree(ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof TerminalNode terminal) {
            Token token = terminal.getSymbol();
            if (token instanceof CommonToken ct) {
                String text = ct.getText();
                if (text != null && text.startsWith(RESERVED_PREFIX)) {
                    ct.setText(text.substring(RESERVED_PREFIX.length()));
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            restoreReservedWordsInTree(node.getChild(i));
        }
    }

    public static String getFormattedShader(ParseTree tree, String header) {
        StringBuilder sb = new StringBuilder();
        if (header != null && !header.isEmpty()) {
            sb.append(header);
            if (!header.endsWith("\n")) {
                sb.append('\n');
            }
        }
        if (tree != null) {
            sb.append(ShaderPrinter.getFormattedShader(tree));
        }
        return sb.toString();
    }

    public static String fixupQualifiers(String source, boolean isFragment) {
        if (source == null) {
            return null;
        }
        // Normalise legacy centroid/flat qualifier ordering if needed; pass-through for valid GLSL.
        return source;
    }

    public static List<GLSLParser.Typeless_declarationContext> declarators(GLSLParser.Init_declarator_listContext list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<GLSLParser.Typeless_declarationContext> declarators = new ArrayList<>();
        GLSLParser.Single_declarationContext single = list.single_declaration();
        if (single != null && single.typeless_declaration() != null) {
            declarators.add(single.typeless_declaration());
        }
        declarators.addAll(list.typeless_declaration());
        return declarators;
    }

    public static boolean hasStorageQualifier(GLSLParser.Type_qualifierContext qualifier, String name) {
        if (qualifier == null || name == null) {
            return false;
        }
        for (GLSLParser.Single_type_qualifierContext single : qualifier.single_type_qualifier()) {
            GLSLParser.Storage_qualifierContext sq = single.storage_qualifier();
            if (sq != null && name.equals(sq.getText())) {
                return true;
            }
        }
        return false;
    }
}
