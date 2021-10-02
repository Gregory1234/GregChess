import org.apache.tools.ant.filters.ReplaceTokens
import java.net.URL

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
}

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project
    api("org.spigotmc:spigot-api:$spigotVersion")
    val kotlinxSerializationVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    val kotlinxCoroutinesVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
    implementation(kotlin("reflect"))
    shaded(project(":core"))
}

fun CopySpec.replace(vararg args: Pair<String, Any>) = filter<ReplaceTokens>("tokens" to mapOf(*args))

tasks {

    processResources {
        val kotlinVersion: String by project
        val kotlinxSerializationVersion: String by project
        val kotlinxCoroutinesVersion: String by project
        val spigotMinecraftVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.yml")
            replace(
                "version" to version,
                "kotlin-version" to kotlinVersion,
                "serialization-version" to kotlinxSerializationVersion,
                "coroutines-version" to kotlinxCoroutinesVersion,
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
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
                "-Xlambdas=indy",
                "-progressive")
        }
    }
    jar {
        from ({ shaded.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep -> dep.moduleArtifacts.map { zipTree(it.file) }}})
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
