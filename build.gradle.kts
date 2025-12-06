plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.minecraftserver) apply false
}

allprojects {
    group = "gregc.gregchess"
    version = "1.2"
}

dependencies {
    dokka(projects.gregchessRegistry)
    dokka(projects.gregchessCoreUtils)
    dokka(projects.gregchessCore)
    dokka(projects.gregchessBukkitUtils)
    dokka(projects.gregchessBukkit)
}

repositories {
    mavenCentral()
}
