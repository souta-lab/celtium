package org.soutalab.celtium.shader.shaderpack.discovery;

import org.soutalab.celtium.shader.ShaderLogger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipError;

public class ShaderpackDirectoryManager {
	private final Path root;

	public ShaderpackDirectoryManager(Path root) {
		this.root = root;
	}

	public void copyPackIntoDirectory(String name, Path source) throws IOException {
		Path target = root.resolve(name);

		// Copy the pack file into the shaderpacks folder.
		Files.copy(source, target);
		// Zip or other archive files will be copied without issue,
		// however normal folders will require additional handling below.

		// Manually copy the contents of the pack if it is a folder
		if (Files.isDirectory(source)) {
			// Use for loops instead of forEach due to createDirectory throwing an IOException
			// which requires additional handling when used in a lambda

			// Copy all sub folders, collected as a list in order to prevent issues with non-ordered sets
			try (Stream<Path> stream = Files.walk(source)) {
				for (Path p : stream.filter(Files::isDirectory).collect(Collectors.toList())) {
					Path folder = source.relativize(p);

					if (Files.exists(folder)) {
						continue;
					}

					Files.createDirectory(target.resolve(folder));
				}
			}

			// Copy all non-folder files
			try (Stream<Path> stream = Files.walk(source)) {
				for (Path p : stream.filter(p -> !Files.isDirectory(p)).collect(Collectors.toSet())) {
					Path file = source.relativize(p);

					Files.copy(p, target.resolve(file));
				}
			}
		}
	}

	public Collection<String> enumerate() throws IOException {
		// Make sure the list is sorted since not all OSes sort the list of files in the directory.
		// Case-insensitive sorting is the most intuitive for the user, but we then sort naturally
		// afterwards so that we don't alternate cases weirdly in the sorted list.
		//
		// We also ignore chat formatting characters when sorting - some shader packs include chat
		// formatting in the file name so that they have fancy text when displayed in the shaders list.
		Comparator<String> baseComparator = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
		Comparator<String> comparator = (a, b) -> {
			a = removeFormatting(a);
			b = removeFormatting(b);

			return baseComparator.compare(a, b);
		};

		try (Stream<Path> list = Files.list(root)) {
			return list.filter(this::isValidShaderpack)
				.map(path -> path.getFileName().toString())
				.sorted(comparator).collect(Collectors.toList());
		}
	}

	/**
	 * Whether {@code pack} looks like a shader pack: a folder containing a {@code shaders}
	 * directory, or a zip containing one. Ported from the Iris facade predicate, with the
	 * shaderpacks-directory self-check resolved against this manager's own root.
	 */
	public boolean isValidShaderpack(Path pack) {
		if (Files.isDirectory(pack)) {
			// Sometimes the shaderpack directory itself can be
			// identified as a shader pack due to it containing
			// folders which contain "shaders" folders, this is
			// necessary to check against that
			if (pack.equals(root)) {
				return false;
			}
			try (Stream<Path> stream = Files.walk(pack)) {
				return stream.filter(Files::isDirectory)
					// Prevent a pack simply named "shaders" from being
					// identified as a valid pack
					.filter(path -> !path.equals(pack)).anyMatch(path -> path.endsWith("shaders"));
			} catch (IOException e) {
				// Not a valid pack, but never fail silently: this is what makes a
				// configured pack vanish from the selection list.
				ShaderLogger.LOGGER.warn("Failed to inspect potential shaderpack folder \"{}\", it will be skipped", pack, e);
			}
		}

		if (pack.toString().endsWith(".zip")) {
			try (FileSystem zipSystem = FileSystems.newFileSystem(pack, ShaderpackDirectoryManager.class.getClassLoader())) {
				final Path zipRoot = zipSystem.getRootDirectories().iterator().next();
				try (Stream<Path> stream = Files.walk(zipRoot)) {
					return stream.filter(Files::isDirectory).anyMatch(path -> path.endsWith("shaders"));
				}
			} catch (ZipError zipError) {
				// Java 8 seems to throw a ZipError instead of a subclass of IOException
				ShaderLogger.LOGGER.warn("The ZIP at " + pack + " is corrupt");
			} catch (IOException e) {
				// Same here: a transient read failure (file lock, sync client) hides
				// the pack from the list, which then silently drops apply requests.
				ShaderLogger.LOGGER.warn("Failed to inspect potential shaderpack zip \"{}\", it will be skipped", pack, e);
			}
		}

		return false;
	}

	/**
	 * Straightforward method to use section-sign based chat formatting from a String
	 */
	private static String removeFormatting(String formatted) {
		char[] original = formatted.toCharArray();
		char[] cleaned = new char[original.length];
		int c = 0;

		for (int i = 0; i < original.length; i++) {
			// check if it's a section sign
			if (original[i] == '\u00a7') {
				i++;
			} else {
				cleaned[c++] = original[i];
			}
		}

		return new String(cleaned, 0, c);
	}

	public URI getDirectoryUri() {
		return root.toUri();
	}
}
