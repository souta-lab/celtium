plugins {
    `java-library`
    id("java-gradle-plugin") // so we can assign and ID to our plugin
}

dependencies {
    implementation("net.fabricmc:tiny-remapper:0.11.0")
    // ASM core at 9.10.1: tiny-remapper's own 9.7.1 cannot read Java 25 class files (the multi-release
    // module-info in the LWJGL 3.4.1 jar on the remap classpath). The retired buildSrc used to carry this
    // version on the build classpath and silently upgraded the plugin's copy; now the plugin carries it.
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("net.fabricmc:mapping-io:0.7.1")
    implementation("net.fabricmc:mapping-io-extras:0.7.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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
        register("celtium-remapper") {
            id = "celtium-remapper"
            implementationClass = "org.soutalab.celtium.gradle.remapper.CeltiumRemapperPlugin"
        }
    }
}