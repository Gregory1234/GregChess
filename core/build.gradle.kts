plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
}

dependencies {
    implementation(kotlin("reflect"))
    val kotlinxSerializationVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    testImplementation(kotlin("test"))
    val kotestVersion: String by project
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
    val mockkVersion: String by project
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    compileKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts",
                "-Xjvm-default=all",
                "-progressive")
        }
    }
    compileTestKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=io.kotest.common.ExperimentalKotest",
                "-Xjvm-default=all",
                "-progressive")
        }
    }
}