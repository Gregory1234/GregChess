import org.apache.tools.ant.filters.ReplaceTokens
import java.net.URL

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
}

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project
    api("org.spigotmc:spigot-api:$spigotVersion")
    val kotlinxSerializationVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    val kotlinxCoroutinesVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
    implementation(kotlin("reflect"))
    shaded(project(":gregchess-core"))
    shaded(project(":bukkit-utils"))
}

// TODO: make this simpler
val trueSpigotVersion by lazy {
    val spigotVersion: String by project
    val snapshot = groovy.xml.XmlParser().parse("https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$spigotVersion/maven-metadata.xml")
        .children().filterIsInstance<groovy.util.Node>().first { it.name() == "versioning" }
        .children().filterIsInstance<groovy.util.Node>().first { it.name() == "snapshot" }.children().filterIsInstance<groovy.util.Node>()
    println("Calculating spigot version")
    spigotVersion.replace("SNAPSHOT", snapshot.first {it.name() == "timestamp"}.text() + "-" + snapshot.first {it.name() == "buildNumber"}.text())
}

fun CopySpec.replace(vararg args: Pair<String, Any>) = filter<ReplaceTokens>("tokens" to mapOf(*args))

tasks {

    processResources {
        val kotlinVersion: String by project
        val kotlinxSerializationVersion: String by project
        val kotlinxCoroutinesVersion: String by project
        val spigotMinecraftVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.yml")
            replace(
                "version" to version,
                "kotlin-version" to kotlinVersion,
                "serialization-version" to kotlinxSerializationVersion,
                "coroutines-version" to kotlinxCoroutinesVersion,
                "minecraft-version" to spigotMinecraftVersion
            )
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    compileKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
                "-Xlambdas=indy",
                "-progressive")
        }
    }
    jar {
        from ({ shaded.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep -> dep.moduleArtifacts.map { zipTree(it.file) }}})
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                // TODO: remove repetition
                sourceLink {
                    val relPath = rootProject.projectDir.toPath().relativize(projectDir.toPath())
                    localDirectory.set(projectDir.resolve("src"))
                    remoteUrl.set(URL("https://github.com/Gregory1234/GregChess/tree/master/$relPath/src"))
                    remoteLineSuffix.set("#L")
                }
                externalDocumentationLink {
                    val spigotVersion: String by project
                    url.set(URL("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/$spigotVersion/spigot-api-$trueSpigotVersion-javadoc.jar/!"))
                    packageListUrl.set(URL("https://hub.spigotmc.org/nexus/service/local/repositories/snapshots/archive/org/spigotmc/spigot-api/$spigotVersion/spigot-api-$trueSpigotVersion-javadoc.jar/!/element-list"))
                }
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-core/kotlinx-serialization-core/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
}
