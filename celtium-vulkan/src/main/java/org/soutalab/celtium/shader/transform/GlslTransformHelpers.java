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

    public static List<String> declarators(GLSLParser.Init_declarator_listContext list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        GLSLParser.Single_declarationContext single = list.single_declaration();
        if (single != null && single.IDENTIFIER() != null) {
            names.add(single.IDENTIFIER().getText());
        }
        if (list.IDENTIFIER() != null) {
            for (TerminalNode id : list.IDENTIFIER()) {
                names.add(id.getText());
            }
        }
        return names;
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
