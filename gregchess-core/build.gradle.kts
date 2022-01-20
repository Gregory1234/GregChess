plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    val kotlinxSerializationVersion: String by project
    api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion")
    val kotlinxCoroutinesVersion: String by project
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
    val slf4jVersion: String by project
    api("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.slf4j:slf4j-jdk14:$slf4jVersion")
    testImplementation(kotlin("test"))
    val junitVersion: String by project
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    val assertKVersion: String by project
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertKVersion")
    val mockkVersion: String by project
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    compileKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = defaultKotlinArgs
        }
    }
    compileTestKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = listOf(
                "-Xjvm-default=all",
                "-progressive")
        }
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
                externalDocumentationLink("https://www.slf4j.org/apidocs/")
            }
        }
    }
    create<Jar>("sourcesJar") {
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
            artifact(tasks.getByPath(":gregchess-core:sourcesJar"))
        }
    }
}