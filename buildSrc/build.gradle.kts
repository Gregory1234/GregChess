import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

val props = FileInputStream(file("../gradle.properties")).use { propFile ->
    Properties().apply { load(propFile) }
}

dependencies {
    val kotlinVersion: String by props
    implementation(kotlin("gradle-plugin", kotlinVersion))
    val dokkaVersion: String by props
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion") {
        exclude("org.jetbrains.kotlin","kotlin-stdlib-jdk8") // https://github.com/Kotlin/dokka/issues/2546
    }
}

tasks {
    compileJava {
        targetCompatibility = "1.8"
    }
}