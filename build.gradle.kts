@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.minecraftserver) apply false
    alias(libs.plugins.fabric.loom) apply false
}

allprojects {
    group = "gregc.gregchess"
    version = "1.2"
}

repositories {
    mavenCentral()
}
