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
    manifest {
        attributes["FMLAT"] = "celeritas_at.cfg"
        attributes["FMLCorePlugin"] = "org.taumc.celeritas.core.CeleritasLoadingPlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
        attributes["ForceLoadAsMod"] = "true"
        attributes["Celtium-Stage"] = "capability-probe"
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
    compileClasspath += sourceSets.main.get().compileClasspath.filter {
        !it.name.startsWith("jabel-javac-plugin-")
    }
}
dependencies {
    add(validation.compileOnlyConfigurationName, platform("org.lwjgl:lwjgl-bom:3.4.1"))
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl")
    add(validation.compileOnlyConfigurationName, "org.lwjgl:lwjgl-opengl")
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
