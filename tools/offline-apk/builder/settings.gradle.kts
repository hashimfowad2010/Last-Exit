// Compiles :core and :app as separate modules (so `internal` stays module-private) without the
// Android Gradle Plugin. Only Maven Central and the Gradle Plugin Portal are needed.
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "last-exit-offline-apk"
include(":core", ":app")
