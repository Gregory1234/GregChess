import java.net.URL

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

// TODO: make this simpler
val trueSpigotVersion by lazy {
    val spigotVersion: String by project
    val snapshot = groovy.xml.XmlParser().parse("https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$spigotVersion/maven-metadata.xml")
        .children().filterIsInstance<groovy.util.Node>().first { it.name() == "versioning" }
        .children().filterIsInstance<groovy.util.Node>().first { it.name() == "snapshot" }.children().filterIsInstance<groovy.util.Node>()
    println("Calculating spigot version")
    spigotVersion.replace("SNAPSHOT", snapshot.first {it.name() == "timestamp"}.text() + "-" + snapshot.first {it.name() == "buildNumber"}.text())
}

tasks {

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
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
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
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
}
