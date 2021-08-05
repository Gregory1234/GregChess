import org.apache.tools.ant.filters.ReplaceTokens
import java.net.URL

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

val shaded: Configuration by configurations.creating
val unshaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)
shaded.extendsFrom(unshaded)

dependencies {
    val spigotVersion: String by project
    api("org.spigotmc:spigot-api:$spigotVersion")

    unshaded(kotlin("stdlib-jdk8"))
    unshaded(kotlin("reflect"))
    val kotlinxSerializationVersion: String by project
    unshaded("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    shaded(project(":core"))
}

fun CopySpec.replace(vararg args: Pair<String, Any>) = filter<ReplaceTokens>("tokens" to mapOf(*args))

tasks {

    processResources {
        val kotlinVersion: String by project
        val kotlinxSerializationVersion: String by project
        val spigotMinecraftVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.yml")
            replace(
                "version" to version,
                "kotlin-version" to kotlinVersion,
                "serialization-version" to kotlinxSerializationVersion,
                "minecraft-version" to spigotMinecraftVersion
            )
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
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
    jar {
        from ({ shaded.filter { it !in unshaded }.map { if(it.isDirectory) it else zipTree(it) } })
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                externalDocumentationLink {
                    url.set(URL("https://hub.spigotmc.org/javadocs/spigot/"))
                    packageListUrl.set(URL("https://hub.spigotmc.org/javadocs/spigot/element-list"))
                }
            }
        }
    }
}
