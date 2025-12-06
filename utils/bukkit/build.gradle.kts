import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
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

kotlin {
    compilerOptions {
        val jvmVersion: String by project
        jvmTarget = JvmTarget.fromTarget(jvmVersion)
        freeCompilerArgs = defaultKotlinArgs
    }
}

dokka {
    dokkaSourceSets {
        configureEach {
            gregchessSourceLink(project)
            externalDocumentationLinkElementList("spigot-api", "https://hub.spigotmc.org/javadocs/spigot/")
            externalDocumentationLink("kotlinx.coroutines", "https://kotlin.github.io/kotlinx.coroutines/")
            externalDocumentationLink("kotlinx.serialization", "https://kotlin.github.io/kotlinx.serialization/")
        }
    }
}

tasks {

    compileJava {
        val jvmVersion: String by project
        sourceCompatibility = jvmVersion
        targetCompatibility = jvmVersion
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