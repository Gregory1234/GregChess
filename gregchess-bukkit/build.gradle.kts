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
    shaded(project(":gregchess-core"))
    shaded(project(":bukkit-utils"))
}

val trueSpigotVersion by lazyTrueSpigotVersion(project)

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
            freeCompilerArgs = defaultKotlinArgs
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
                gregchessSourceLink(project)
                val spigotVersion: String by project
                externalDocumentationLinkElementList("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/$spigotVersion/spigot-api-$trueSpigotVersion-javadoc.jar/!/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-core/kotlinx-serialization-core/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
}
