plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("io.mockk:mockk:1.11.0")
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
            jvmTarget = "11"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts")
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = listOf("-Xopt-in=kotlin.ExperimentalStdlibApi")
        }
    }
}