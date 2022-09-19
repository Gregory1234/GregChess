import dev.s7a.gradle.minecraft.server.tasks.LaunchMinecraftServerTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("dev.s7a.gradle.minecraft.server")
    `maven-publish`
}

minecraftServerConfig {
    jarUrl.set(LaunchMinecraftServerTask.JarUrl.Paper(libs.versions.spigot.api.get().substringBefore("-")))
    serverDirectory.set(projectDir.resolve("run"))
    jvmArgument.set(listOf(
        "-Xms2G", "-Xmx2G", "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:SurvivorRatio=32",
        "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs", "-Daikars.new.flags=true", "-Dkotlinx.coroutines.debug=on",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"))
    jarName.set("server.jar")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
}

val shaded: Configuration by configurations.creating
val spigotLib: Configuration by configurations.creating

configurations.implementation.get().extendsFrom(shaded, spigotLib)

dependencies {
    api(libs.spigot.api)
    api(libs.kotlinx.serialization.json)
    spigotLib(kotlin("stdlib"))
    spigotLib(kotlin("reflect"))
    spigotLib(libs.kotlinx.serialization.json)
    spigotLib(libs.kotlinx.coroutines.core)
    spigotLib(libs.slf4j.jdk14)
    api(projects.gregchessCore)
    shaded(projects.gregchessCore)
    shaded(projects.gregchessBukkitUtils)
}

val trueSpigotVersion by lazyTrueSpigotVersion(libs.versions.spigot.api.get())

tasks {

    processResources {
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.yml")
            replace(
                "version" to version,
                "minecraft-version" to libs.versions.spigot.api.get().substringBefore("-").split(".").take(2).joinToString("."),
                "libraries" to spigotLib.resolvedConfiguration.firstLevelModuleDependencies.toList()
                    .joinToString("\n") { "  - ${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}" }
            )
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    compileJava {
        val jvmVersion: String by project
        sourceCompatibility = jvmVersion
        targetCompatibility = jvmVersion
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
    register<Jar>("shadedJar") {
        group = "build"
        dependsOn(jar)
        from({ jar.get().outputs.files.map { zipTree(it) } })
        from({ shaded.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep -> dep.moduleArtifacts.map { zipTree(it.file) }}})
        archiveClassifier.set("shaded")
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                externalDocumentationLinkElementList("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/${libs.versions.spigot.api.get()}/spigot-api-$trueSpigotVersion-javadoc.jar/!/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
    register<Jar>("sourcesJar") {
        group = "build"
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }
    launchMinecraftServer {
        dependsOn(shadedJar)
        group = "minecraft"
        serverArgument.add("-add-plugin=${shadedJar.get().archiveFile.get().asFile.absolutePath}")
        System.getProperty("config")?.let { config ->
            serverArgument.add("--config=$config.properties")
        }
    }
    register<Copy>("finalJar") {
        group = "gregchess"
        from(shadedJar)
        into(File(rootProject.buildDir, "libs"))
        rename { "${rootProject.name}-$version-bukkit.jar" }
    }
}

publishing {
    publications {
        create<MavenPublication>("bukkit") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["kotlin"])
            artifact(tasks.sourcesJar)
        }
    }
}