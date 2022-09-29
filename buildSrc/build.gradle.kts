plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.plugin)
    compileOnly(libs.dokka.plugin) {
        exclude("org.jetbrains.kotlin","kotlin-stdlib-jdk8") // https://github.com/Kotlin/dokka/issues/2546
    }
}

tasks {
    compileJava {
        targetCompatibility = "1.8"
    }
}