import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gtnewhorizons.retrofuturagradle.mcp.JSTTransformerTask
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils
import org.soutalab.celtium.gradle.remapper.ShadowHelper
import org.soutalab.celtium.gradle.remapper.ReobfuscateCodeAndMixinsTask

plugins {
    id("com.gtnewhorizons.retrofuturagradle")
    // Versioned here since the buildSrc tree that used to put the Shadow plugin on the build classpath was
    // retired with the LWJGL service layer (S1-b); 9.6.1 is the version that classpath carried.
    id("com.gradleup.shadow") version "9.6.1"
    id("celtium-remapper")
    id("maven-publish")
}

group = "org.soutalab.celtium"
version = rootProject.version

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

base.archivesName = "celtium-forge-mc1.12.2"

val modCompileOnly = configurations.create("modCompileOnly")
configurations.compileOnly.get().extendsFrom(modCompileOnly)
val modRuntimeOnly = configurations.create("modRuntimeOnly")
configurations.runtimeOnly.get().extendsFrom(modRuntimeOnly)
// Shadow's 'shadow' configuration denotes external files for its manifest.
// Keep jars that we actually embed in a separate configuration.
val modEmbedDependencies = configurations.create("modEmbedDependencies") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes { attribute(ModUtils.DEOBFUSCATOR_TRANSFORMED, true) }
}
// The one :celtium-core artifact the validation client jar draws its harness reader from (see the
// validationJar task): the same harnessJar the mod itself embeds, filtered there to the harness package.
val validationHarnessEmbed = configurations.create("validationHarnessEmbed") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes { attribute(ModUtils.DEOBFUSCATOR_TRANSFORMED, true) }
}

val celeritasLwjglVersion = "3.3.3"

minecraft {
    mcVersion.set("1.12.2")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://maven.cleanroommc.com") }
        filter {
            includeGroup("zone.rong")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://cursemaven.com")
            }
        }
        filter {
            includeGroup("curse.maven")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    exclusiveContent {
        forRepository { maven("https://nexus.gtnewhorizons.com/repository/public/") }
        filter {
            includeGroupAndSubgroups("com.gtnewhorizons")
            includeGroup("com.github.GTNewHorizons")
            // The one org.taumc artifact this build consumes - the GLSL transformation
            // library the direct-GL converter embeds (S4) - is a GTNH build published
            // only on this nexus; the maven.taumc.org exclusive block that used to claim
            // the group resolved nothing and was removed so the group can land here.
            // S5-a's modEmbed of the same library resolves through this claim as well.
            includeGroup("org.taumc")
        }
    }
    mavenCentral()
}

val forgePatchDeps = configurations.create("forgePatchDeps") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    named("shadow") {
        attributes {
            attribute(ModUtils.DEOBFUSCATOR_TRANSFORMED, true)
        }
    }
}

