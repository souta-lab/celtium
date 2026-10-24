#!/usr/bin/env python3
"""Execute raster setters rewritten from real LWJGL 3.4.3 native declarations; CPU ABI proof only, no graphics context."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def cached_jar(group, artifact, version=None):
    parent = CACHE / group / artifact
    versions = [version] if version else sorted((p.name for p in parent.iterdir() if p.is_dir()),
                                               key=lambda x: tuple(int(p) if p.isdigit() else 0 for p in x.split('.')),
                                               reverse=True)
    for name in versions:
        found = sorted((parent / name).glob(f'*/{artifact}-{name}.jar'))
        if found:
            return found[0]
    raise FileNotFoundError(f'{group}:{artifact}:{version or "cached version"}; supply its explicit path')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--renderer-jar', type=Path, required=True)
    parser.add_argument('--lwjgl-opengl', type=Path)
    parser.add_argument('--asm', type=Path)
    args = parser.parse_args()
    renderer = args.renderer_jar.resolve(strict=True)
    opengl = args.lwjgl_opengl or cached_jar('org.lwjgl', 'lwjgl-opengl', '3.4.3')
    asm = args.asm or cached_jar('org.ow2.asm', 'asm')
    java_home = os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')
    javac, java = str(Path(java_home) / 'bin/javac'), str(Path(java_home) / 'bin/java')
    for executable in (javac, java):
        if not Path(executable).is_file():
            raise FileNotFoundError(f'JDK 21+ required: {executable}')
    with tempfile.TemporaryDirectory(prefix='celtium-raster-abi-') as directory:
        work = Path(directory)
        transformer_stub = work / 'net/minecraft/launchwrapper/IClassTransformer.java'
        transformer_stub.parent.mkdir(parents=True)
        transformer_stub.write_text('package net.minecraft.launchwrapper; public interface IClassTransformer { '
                                    'byte[] transform(String name, String transformedName, byte[] bytes); }')
        logger = work / 'org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java'
        logger.parent.mkdir(parents=True)
        logger.write_text('package org.taumc.celeritas.mixin; public final class CeleritasVintageMixinPlugin { '
                          'public static final Log LOGGER = new Log(); public static class Log { '
                          'public void info(String s, Object... a) {} '
                          'public void error(String s, Object... a) { throw new AssertionError(s); } } }')
        sources = [transformer_stub, logger,
                   ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java',
                   ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/NativeRasterAdapter.java',
                   ROOT / 'scripts/cpu-contract/NativeRasterAdapterCheck.java']
        classpath = os.pathsep.join(map(str, [asm, renderer]))
        subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(work), *map(str, sources)], check=True)
        # Temporary logger/class-transformer stubs precede the packaged mod; no Minecraft initialization is allowed.
        subprocess.run([java, '-cp', os.pathsep.join([str(work), classpath]), 'NativeRasterAdapterCheck', str(opengl)],
                       check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
