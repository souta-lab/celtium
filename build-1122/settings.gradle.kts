pluginManagement {
    includeBuild("../plugins/celeritas-mdg-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
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

rootProject.name = "celtium-1122"
include("common", "forge122", "celtium-probe")
project(":common").projectDir = file("../common")
project(":forge122").projectDir = file("../forge122")
project(":celtium-probe").projectDir = file("../celtium-probe")
