import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gtnewhorizons.retrofuturagradle.mcp.JSTTransformerTask
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils
import org.embeddedt.embeddium.gradle.build.conventions.ShadowHelper
import org.embeddedt.embeddium.gradle.mdg.remapper.ReobfuscateCodeAndMixinsTask

plugins {
    id("com.gtnewhorizons.retrofuturagradle")
    id("com.gradleup.shadow")
    id("embeddium-mdg-remapper")
    id("maven-publish")
}

group = "org.embeddedt"
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

/**
 * Resolves the JVM Downgrader artifact only so its licence text can be copied into the shipped jar next to the
 * `xyz.wagyourtail.jvmdg` runtime stubs that common's `shadeDowngradedApi` shades in. Nothing from this
 * configuration is compiled against or bundled except the two licence files named in the `jar` task.
 * <p>
 * (Do not write a glob such as `jvmdg` + a double star in a Kotlin block comment: Kotlin nests block comments,
 * and the star-star-slash inside it opens a nested comment that a later string literal can close.)
 */
val jvmDowngraderLicense = configurations.create("jvmDowngraderLicense") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    // Pinned to the plugin version at common/build.gradle:6; scripts/verify_forge_jar.py fails the jar if the
    // licence entries stop matching that pin.
    add(jvmDowngraderLicense.name, "xyz.wagyourtail.jvmdowngrader:jvmdowngrader:2.0.1")
}

val celeritasLwjglVersion = "3.3.3"

minecraft {
    mcVersion.set("1.12.2")
}

