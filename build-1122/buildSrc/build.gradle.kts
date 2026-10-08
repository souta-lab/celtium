plugins {
    `kotlin-dsl`
    `java-library`
}

repositories { mavenCentral() }
dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
}

// Compile the two upstream helpers directly, without copying or compiling the
// unrelated Stonecutter and modern-platform conventions in the parent buildSrc.
sourceSets.main {
    java.setSrcDirs(listOf("../../buildSrc/src/main/java"))
    java.include("org/embeddedt/embeddium/gradle/build/conventions/ShadowHelper.java")
}
kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf("../../buildSrc/src/main/kotlin"))
    kotlin.include("org/embeddedt/embeddium/gradle/task/GenerateLWJGLAbstraction.kt")
}
