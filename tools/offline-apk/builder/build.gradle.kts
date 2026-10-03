import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins { kotlin("jvm") version "2.0.21" apply false }

val repo = providers.gradleProperty("repo").get()
val androidJar = providers.gradleProperty("androidJar").get()
val genDir = providers.gradleProperty("genDir").get()

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            // dx cannot desugar invokedynamic, so lambdas become classes; only Java 8 library APIs
            // are visible so nothing JDK-only can reach the APK.
            freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class", "-Xstring-concat=inline", "-Xjdk-release=1.8")
        }
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(8) }
}

project(":core") {
    extensions.configure<KotlinJvmProjectExtension> {
        sourceSets.named("main") { kotlin.setSrcDirs(listOf("$repo/core/src/main/kotlin")) }
    }
}

project(":app") {
    extensions.configure<KotlinJvmProjectExtension> {
        sourceSets.named("main") { kotlin.setSrcDirs(listOf("$repo/app/src/main/java", genDir)) }
    }
    extensions.configure<SourceSetContainer> {
        named("main") { java.setSrcDirs(listOf(genDir)) }
    }
    dependencies {
        "implementation"(project(":core"))
        "compileOnly"(files(androidJar))
    }
    tasks.register<Copy>("runtimeJars") {
        from(configurations.getByName("runtimeClasspath"))
        into(layout.buildDirectory.dir("runtime"))
    }
}
