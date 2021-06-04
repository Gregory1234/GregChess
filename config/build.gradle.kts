plugins {
    kotlin("jvm")
}

group = "gregc"
version = "1.0"

dependencies {
    implementation("com.squareup:kotlinpoet:1.8.0")
    implementation(project(":core"))
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = listOf("-Xopt-in=kotlin.ExperimentalStdlibApi")
        }
    }
}