
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm")
    idea
}

group = "gregc"
version = "1.0"

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project

    api("org.spigotmc:spigot-api:$spigotVersion")
    api(kotlin("stdlib-jdk8"))
    shaded(project(":core"))
    shaded.exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}

tasks {

    processResources {
        val kotlinVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.yml")
            filter<ReplaceTokens>("tokens" to mapOf("version" to version, "kotlin-version" to kotlinVersion))
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts")
        }
    }
    jar {
        archiveBaseName.set(project.name)
        destinationDirectory.set(file(rootDir))
        from ({ shaded.map { if(it.isDirectory) it else zipTree(it) } })
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