dependencies {
    // In-mod Vulkan bootstrap/backend classes. Keep LWJGL core/platform/native modules supplied by Cleanroom.
    implementation(project(":celtium-vulkan", configuration = "modEmbed")) { isTransitive = false }
    modEmbedDependencies(project(":celtium-vulkan", configuration = "modEmbed")) { isTransitive = false }
    // The Vulkan *Java bindings* are not part of every Cleanroom 0.7 installation. The validation
    // harness adds this jar to the process classpath, but stock Prism instances do not, causing
    // a GL-less window with no Vulkan renderer (permanent black screen). Embed exactly this module
    // at Cleanroom 0.7's LWJGL version, without any transitives or replacement LWJGL natives.
    modEmbedDependencies("org.lwjgl:lwjgl-vulkan:${rootProject.findProperty("celtium_mod_lwjgl_version")}") {
        isTransitive = false
    }
    // The shaderc *Java binding* (no natives), for a mod's own GLSL programs (ModShaderCompiler). Cleanroom's mod
    // class loader does not see jars added to the launcher classpath (measured 2026-11-05, leg r15-gt:
    // ClassNotFoundException for org.lwjgl.util.shaderc.Shaderc with the jar on -cp), so the binding is embedded the
    // way lwjgl-vulkan is. Without libshaderc the binding fails its own load and the programs stay refused; whether
    // the natives ship is a separate distribution decision.
    modEmbedDependencies("org.lwjgl:lwjgl-shaderc:${rootProject.findProperty("celtium_mod_lwjgl_version")}") {
        isTransitive = false
    }
    // Its natives, decided 2026-11-05 by the user: without them the binding fails its own load on every ordinary
    // install and a mod's GL program stays refused. LWJGL extracts them from the binding class's own class loader
    // (linux/x64/org/lwjgl/shaderc/libshaderc.so, windows/x64/.../shaderc.dll), which is the mod jar.
    for (platform in listOf("natives-linux", "natives-windows")) {
        modEmbedDependencies("org.lwjgl:lwjgl-shaderc:${rootProject.findProperty("celtium_mod_lwjgl_version")}:$platform") {
            isTransitive = false
        }
    }
    val lombokVersion = rootProject.findProperty("lombok_version").toString()
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    implementation(project(":common", configuration = "modJar")) {
        isTransitive = false
    }
    modEmbedDependencies(project(":common", configuration = "modJar")) {
        isTransitive = false
    }
    // HarnessConfig moved from :common to :celtium-core in the v0.4.0 S1-a stage. The harnessJar
    // artifact carries it at release 21, which this module's 21 compiler can read.
    implementation(project(":celtium-core", configuration = "harnessJar")) {
        isTransitive = false
    }
    // The v0.4.0 S2-a stage puts :celtium-core's own release-25 contract classes on this module's
    // classpath after all: the zero-based terrain mesher (org.soutalab.celtium.forge.terrain)
    // produces the core geometry contracts (PackedAxisQuad / GeneralQuad / SectionDesc) directly.
    // This module's compiler is the JDK 25 javac (see the S1-a compiler swap), which reads
    // release-25 class files while still emitting release 21; the game JVM is Java 25+, so the
    // embedded classes load as they are. The mainJar artifact carries the main source set only,
    // so no class arrives twice (the harness classes come from the harnessJar above).
    implementation(project(":celtium-core", configuration = "mainJar")) {
        isTransitive = false
    }
    modEmbedDependencies(project(":celtium-core", configuration = "mainJar")) {
        isTransitive = false
    }
    modEmbedDependencies(project(":celtium-core", configuration = "harnessJar")) {
        isTransitive = false
    }
    validationHarnessEmbed(project(":celtium-core", configuration = "harnessJar")) {
        isTransitive = false
    }
    // The direct-GL converter and built-in shader pack interpreter use the GLSL
    // transformation library and its ANTLR 4 runtime. Both jars ship inside the
    // mod jar, non-transitive as in Actinium's shadowBundle declarations.
    modEmbedDependencies("org.taumc:glsl-transformation-lib:0.2.0-32.g7dd88a4-GTNH") {
        isTransitive = false
    }
    modEmbedDependencies("org.antlr:antlr4-runtime:4.13.2") {
        isTransitive = false
    }
    modEmbedDependencies("org.joml:joml:1.10.5")
    // The shader pack interpreter also uses jcpp, relocated in shadowRemapJar below.
    modEmbedDependencies("org.anarres:jcpp:1.4.14") { isTransitive = false }
    implementation("org.joml:joml:1.10.5")
    implementation("zone.rong:mixinbooter:10.5")
    // LWJGL is a direct dependency now: the immediate path reads a ByteBuffer address with MemoryUtil instead of
    // calling it reflectively, which matches the pinned runtime (LWJGL 3.4.1) and removes the last reflective call
    // from the per-batch path (docs/plan.md section 0).
    compileOnly("org.lwjgl:lwjgl:3.4.1")
    compileOnly("com.gtnewhorizons.retrofuturabootstrap:RetroFuturaBootstrap:1.0.11") {
        exclude(group = "org.apache.logging.log4j")
    }
    "modRuntimeOnly"("curse.maven:ae2-223794:2747063")
    modCompileOnly("maven.modrinth:fluidlogged-api:3.0.6")
}

