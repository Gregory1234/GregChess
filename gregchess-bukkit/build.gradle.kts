import dev.s7a.gradle.minecraft.server.tasks.LaunchMinecraftServerTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("dev.s7a.gradle.minecraft.server")
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
    create<LaunchMinecraftServerTask>("runServer") {
        dependsOn(":gregchess-bukkit:shadedJar")
        doFirst {
            copy {
                from(getByPath(":gregchess-bukkit:shadedJar"))
                into(projectDir.resolve("run/plugins"))
            }
        }
        val paperServerVersion: String by project
        jarUrl.set(LaunchMinecraftServerTask.JarUrl.Paper(paperServerVersion))
        serverDirectory.set(projectDir.resolve("run"))
        jvmArgument.set(listOf(
            "-Xms1G", "-Xmx1G", "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
            "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
            "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
            "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
            "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:SurvivorRatio=32",
            "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
            "-Dusing.aikars.flags=https://mcflags.emc.gs", "-Daikars.new.flags=true", "-Dkotlinx.coroutines.debug=on",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"))
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