package org.soutalab.celtium.shaderpack.cli;

import org.soutalab.celtium.shaderpack.LoadOptions;
import org.soutalab.celtium.shaderpack.PackException;
import org.soutalab.celtium.shaderpack.PackManifest;
import org.soutalab.celtium.shaderpack.ShaderPackLoader;

import java.nio.file.Path;

/**
 * Prints the {@link PackManifest#toReport() canonical report} for a pack.
 *
 * <p>Usage: {@code PackInspect <pack.zip|packdir> [--mc-version N] [--profile NAME]}
 * This is a local inspection tool (e.g. running the S0 census against a real
 * pack zip); it performs no rendering.</p>
 */
public final class PackInspect {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: PackInspect <pack.zip|packdir> [--mc-version N] [--profile NAME]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        LoadOptions options = LoadOptions.defaults();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--mc-version" -> options = new LoadOptions(options.limits(),
                        Integer.parseInt(args[++i]), options.profile(),
                        options.optionOverrides(), options.extraDefines());
                case "--profile" -> options = options.withProfile(args[++i]);
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                }
            }
        }
        try {
            PackManifest manifest = ShaderPackLoader.load(input, options);
            System.out.print(manifest.toReport());
            if (manifest.hasErrors()) {
                System.exit(1);
            }
        } catch (PackException e) {
            System.err.println("FAILED " + e.code() + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
