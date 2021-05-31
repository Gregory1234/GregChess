plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "gregc"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup:kotlinpoet:1.8.0")
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.ExperimentalStdlibApi"
        }
    }
}