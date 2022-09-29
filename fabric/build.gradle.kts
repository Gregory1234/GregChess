import net.fabricmc.loom.task.RemapJarTask

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.dokka)
    `maven-publish`
}

loom {
    runConfigs.forEach {
        it.runDir = it.runDir
        it.vmArgs += listOf("-Dkotlinx.coroutines.debug=on", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
    }
    runtimeOnlyLog4j.set(true)
}

repositories {
    maven ("https://server.bbkr.space/artifactory/libs-release") { name = "LibGui" }
}

val shaded: Configuration by configurations.creating

configurations.implementation.get().extendsFrom(shaded)

val fabricModules = setOf(
    "fabric-item-groups-v0",
    "fabric-lifecycle-events-v1",
    "fabric-item-api-v1",
    "fabric-transitive-access-wideners-v1",
    "fabric-registry-sync-v0",
)

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "net.fabricmc.fabric-api") {
            val newVersion = fabricApi
                .module(requested.name, libs.versions.fabric.api.get())
                .version!!
            useVersion(newVersion)
        }
    }
}

dependencies {
    minecraft(libs.fabric.minecraft)
    mappings(variantOf(libs.fabric.yarn) { classifier("v2") })
    modApi(libs.fabric.loader)
    fabricModules.forEach {
        modImplementation("net.fabricmc.fabric-api", it)
    }
    modImplementation(libs.fabric.kotlin)
    modApi(libs.fabric.libgui)
    include(libs.fabric.libgui)
    api(projects.gregchessCore)
    shaded(projects.gregchessCore)
    shaded(projects.gregchessRegistry)
    shaded(projects.gregchessCoreUtils)
    shaded(projects.gregchessFabricUtils.copy().apply { targetConfiguration = "namedElements" })
}

tasks {
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
    processResources {
        val jvmVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.json")
            replace(
                "version" to version,
                "loader-version" to libs.versions.fabric.loader.get(),
                "fabric-api-modules" to fabricModules.joinToString(separator = "\": \"*\", \""),
                "fabric-kotlin-version" to libs.versions.fabric.kotlin.get(),
                "minecraft-version" to libs.versions.fabric.minecraft.get().dropLastWhile { it != '.' } + "x",
                "java-min-version" to jvmVersion
            )
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    jar {
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
    remapJar {
        addNestedDependencies.set(false)
    }
    val shadedJar by registering(Jar::class) {
        group = "build"
        dependsOn(jar)
        from({ jar.get().outputs.files.map { zipTree(it) } })
        from({ shaded.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep -> dep.moduleArtifacts.map { zipTree(it.file) }}})
        archiveClassifier.set("shaded-dev")
        destinationDirectory.set(jar.get().destinationDirectory)
    }
    val remapShadedJar by registering(RemapJarTask::class) {
        dependsOn(shadedJar)
        group = "fabric"
        inputFile.set(shadedJar.get().archiveFile)
        archiveClassifier.set("shaded")
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                externalDocumentationLinkElementList("https://cottonmc.github.io/docs/libgui/")
                externalDocumentationLinkElementList("https://maven.fabricmc.net/docs/yarn-${libs.versions.fabric.yarn.get()}/")
                externalDocumentationLinkElementList("https://maven.fabricmc.net/docs/fabric-loader-${libs.versions.fabric.loader.get()}/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
    register<Copy>("finalJar") {
        group = "gregchess"
        from(remapShadedJar)
        into(File(rootProject.buildDir, "libs"))
        rename { "${rootProject.name}-$version-fabric.jar" }
    }
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("fabric") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["java"])
        }
    }
}