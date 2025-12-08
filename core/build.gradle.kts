import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.reflect)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    api(projects.gregchessRegistry)
    implementation(projects.gregchessCoreUtils)
    testImplementation(libs.slf4j.jdk14)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
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
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
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
        create<MavenPublication>("core") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["kotlin"])
            artifact(tasks.sourcesJar)
        }
    }
}