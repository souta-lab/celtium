plugins {
    `java-library`
    id("java-gradle-plugin") // so we can assign and ID to our plugin
}

dependencies {
    implementation("net.fabricmc:tiny-remapper:0.11.0")
    implementation("net.fabricmc:mapping-io:0.7.1")
    implementation("net.fabricmc:mapping-io-extras:0.7.1")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net/") }
        filter {
            includeGroup("net.fabricmc")
        }
    }
}

gradlePlugin {
    plugins {
        register("embeddium-mdg-remapper") {
            id = "embeddium-mdg-remapper"
            implementationClass = "org.embeddedt.embeddium.gradle.mdg.remapper.MDGRemapperPlugin"
        }
    }
}