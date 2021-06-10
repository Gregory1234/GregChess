plugins {
    kotlin("jvm")
}

group = "gregc"
version = "1.0"

dependencies {
    val spigotVersion: String by project
    implementation("com.squareup:kotlinpoet:1.8.0")
    implementation(kotlin("reflect"))
    implementation("org.spigotmc:spigot-api:$spigotVersion")
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts")
        }
    }
}