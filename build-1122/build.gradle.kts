import java.util.Properties

plugins { base }

// The shared sources stay in their upstream directories. This build needs neither
// the modern-version preprocessor nor upstream's publication/versioning plugins.
val inheritedProperties = Properties().apply {
    rootProject.file("../gradle.properties").inputStream().use { load(it) }
}
allprojects {
    inheritedProperties.forEach { key, value ->
        if (!hasProperty(key.toString())) extensions.extraProperties[key.toString()] = value
    }
}
// Explicit release builds keep the development version unchanged for running validation sessions.
// The development version is Celtium's own now. It used to be "2.5.12-celtium.0.1.0-dev.1", which carried
// Celeritas's upstream version as its prefix and left this fork looking like a patch of someone else's release -
// that prefix is part of the rebrand, so it is gone. The scheme is now the project's own: the development
// version tracks the release it is working towards, and an explicit release build still passes
// -Pceltium_release_version, exactly as 0.0.1-alpha did.
version = providers.gradleProperty("celtium_release_version")
    .orElse("0.0.2-alpha-dev").get()
extra["celtium_repository_root"] = rootProject.file("..").canonicalFile

// Evidence ties a validation run to an exact artifact. Without this, every rebuild embeds a new timestamp and
// the same sources produce a different hash, which makes "this jar was tested" impossible to check.
allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
