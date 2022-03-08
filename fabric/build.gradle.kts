import net.fabricmc.loom.task.RemapJarTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("fabric-loom")
    id("org.jetbrains.dokka")
    `maven-publish`
}

loom {
    runConfigs.forEach {
        it.runDir = "fabric/" + it.runDir
        it.vmArgs += listOf("-Dkotlinx.coroutines.debug=on", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
    }
    runtimeOnlyLog4j.set(true)
}

repositories {
    maven ("https://server.bbkr.space/artifactory/libs-release") { name = "LibGui" }
}

val shaded: Configuration by configurations.creating

configurations.implementation.get().extendsFrom(shaded)

dependencies {
    minecraft(libs.fabric.minecraft)
    mappings(variantOf(libs.fabric.yarn) { classifier("v2") })
    modApi(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.kotlin)
    modApi(libs.fabric.libgui)
    include(libs.fabric.libgui)
    api(projects.gregchessCore)
    shaded(projects.gregchessCore)
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
    register<RemapJarTask>("remapShadedJar") {
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