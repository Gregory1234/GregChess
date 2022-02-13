import net.fabricmc.loom.task.RemapJarTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("fabric-loom")
    id("org.jetbrains.dokka")
    `maven-publish`
}

loom.runConfigs.forEach {
    it.runDir = "gregchess-fabric/" + it.runDir
    it.vmArgs += listOf("-Dkotlinx.coroutines.debug=on", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
}

repositories {
    maven ("https://server.bbkr.space/artifactory/libs-release") { name = "LibGui" }
}

dependencies {
    minecraft(libs.fabric.minecraft)
    mappings(variantOf(libs.fabric.yarn) { classifier("v2") })
    modApi(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.kotlin)
    modApi(libs.fabric.libgui)
    include(libs.fabric.libgui)
    api(projects.gregchessCore)
    include(projects.gregchessCore)
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
    register<RemapJarTask>("unshadedRemapJar") {
        dependsOn(jar)
        group = "fabric"
        input.set(remapJar.get().input)
        archiveClassifier.set("remapped")
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
    register<Jar>("sourcesJar") {
        group = "build"
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }
}

publishing {
    publications {
        create<MavenPublication>("fabric") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["kotlin"])
            artifact(tasks.sourcesJar)
            artifact(tasks["unshadedRemapJar"])
        }
    }
}