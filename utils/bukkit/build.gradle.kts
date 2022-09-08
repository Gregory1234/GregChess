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

dependencies {
    api(libs.spigot.api)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
}

val trueSpigotVersion by lazyTrueSpigotVersion(libs.versions.spigot.api.get())

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
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                externalDocumentationLinkElementList("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/${libs.versions.spigot.api.get()}/spigot-api-$trueSpigotVersion-javadoc.jar/!/")
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
        create<MavenPublication>("bukkitUtils") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["kotlin"])
            artifact(tasks.sourcesJar)
        }
    }
}