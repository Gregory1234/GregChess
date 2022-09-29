@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.dokka)
    `maven-publish`
}

val shaded: Configuration by configurations.creating

configurations.implementation.get().extendsFrom(shaded)

dependencies {
    minecraft(libs.fabric.minecraft)
    mappings(variantOf(libs.fabric.yarn) { classifier("v2") })
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
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
    jar {
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                externalDocumentationLinkElementList("https://maven.fabricmc.net/docs/yarn-${libs.versions.fabric.yarn.get()}/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/")
            }
        }
    }
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("fabricUtils") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["java"])
        }
    }
}