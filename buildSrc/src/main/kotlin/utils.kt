import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.kotlin.dsl.filter
import org.gradle.kotlin.dsl.provideDelegate

fun CopySpec.replace(vararg args: Pair<String, Any>) = filter<ReplaceTokens>("tokens" to mapOf(*args))

val defaultKotlinArgs = listOf(
    "-Xopt-in=kotlin.RequiresOptIn",
    "-Xjvm-default=all",
    "-Xlambdas=indy",
    "-progressive"
)

// TODO: make this simpler
fun lazyTrueSpigotVersion(project: Project) = lazy {
    val spigotVersion: String by project
    val snapshot = groovy.xml.XmlParser().parse("https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$spigotVersion/maven-metadata.xml")
        .children().filterIsInstance<groovy.util.Node>().first { it.name() == "versioning" }
        .children().filterIsInstance<groovy.util.Node>().first { it.name() == "snapshot" }.children().filterIsInstance<groovy.util.Node>()
    println("Calculating spigot version")
    spigotVersion.replace("SNAPSHOT", snapshot.first {it.name() == "timestamp"}.text() + "-" + snapshot.first {it.name() == "buildNumber"}.text())
}