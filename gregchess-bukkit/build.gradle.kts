plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
}

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project
    api("org.spigotmc:spigot-api:$spigotVersion")
    val kotlinxSerializationVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    implementation(kotlin("reflect"))
    val slf4jVersion: String by project
    implementation("org.slf4j:slf4j-jdk14:$slf4jVersion")
    api(project(":gregchess-core"))
    shaded(project(":gregchess-core"))
    shaded(project(":bukkit-utils"))
}

val trueSpigotVersion by lazyTrueSpigotVersion(project)

tasks {

    processResources {
        val kotlinVersion: String by project
        val kotlinxSerializationVersion: String by project
        val kotlinxCoroutinesVersion: String by project
        val slf4jVersion: String by project
        val spigotMinecraftVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.yml")
            replace(
                "version" to version,
                "kotlin-version" to kotlinVersion,
                "serialization-version" to kotlinxSerializationVersion,
                "coroutines-version" to kotlinxCoroutinesVersion,
                "slf4j-version" to slf4jVersion,
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
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
    create<Jar>("shadedJar") {
        group = "build"
        dependsOn(":gregchess-bukkit:jar")
        from({ getByPath(":gregchess-bukkit:jar").outputs.files.map { zipTree(it) } })
        from({ shaded.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep -> dep.moduleArtifacts.map { zipTree(it.file) }}})
        archiveClassifier.set("shaded")
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                val spigotVersion: String by project
                externalDocumentationLinkElementList("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/$spigotVersion/spigot-api-$trueSpigotVersion-javadoc.jar/!/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
    create<Jar>("sourcesJar") {
        group = "build"
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }
}

publishing {
    publications {
        create<MavenPublication>("bukkit") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["kotlin"])
            artifact(tasks.getByPath(":gregchess-bukkit:sourcesJar"))
        }
    }
}