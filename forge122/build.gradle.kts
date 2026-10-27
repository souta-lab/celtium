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

    implementation(project(":common", configuration = "modJar")) {
        isTransitive = false
    }
    modEmbedDependencies(project(":common", configuration = "modJar")) {
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

// Release 21, not Java 8 with newer syntax: the mod runs on Cleanroom only, whose JVM is 21+, and the Vulkan module it
// embeds was already release 21. The Java 8 API ceiling only cost the code the platform's own library.
tasks.named<JavaCompile>("compileJava") {
    options.release = 21

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
    // The embedded module jars are inputs in their own right. Without this the task reported UP-TO-DATE after a module
    // had been rebuilt, so a leg could run a jar whose embedded classes predate the change - measured: the packaged jar's
    // hash did not move across a shaderpack fix, and three separate rounds verified their fixes offline because of it.
    inputs.files(modEmbedDependencies)
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
    manifest {
        attributes["FMLAT"] = "celeritas_at.cfg"
        attributes["FMLCorePlugin"] = "org.taumc.celeritas.core.CeleritasLoadingPlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
        attributes["ForceLoadAsMod"] = "true"
        // Replaces the stale `Celtium-Stage: backend-boundary` (a 2026-10-09 label). These two are true today
        // and each is enforced by code named in docs/history/scope-and-limits.md section 0.
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
dependencies {
    // Same exact opt-in jar as the plugin's source set, so the compiled API and the
    // tested HEI/JEI runtime cannot silently differ.
    validationJeiJar?.let { add(validation.compileOnlyConfigurationName, files(it)) }
    add(validation.compileOnlyConfigurationName, platform("org.lwjgl:lwjgl-bom:3.4.1"))
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl")
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-opengl")
    // The fixture drives window resizes to prove the renderer survives a framebuffer size change, so it needs the GLFW
    // binding at compile time. Compile-only, like the rest: nothing here is part of Celtium's distributable.
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-glfw")
    add(validation.compileOnlyConfigurationName, "com.google.code.gson:gson:2.10.1")
}
tasks.named<JavaCompile>(validation.compileJavaTaskName) {
    options.release = 21
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
