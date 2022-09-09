
plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("fabric-loom") apply false
}

allprojects {
    group = "gregc.gregchess"
    version = "1.2"
}

repositories {
    mavenCentral()
}
