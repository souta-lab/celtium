# CI: the shader compiler was a build dependency living in a gitignored directory

Owner: **glyph-fix**. Every PR failed the `minecraft-1122` job with
`java.io.IOException: Cannot run program "/home/runner/work/celtium/celtium/.ce…"` / `Exec failed, error: 2`.
Local builds were green.

## Root cause, named

`.github/workflows/celtium.yaml` step `Build Minecraft 1.12.2 mod` runs
`./gradlew -p build-1122 :forge122:packageJar`, and `:forge122:packageJar` pulls in `:celtium-vulkan`'s resources,
which depend on **`celtium-vulkan/build.gradle:124-133`**:

```groovy
def compileTerrainShaders = tasks.register('compileTerrainShaders', CeltiumCompileShaders) {
    def compilerSetting = providers.gradleProperty('glslang_validator')
            .getOrElse('.celtium-work/gpu-tools/extracted/usr/bin/glslangValidator')   // line 126-127
    ...
}
tasks.named('processResources') { dependsOn compileTerrainShaders }                     // line 134
```

The default compiler path is **workspace-relative and gitignored** (`.gitignore:47` ignores `/.celtium-work/`), so a
CI checkout has no such file and the task cannot start the process. The task is not optional: the SPIR-V it emits is
added to the main resources (`sourceSets.main.resources.srcDir(terrainSpirvOutput)`) and is what the loader reads, so
skipping it would ship a jar without shaders.

## Reproduced

Not by renaming `.celtium-work` - a GPU job held the slot at the time and its run directories live there. The
equivalent isolation is a clean worktree, which is what CI has:

```sh
git worktree add --detach /tmp/celtium-ci HEAD        # no .celtium-work, no build outputs
cd /tmp/celtium-ci && ./gradlew -p build-1122 :forge122:packageJar --console=plain --stacktrace
```

```
* What went wrong:
Execution failed for task ':celtium-vulkan:compileTerrainShaders' (registered in build file '../celtium-vulkan/build.gradle').
> A problem occurred starting process 'command '/tmp/celtium-ci/.celtium-work/gpu-tools/extracted/usr/bin/glslangValidator''
Caused by: java.io.IOException: Cannot run program ".../glslangValidator" ...: Exec failed, error: 2
```

Same task, same `error: 2`, same shape as the CI log.

## Fix

The workflow now installs the compiler and points the task at it through the property the task already reads, which
keeps the compile and its validation in the build (nothing skipped, no check removed):

```yaml
      - name: Install the shader compiler
        run: |
          sudo apt-get update
          sudo apt-get install -y --no-install-recommends glslang-tools
          glslangValidator --version | head -1
      - name: Build Minecraft 1.12.2 mod
        run: >-
          ./gradlew -p build-1122 :forge122:packageJar --console=plain --stacktrace
          "-Pglslang_validator=$(command -v glslangValidator)"
```

The alternative - editing `celtium-vulkan/build.gradle` to fall back to `PATH` and to fail with a named message - was
implemented, verified, and then **reverted**, because the unit's write scope excludes `celtium-vulkan/`. It remains the
better shape (a checkout without the tool would say what to install instead of `error: 2`); the workflow fix is what
this unit was scoped to.

## Verification (raw)

**(i) clean checkout, no `.celtium-work`, compiler on PATH (the CI case), exact workflow command** - the wrapper
`/tmp/ci-bin/glslangValidator` stands in for the installed package:

```
$ cd /tmp/celtium-ci && PATH=/tmp/ci-bin:$PATH ./gradlew -p build-1122 :forge122:packageJar --console=plain --stacktrace "-Pglslang_validator=$(command -v glslangValidator)"
> Task :celtium-vulkan:compileTerrainShaders
BUILD SUCCESSFUL in 6s
$ ls build-1122/build/libs/*/*.jar
celtium-forge-mc1.12.2-0.0.2-alpha-dev.jar   (2,594,947 bytes)
$ find . -name '*.spv' | wc -l          # 66 generated (33 shaders, two copies)
$ unzip -l build-1122/build/libs/*/*.jar | grep -c '\.spv'   # 33 in the packaged jar
$ python3 scripts/verify_forge_jar.py build-1122/build/libs/*/*.jar   # the job's second step
  "errors": [], "verified": true
```

**(ii) normal state, `.celtium-work` present, the task's own default resolution**:

```
$ cd /home/s0u7a/projects/celtium && ./gradlew -p build-1122 :celtium-vulkan:compileTerrainShaders --console=plain
> Task :celtium-vulkan:compileTerrainShaders
BUILD SUCCESSFUL in 4s
$ find celtium-vulkan/build -name '*.spv' | wc -l    # 70 outputs present
```

**(iii) not testable here**: the `apt-get install glslang-tools` line itself (this machine's package state was left
alone; `apt-cache policy` shows the same 16.6.0-1 the workspace copy was extracted from). The step prints
`glslangValidator --version`, so a renamed package fails loudly at that step rather than silently later. Also untested:
whether Ubuntu 24.04's `glslang-tools` (a different upstream version than the pinned 16.6.0) accepts all 33 shaders;
the shaders target `vulkan1.0` and are plain GLSL, but the first CI run is the evidence for that, not this document.