repositories {
    // For the JVM Downgrader licence artifact (jvmDowngraderLicense); the runtime stubs it covers are shaded
    // in by common, which resolves it from this same repository.
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
        }
    }
    exclusiveContent {
        forRepository { maven("https://maven.taumc.org/releases") }
        filter {
            includeGroupAndSubgroups("org.taumc")
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
    // Own Java-8-compatible classes only. LWJGL and native libraries belong to the active loader.
    implementation(project(":celtium-probe", configuration = "modEmbed")) { isTransitive = false }
    modEmbedDependencies(project(":celtium-probe", configuration = "modEmbed")) { isTransitive = false }
    // In-mod Vulkan bootstrap/backend classes. Compiled against LWJGL, never bundled with it.
    implementation(project(":celtium-vulkan", configuration = "modEmbed")) { isTransitive = false }
    modEmbedDependencies(project(":celtium-vulkan", configuration = "modEmbed")) { isTransitive = false }
    val lombokVersion = rootProject.findProperty("lombok_version").toString()
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    val jabelVersion = rootProject.findProperty("jabel_version").toString()

    annotationProcessor("com.github.GTNewHorizons:jabel-javac-plugin:${jabelVersion}")
    compileOnly("com.github.GTNewHorizons:jabel-javac-plugin:${jabelVersion}")

    implementation(project(":common", configuration = "downgraded")) {
        isTransitive = false
    }
    modEmbedDependencies(project(":common", configuration = "downgraded")) {
        isTransitive = false
    }
    modEmbedDependencies("org.joml:joml:1.10.5")
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

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "21"
    options.release = 8

    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named("reobfJar").configure {
    enabled = false
}

tasks.register<ReobfuscateCodeAndMixinsTask>("celeritasRemapJar") {
    tsrgMappings = mcpTasks.srgFile("mcp-srg.srg")
    deobfMinecraftJar = mcpTasks.taskPackagePatchedMc.flatMap { it.archiveFile }
    classpath = sourceSets.main.get().compileClasspath
    archiveBaseName.set(base.archivesName)
    archiveClassifier.set("reobf")
    input = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dependsOn(mcpTasks.taskGenerateForgeSrgMappings)
}

ShadowHelper.createShadowRemapJar(project, "celeritasRemapJar")

tasks.named<ShadowJar>("shadowRemapJar") {
    configurations.set(listOf(modEmbedDependencies))
    exclude("module-info.class", "**/module-info.class")
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
    // The JVM Downgrader runtime stubs (xyz/wagyourtail/jvmdg/**, shaded in by common's shadeDowngradedApi)
    // are LGPL-2.1, read from the dependency's own metadata rather than from memory:
    //   - jvmdowngrader-2.0.1.pom declares <licenses><license><name>LGPL-2.1</name>
    //   - its LICENSE.md offers LGPL-2.1 or a commercial licence and states explicitly that shading its api
    //     classes into a Combined Work is permitted provided the unshaded jar or the source is available,
    //     which this repository is (LGPL-3.0, sources included).
    // The version below is the plugin pin at common/build.gradle:6; scripts/verify_forge_jar.py fails the jar
    // if the two drift apart, because a licence notice for the wrong version is worse than none.
    from(configurations.named("jvmDowngraderLicense").map { zipTree(it.singleFile) }) {
        include("LICENSE.md")
        into("licenses")
        rename { "jvmdowngrader-LICENSE.md" }
    }
    from(configurations.named("jvmDowngraderLicense").map { zipTree(it.singleFile) }) {
        include("license/LGPLv2.1.md")
        // eachFile, not rename: the include keeps the `license/` directory in the path and rename only changes
        // the file name, which shipped the text as licenses/license/jvmdowngrader-LGPLv2.1.md.
        eachFile { path = "licenses/jvmdowngrader-LGPLv2.1.md" }
        includeEmptyDirs = false
    }
    manifest {
        attributes["FMLAT"] = "celeritas_at.cfg"
        attributes["FMLCorePlugin"] = "org.taumc.celeritas.core.CeleritasLoadingPlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
        attributes["ForceLoadAsMod"] = "true"
        // Replaces the stale `Celtium-Stage: backend-boundary` (a 2026-10-09 label). These two are true today
        // and each is enforced by code named in docs/scope-and-limits.md section 0.
        attributes["Celtium-License"] = "LGPL-3.0"
        attributes["Celtium-Vulkan-Minimum"] = "1.2"
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

    filesMatching("mcmod.info") {
        expand(mapOf("version" to inputs.properties["version"]))
    }

    val repositoryRoot = rootProject.findProperty("celtium_repository_root") ?: rootProject.projectDir
    from(file("$repositoryRoot/modern/src/main/resources/icon.png"))
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
val validation = sourceSets.create("validation") {
    java.srcDir("../validation/client/src/main/java")
    // JEI's recipe screen is exercised only when the mod's jar has been fetched into the work directory. The plugin source
    // lives in its own directory, so a normal build - and every ordinary run - is unchanged by its absence.
    val jeiJar = file("${rootProject.projectDir.parentFile}/.celtium-work/major-mods")
        .listFiles()?.firstOrNull { it.name.startsWith("jei_") && it.name.endsWith(".jar") }
    if (jeiJar != null) {
        java.srcDir("../validation/client/src/jei/java")
        logger.lifecycle("validation client: JEI plugin enabled against ${jeiJar.name}")
    }
    compileClasspath += sourceSets.main.get().compileClasspath.filter {
        !it.name.startsWith("jabel-javac-plugin-")
    }
}
dependencies {
    // Same condition as the source directory above: the fixture can reach the mod's plugin API only when the jar is present.
    file("${rootProject.projectDir.parentFile}/.celtium-work/major-mods")
        .listFiles()?.firstOrNull { it.name.startsWith("jei_") && it.name.endsWith(".jar") }
        ?.let { add(validation.compileOnlyConfigurationName, files(it)) }
    add(validation.compileOnlyConfigurationName, platform("org.lwjgl:lwjgl-bom:3.4.1"))
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl")
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-opengl")
    // The fixture drives window resizes to prove the renderer survives a framebuffer size change, so it needs the GLFW
    // binding at compile time. Compile-only, like the rest: nothing here is part of Celtium's distributable.
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-glfw")
    add(validation.compileOnlyConfigurationName, "com.google.code.gson:gson:2.10.1")
}
tasks.named<JavaCompile>(validation.compileJavaTaskName) {
    options.release = 8
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(21) }
}
val validationJar = tasks.register<Jar>("validationJar") {
    from(validation.output)
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
