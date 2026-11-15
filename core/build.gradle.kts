import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Exposed as `api` because :core's public API leaks serialization types
    // (VisualizationDocument is @Serializable and ComponentNode.params is a JsonObject),
    // so :app must be able to see them on its compile classpath.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
