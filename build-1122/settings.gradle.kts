pluginManagement {
    includeBuild("../plugins/celeritas-mdg-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Dependencies of the included remapper plugin are also resolved on
        // the consuming build's plugin classpath, not only in its own build.
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net/") }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.neoforged.net/releases") }
            filter { includeGroup("net.neoforged") }
        }
        exclusiveContent {
            forRepository { maven("https://nexus.gtnewhorizons.com/repository/public/") }
            filter { includeGroupAndSubgroups("com.gtnewhorizons") }
        }
        exclusiveContent {
            forRepositories(
                maven("https://maven.wagyourtail.xyz/releases"),
                maven("https://maven.wagyourtail.xyz/snapshots")
            )
            filter { includeGroupAndSubgroups("xyz.wagyourtail") }
        }
    }
    plugins {
        id("com.gtnewhorizons.retrofuturagradle") version "2.0.4"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

require(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25)) {
    "RetroFuturaGradle 2.0.4 requires JDK 25 or newer to run Gradle. " +
        "Also provide JDK 21 for mod compilation and JDK 8 for Minecraft preparation."
}

rootProject.name = "celtium-1122"
include("common", "forge122", "celtium-probe")
project(":common").projectDir = file("../common")
project(":forge122").projectDir = file("../forge122")
project(":celtium-probe").projectDir = file("../celtium-probe")
