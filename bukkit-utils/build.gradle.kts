plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

dependencies {
    val spigotVersion: String by project
    api("org.spigotmc:spigot-api:$spigotVersion")
    val kotlinxCoroutinesVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
    implementation(kotlin("reflect"))
}

val trueSpigotVersion by lazyTrueSpigotVersion(project)

tasks {

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
                val spigotVersion: String by project
                externalDocumentationLinkElementList("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/$spigotVersion/spigot-api-$trueSpigotVersion-javadoc.jar/!/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
}