// Release 21, not Java 8 with newer syntax: the mod runs on Cleanroom only, whose JVM is 21+, and the Vulkan module it
// embeds was already release 21. The Java 8 API ceiling only cost the code the platform's own library.
// Since the v0.4.0 S1-a stage the embedded :celtium-vulkan jar is release 25 (class file 69), which a
// 21 compiler's class reader refuses outright ("wrong version 69.0, should be 65.0") - so the compiler
// is the 25 toolchain while options.release stays 21: a 25 javac under --release 21 still emits 65
// bytecode against the 21 API ceiling and reads the 69 classes on the classpath (measured on the
// agera gate). The output contract of this module does not move; only the reader widened.
tasks.named<JavaCompile>("compileJava") {
    options.release = 21

    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named("reobfJar").configure {
    enabled = false
}

tasks.register<ReobfuscateCodeAndMixinsTask>("celtiumRemapJar") {
    tsrgMappings = mcpTasks.srgFile("mcp-srg.srg")
    deobfMinecraftJar = mcpTasks.taskPackagePatchedMc.flatMap { it.archiveFile }
    classpath = sourceSets.main.get().compileClasspath
    archiveBaseName.set(base.archivesName)
    archiveClassifier.set("reobf")
    input = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dependsOn(mcpTasks.taskGenerateForgeSrgMappings)
}

ShadowHelper.createShadowRemapJar(project, "celtiumRemapJar")

tasks.named<ShadowJar>("shadowRemapJar") {
    configurations.set(listOf(modEmbedDependencies))
    // The embedded module jars are inputs in their own right. Without this the task reported UP-TO-DATE after a module
    // had been rebuilt, so a leg could run a jar whose embedded classes predate the change - measured: the packaged jar's
    // hash did not move across a shaderpack fix, and three separate rounds verified their fixes offline because of it.
    inputs.files(modEmbedDependencies)
    exclude("module-info.class", "**/module-info.class")
    relocate("org.anarres.cpp", "org.soutalab.celtium.impl.shadow.jcpp")
    relocate("org.antlr", "org.soutalab.celtium.impl.shadow.antlr")
    relocate("org.taumc.glsl", "org.soutalab.celtium.impl.shadow.glsl")
}

tasks.named<JSTTransformerTask>("applyJST") {
    accessTransformerFiles.from("src/main/resources/META-INF/celeritas_at.cfg")
}

tasks.named<Jar>("jar") {
    val repositoryRoot = rootProject.findProperty("celtium_repository_root") ?: rootProject.projectDir
    from(file(repositoryRoot)) {
        include("README.md", "docs/upstream-README.md")
    }
    from(file("$repositoryRoot/common/src/main/java/grondag/bitraster/LICENSE")) {
        into("licenses")
        rename { "bitraster-LICENSE" }
    }
    // LWJGL Vulkan Java bindings are bundled since v0.1.2-alpha; include their BSD-3-Clause notice.
    from(file("$repositoryRoot/licenses/lwjgl-LICENSE")) {
        into("licenses")
    }
    // The shaderc natives embedded above link shaderc, SPIRV-Tools, SPIRV-Headers and glslang; their notices.
    from(file("$repositoryRoot/licenses/shaderc-LICENSE")) {
        into("licenses")
    }
    // The GLSL transformation library and ANTLR 4 runtime embedded above (S4); their notices.
    from(file("$repositoryRoot/licenses/glsl-transformation-lib-LICENSE")) {
        into("licenses")
    }
    from(file("$repositoryRoot/licenses/antlr4-runtime-LICENSE")) {
        into("licenses")
    }
    manifest {
        attributes["FMLAT"] = "celeritas_at.cfg"
        attributes["FMLCorePlugin"] = "org.taumc.celeritas.core.CeleritasLoadingPlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
        attributes["ForceLoadAsMod"] = "true"
        // Replaces the stale `Celtium-Stage: backend-boundary` (a 2026-10-09 label). These two are true today
        // and each is enforced by code named in docs/history/scope-and-limits.md section 0.
        attributes["Celtium-License"] = "LGPL-3.0"
        attributes["Celtium-Vulkan-Minimum"] = "1.3"
        attributes["Celtium-Upstream-Commit"] = "989ebf2e52ceed052ec112a569e0c1d172095756"
    }
}

tasks.register("packageJar", Copy::class) {
    from(tasks.named<ShadowJar>("shadowRemapJar").get().archiveFile)
    into("${rootProject.layout.buildDirectory.get()}/libs/${project.version}")
    dependsOn(tasks.named("shadowRemapJar"))
}

tasks.processResources.configure {
    inputs.property("version", version)

    filesMatching(listOf("mcmod.info", "version.properties")) {
        expand(mapOf("version" to inputs.properties["version"]))
    }

}

publishing {
    publications {
        create<MavenPublication>("default") {
            artifactId = base.archivesName.get()
            artifact(tasks.named<ShadowJar>("shadowRemapJar").map { it.archiveFile })
            artifact(tasks.named("sourcesJar")) {
                classifier = "sources"
            }
        }
    }
}

// A separate test mod drives a fixed world and observes the existing GL FBO.
// None of its classes or dependencies are part of Celtium's distributable.
// The optional HEI/JEI fixture must be compiled against the *exact* mod jar used in the
// runtime acceptance leg. Opt in explicitly for reproducible runs; retain the historical
// work-directory fallback for earlier acceptance scripts. Neither mode shades HEI/JEI into
// the published Celtium jar. An incorrect explicit path is an error, not silent partial proof.
val validationJeiJar = providers.gradleProperty("celtium_validation_jei_jar").orNull?.let { supplied ->
    file(supplied).also { require(it.isFile) { "celtium_validation_jei_jar is not a file: $supplied" } }
} ?: file("${rootProject.projectDir.parentFile}/.celtium-work/major-mods")
    .listFiles()?.firstOrNull { it.name.startsWith("jei_") && it.name.endsWith(".jar") }

val validation = sourceSets.create("validation") {
    java.srcDir("../validation/client/src/main/java")
    if (validationJeiJar != null) {
        java.srcDir("../validation/client/src/jei/java")
        logger.lifecycle("validation client: JEI/HEI plugin enabled against ${validationJeiJar.name}")
    }
    compileClasspath += sourceSets.main.get().compileClasspath
}

// Compile-time-only stubs of optional-mod types whose exact descriptors @Pseudo mixin
// handlers must declare (item 648: Demonica/Angelica timing brackets). The stubs output is
// on main's compile classpath only; none of it is part of Celtium's distributable, and at
// runtime the real mod classes supply the types.
val stubs = sourceSets.create("stubs") {
    java.srcDir("src/stubs/java")
}
sourceSets.named("main") {
    compileClasspath += stubs.output
}
dependencies {
    // Same exact opt-in jar as the plugin's source set, so the compiled API and the
    // tested HEI/JEI runtime cannot silently differ.
    validationJeiJar?.let { add(validation.compileOnlyConfigurationName, files(it)) }
    add(validation.compileOnlyConfigurationName, platform("org.lwjgl:lwjgl-bom:3.4.1"))
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl")
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-opengl")
    // The GPU copy fixture records barriers and reads back the result; the runtime binding is supplied by Celtium.
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-vulkan")
    // The fixture drives window resizes to prove the renderer survives a framebuffer size change, so it needs the GLFW
    // binding at compile time. Compile-only, like the rest: nothing here is part of Celtium's distributable.
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-glfw")
    add(validation.compileOnlyConfigurationName, "com.google.code.gson:gson:2.10.1")
}
tasks.named<JavaCompile>(validation.compileJavaTaskName) {
    options.release = 21
    // 25 compiler for the same reason as compileJava above: the classpath carries release-25 classes.
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
}
val validationJar = tasks.register<Jar>("validationJar") {
    from(validation.output)
    // HarnessConfig (org.soutalab.celtium.harness, in :celtium-core since the v0.4.0 S1-a move) is the
    // harness's one configuration
    // reader, and ValidationClient's static init touches it before anything else. It normally reaches
    // the game through the mod jar's embedded copy of the harnessJar, so a leg whose renderer slot holds a
    // different renderer (item 341's Celeritas reference arm) died with NoClassDefFoundError during
    // "Initializing game" - the fixture had silently become renderer-dependent. Ship the harness
    // package in this jar as well: the class is self-contained (JDK types only), and when both jars
    // are present the class loader takes either copy - both are compiled from the same source in the
    // same build, so the second copy cannot disagree with the first.
    from({ zipTree(validationHarnessEmbed.singleFile) }) {
        include("org/soutalab/celtium/harness/**")
    }
    dependsOn(validationHarnessEmbed)
    // Keep the proof inside the mod classloader, alongside the GL bindings
    // initialized by Cleanroom. Do not add this jar to the application classpath.
    providers.gradleProperty("celtium_validation_interop_jar").orNull?.let {
        from(zipTree(file(it))) { exclude("META-INF/MANIFEST.MF") }
        inputs.file(file(it))
    }
    archiveBaseName.set("celtium-validation-client")
    archiveClassifier.set("dev")
}
tasks.register<ReobfuscateCodeAndMixinsTask>("validationRemapJar") {
    tsrgMappings = mcpTasks.srgFile("mcp-srg.srg")
    deobfMinecraftJar = mcpTasks.taskPackagePatchedMc.flatMap { it.archiveFile }
    classpath = validation.compileClasspath
    archiveBaseName.set("celtium-validation-client")
    archiveClassifier.set("")
    input = validationJar.flatMap { it.archiveFile }
    dependsOn(mcpTasks.taskGenerateForgeSrgMappings)
}

// Offline unit tests for the pure decision logic that must not need a world to verify (item 378:
// the light-cache funnel's gate and key layout). The test source set sees the main compile
// classpath the same way the validation source set does; nothing here ships in the mod jar.
sourceSets.named("test") {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}
dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter:5.13.4")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
tasks.named<JavaCompile>("compileTestJava") {
    options.release = 21
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
// The test classes are release 21 like the main ones, but a Test task launches on the
// project toolchain (Java 8, kept for RFG) unless told otherwise - an 8 JVM cannot load
// class file 65.0 and the suite dies before running a single test. The launcher is 25
// since S1-a: the suite's classpath reaches the release-25 :celtium-vulkan classes
// through the main source set, and a 21 JVM cannot load class file 69 either.
tasks.named<Test>("test") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
