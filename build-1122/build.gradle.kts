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
version = "2.5.12-celtium.0.1.0-dev.1"
extra["celtium_repository_root"] = rootProject.file("..").canonicalFile
