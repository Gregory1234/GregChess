import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.reflect)
    api(libs.kotlinx.serialization.core)
    api(libs.slf4j.api)
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
            addLinks(project)
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
        create<MavenPublication>("registry") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["kotlin"])
            artifact(tasks.sourcesJar)
        }
    }
}