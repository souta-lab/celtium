package org.soutalab.celtium.shader.shaderpack;

import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.model.BufferBlendInformation;
import org.soutalab.celtium.shader.model.BufferBlendingSupport;
import org.soutalab.celtium.shader.shaderpack.option.ShaderPackOptions;
import org.soutalab.celtium.shader.shaderpack.preprocessor.PropertiesPreprocessor;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.stream.Collectors;

/** Re-evaluates shaders.properties as {@link #IRIS_ERA_MC_VERSION} and adopts its unconfigured per-buffer blend directives. */
final class IrisEraBufferBlendAdopterImpl implements IrisEraBufferBlendAdopter {
	private final BufferBlendingSupport bufferBlendingSupport;

	IrisEraBufferBlendAdopterImpl(BufferBlendingSupport bufferBlendingSupport) {
		this.bufferBlendingSupport = bufferBlendingSupport;
	}

	@Override
	public List<BufferBlendDirective> adopt(String contents, ShaderPackOptions options, Iterable<StringPair> environmentDefines,
											Map<String, ? extends List<BufferBlendInformation>> configured) {
		final OptionalInt environmentMcVersion = McVersionDefines.mcVersionOf(environmentDefines);

		if (environmentMcVersion.isPresent() && environmentMcVersion.getAsInt() >= IRIS_ERA_MC_VERSION) {
			ShaderLogger.LOGGER.debug("Skipping Iris-era buffer blend adoption: environment MC_VERSION {} already evaluates as {} or newer",
				environmentMcVersion.getAsInt(), IRIS_ERA_MC_VERSION);
			return List.of();
		}

		if (!contents.contains(McVersionDefines.MC_VERSION)) {
			ShaderLogger.LOGGER.debug("Skipping Iris-era buffer blend adoption: shaders.properties does not reference MC_VERSION");
			return List.of();
		}

		final List<StringPair> candidates = collectPerBufferDirectives(evaluateAsIrisEra(contents, options, environmentDefines));

		if (candidates.isEmpty()) {
			ShaderLogger.LOGGER.debug("Skipping Iris-era buffer blend adoption: the MC_VERSION {} evaluation of shaders.properties has no per-buffer blend directives",
				IRIS_ERA_MC_VERSION);
			return List.of();
		}

		if (!bufferBlendingSupport.isSupported()) {
			ShaderLogger.LOGGER.warn("Per-buffer blend directives of the MC_VERSION " + IRIS_ERA_MC_VERSION
				+ " evaluation of shaders.properties ignored - buffer blending requires OpenGL 4.0 or GL_ARB_draw_buffers_blend: "
				+ candidates.stream().map(candidate -> describe(candidate.getKey(), candidate.getValue())).collect(Collectors.joining(", ")));
			return List.of();
		}

		final List<BufferBlendDirective> adopted = new ArrayList<>();

		for (StringPair candidate : candidates) {
			final BufferBlendDirective directive = BufferBlendDirective.parse(candidate.getKey(), candidate.getValue());

			if (isConfigured(configured, directive)) {
				ShaderLogger.LOGGER.debug("Not adopting {}: the environment evaluation already configures {} buffer {}",
					describe(directive.key(), directive.value()), directive.program(), directive.index());
				continue;
			}

			adopted.add(directive);
		}

		if (!adopted.isEmpty()) {
			ShaderLogger.LOGGER.info("Adopted {} per-buffer blend directive(s) that shaders.properties only enables for newer Minecraft versions"
					+ " (evaluated as MC_VERSION {}, environment MC_VERSION {}): {}",
				adopted.size(), IRIS_ERA_MC_VERSION,
				environmentMcVersion.isPresent() ? Integer.toString(environmentMcVersion.getAsInt()) : "undefined",
				adopted.stream().map(directive -> describe(directive.key(), directive.value())).collect(Collectors.joining(", ")));
		}

		return adopted;
	}

	private static Properties evaluateAsIrisEra(String contents, ShaderPackOptions options, Iterable<StringPair> environmentDefines) {
		final String preprocessed = PropertiesPreprocessor.preprocessSource(contents, options,
			McVersionDefines.withMcVersion(environmentDefines, IRIS_ERA_MC_VERSION));
		final Properties properties = new OrderBackedProperties();

		try {
			properties.load(new StringReader(preprocessed));
		} catch (IOException e) {
			ShaderLogger.LOGGER.error("Error loading the MC_VERSION " + IRIS_ERA_MC_VERSION + " evaluation of shaders.properties", e);
			throw new UncheckedIOException(e);
		}

		return properties;
	}

	private static List<StringPair> collectPerBufferDirectives(Properties properties) {
		final List<StringPair> directives = new ArrayList<>();

		properties.forEach((keyObject, valueObject) -> {
			final String key = (String) keyObject;

			if (BufferBlendDirective.isPerBufferKey(key)) {
				directives.add(new StringPair(key, (String) valueObject));
			}
		});

		return directives;
	}

	private static boolean isConfigured(Map<String, ? extends List<BufferBlendInformation>> configured, BufferBlendDirective directive) {
		final List<BufferBlendInformation> programOverrides = configured.get(directive.program());

		if (programOverrides == null) {
			return false;
		}

		for (BufferBlendInformation override : programOverrides) {
			if (override.getIndex() == directive.index()) {
				return true;
			}
		}

		return false;
	}

	private static String describe(String key, String value) {
		return key + "=" + value;
	}
}
