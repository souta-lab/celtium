package org.soutalab.celtium.shader.transform;

import org.soutalab.celtium.shader.model.ShaderType;
import org.soutalab.celtium.shader.transform.parameter.AttributeParameters;
import org.soutalab.celtium.shader.transform.parameter.Parameters;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.HashSet;
import java.util.Set;

public class CommonTransformer {

	public static void transform(Transformer root, Parameters parameters, boolean core, int glslVersion) {
		root.rename("gl_FogFragCoord", "iris_FogFragCoord");
		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out float iris_FogFragCoord;");
			root.prependMain("iris_FogFragCoord = 0.0f;");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in float iris_FogFragCoord;");
		}

		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out vec4 iris_FrontColor;");
			root.rename("gl_FrontColor", "iris_FrontColor");
			// Legacy packs read gl_Color in the composite vertex stage (e.g. Sildur's
			// Enhanced Default). The full-screen quad has no color attribute, so map it
			// to the same white-initialized out variable that gl_FrontColor uses.
			// Only COMPOSITE: other patches map gl_Color themselves (ATTRIBUTES to the
			// vertex color / modulator, Celeritas and DH terrain to _vert_color).
			if (parameters.patch == Patch.COMPOSITE) {
				root.rename("gl_Color", "iris_FrontColor");
			}
			root.prependMain("iris_FrontColor = vec4(1.0);");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in vec4 iris_FrontColor;");
			root.rename("gl_Color", "iris_FrontColor");
		}

		if (parameters.type == ShaderType.FRAGMENT) {
			if (root.containsCall("gl_FragColor")) {
				root.replaceExpression("gl_FragColor", "gl_FragData[0]");
			}

			if (core) {
				// Core profile: gl_FragData doesn't exist. Flatten gl_FragData[N] → iris_FragDataN with layout-qualified out declarations.
				Set<Integer> found = new HashSet<>();
				root.renameArray("gl_FragData", "iris_FragData", found);

				for (Integer i : found) {
					root.injectVariable("layout (location = " + i + ") out vec4 iris_FragData" + i + ";");
				}

				// Core profile: GL_ALPHA_TEST is removed. 1.7.10 engine relies on alpha test to discard transparent fragments. Inject
				// runtime discard using the GLSM-tracked alpha reference value.
				if (found.contains(0) && parameters.patch != Patch.COMPOSITE && parameters.patch != Patch.COMPUTE) {
					root.injectVariable("uniform float iris_currentAlphaTest;");
					root.appendMain("if (iris_FragData0.a <= iris_currentAlphaTest) discard;");
				}
			}
		}

		if (root.containsCall("texture") && root.hasVariable("texture")) {
			root.rename("texture", "gtexture");
		}

		if (root.hasVariable("actinium_renamed_texture")) {
			root.rename("actinium_renamed_texture", "gtexture");
		}

		if (root.containsCall("gcolor") && root.hasVariable("gcolor")) {
			root.rename("gcolor", "gtexture");
		}

		root.rename("gl_Fog", "iris_Fog");
		root.injectVariable("uniform float iris_FogDensity;");
		root.injectVariable("uniform float iris_FogStart;");
		root.injectVariable("uniform float iris_FogEnd;");
		root.injectVariable("uniform vec4 iris_FogColor;");
		root.injectFunction("struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};");
		root.injectFunction("iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));");

		root.renameFunctionCall(GlslTransformHelpers.TEXTURE_RENAMES);
		root.renameAndWrapShadow("shadow2D", "texture");
		root.renameAndWrapShadow("shadow2DLod", "textureLod");
		AdaptiveShadowBoundsTransformer.transform(root, parameters.type);

		if (parameters instanceof AttributeParameters attributes) {
			injectClipDistances(root, attributes);
		}
	}

	private static final String EMULATED_VERTEX_CLIP = buildEmulatedVertexClip();

	private static String buildEmulatedVertexClip() {
		String[] distances = new String[8];
		for (int plane = 0; plane < distances.length; plane++) {
			distances[plane] = "dot(actinium_ClipPlane[" + plane + "], _cp_ep)";
		}
		StringBuilder clip = new StringBuilder("{ vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; ");
		clip.append("// native gl_ClipDistance
");
		return clip.append('}').toString();
	}

	private static void injectClipDistances(Transformer root, AttributeParameters parameters) {
		if (true) {
			if (parameters.type == ShaderType.VERTEX) {
				injectNativeClipPlanes(root, false);
			}
			return;
		}
		if (!parameters.clipDiscard || parameters.hasGeometry || parameters.hasTesselation) return;
		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("uniform vec4 actinium_ClipPlane[8];");
			
			
			root.prependMain(EMULATED_VERTEX_CLIP);
		} else if (parameters.type == ShaderType.FRAGMENT) {
			
			
			
		}
	}

	static void injectNativeClipPlanes(Transformer root, boolean sdlGpu) {
		root.injectVariable("uniform vec4 actinium_ClipPlane[8];");
		if (sdlGpu) {
			root.injectVariable("uniform int actinium_ClipPlaneMask;");
			// SDL GPU consumes declared clip distances even when no user clip plane is enabled.
			StringBuilder defaults = new StringBuilder("{ ");
			StringBuilder clip = new StringBuilder("{ if (actinium_ClipPlaneMask != 0) { vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; ");
			for (int plane = 0; plane < 8; plane++) {
				defaults.append("gl_ClipDistance[").append(plane).append("] = 0.0; ");
				clip.append("if ((actinium_ClipPlaneMask & ").append(1 << plane).append(") != 0) ")
					.append("gl_ClipDistance[").append(plane).append("] = dot(actinium_ClipPlane[").append(plane).append("], _cp_ep); ");
			}
			final String applyPlanes = clip.append("} }").toString();
			root.prependMain(defaults.append('}').toString());
			root.mutateTree(tree -> {
				for (var function : GlslAstHelpers.collectAll(tree, GLSLParser.Function_definitionContext.class)) {
					if (!"main".equals(function.function_prototype().IDENTIFIER().getText())) continue;
					for (var jump : GlslAstHelpers.collectAll(function, GLSLParser.Jump_statementContext.class)) {
						if (jump.RETURN() == null) continue;
						var statement = jump.getParent().getParent();
						final String replacement = "{ " + applyPlanes + " return; }";
						if (statement instanceof GLSLParser.Statement_no_new_scopeContext) {
							GlslAstHelpers.replaceNode(statement, ShaderParser.parseSnippet(replacement, GLSLParser::statement_no_new_scope));
						} else {
							GlslAstHelpers.replaceNode(statement, ShaderParser.parseSnippet(replacement, GLSLParser::statement));
						}
					}
				}
			});
			root.appendMain(applyPlanes);
			return;
		}
		root.injectVariable("uniform bool actinium_ClipPlanesEnabled;");
		root.appendMain(
			"{ if (actinium_ClipPlanesEnabled) { vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; "
			+ "gl_ClipDistance[0] = dot(actinium_ClipPlane[0], _cp_ep); "
			+ "gl_ClipDistance[1] = dot(actinium_ClipPlane[1], _cp_ep); "
			+ "gl_ClipDistance[2] = dot(actinium_ClipPlane[2], _cp_ep); "
			+ "gl_ClipDistance[3] = dot(actinium_ClipPlane[3], _cp_ep); "
			+ "gl_ClipDistance[4] = dot(actinium_ClipPlane[4], _cp_ep); "
			+ "gl_ClipDistance[5] = dot(actinium_ClipPlane[5], _cp_ep); "
			+ "gl_ClipDistance[6] = dot(actinium_ClipPlane[6], _cp_ep); "
			+ "gl_ClipDistance[7] = dot(actinium_ClipPlane[7], _cp_ep); } }"
		);
	}
}
