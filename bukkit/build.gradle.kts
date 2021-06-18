import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm")
}

val shaded: Configuration by configurations.creating
val unshaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)
shaded.extendsFrom(unshaded)

dependencies {
    val spigotVersion: String by project

    api("org.spigotmc:spigot-api:$spigotVersion")
    unshaded(kotlin("stdlib-jdk8"))
    shaded(project(":core"))
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
        archiveBaseName.set(rootProject.name)
        destinationDirectory.set(file(rootProject.rootDir))
        from ({ shaded.filter { it !in unshaded }.map { if(it.isDirectory) it else zipTree(it) } })
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
