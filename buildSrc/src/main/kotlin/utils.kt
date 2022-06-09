
import groovy.namespace.QName
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.filter
import org.gradle.kotlin.dsl.named

fun CopySpec.replace(vararg args: Pair<String, Any>) = filter<ReplaceTokens>("tokens" to mapOf(*args))

val defaultKotlinArgs = listOf(
    "-Xjvm-default=all",
    "-Xlambdas=indy",
    "-progressive"
)

fun lazyTrueSpigotVersion(spigotVersion: String) = lazy {
    val snapshot = groovy.xml.XmlParser()
        .parse("https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$spigotVersion/maven-metadata.xml")
        .getAt(QName.valueOf("versioning")).getAt("snapshot")
    println("Calculating spigot version")
    spigotVersion.replace("SNAPSHOT", snapshot.getAt("timestamp").text() + "-" + snapshot.getAt("buildNumber").text())
}

val TaskContainer.sourcesJar: TaskProvider<Jar>
    get() = named<Jar>("sourcesJar")

val TaskContainer.shadedJar: TaskProvider<Jar>
    get() = named<Jar>("shadedJar")