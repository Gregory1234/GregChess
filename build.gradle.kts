
import org.apache.tools.ant.filters.ReplaceTokens
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "gregc"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        name = "spigotmc-repo"
        url = URI("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "sonatype"
        url = URI("https://oss.sonatype.org/content/groups/public/")
    }
}

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project

    "api"("org.spigotmc:spigot-api:$spigotVersion")
    "shaded"(kotlin("stdlib-jdk8"))
}

tasks {
    processResources {
        from(sourceSets["main"].resources.srcDirs) {
            filter<ReplaceTokens>("tokens" to mapOf("version" to version))
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.contracts.ExperimentalContracts"
        }
    }
    jar {
        archiveBaseName.set(project.name)
        destinationDirectory.set(file(rootDir))
        from ({ shaded.map { if(it.isDirectory) it else zipTree(it) } })
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
