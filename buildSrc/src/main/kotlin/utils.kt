
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
    "-progressive",
    "-Xconsistent-data-class-copy-visibility",
    "-opt-in=kotlin.time.ExperimentalTime"
)

val TaskContainer.sourcesJar: TaskProvider<Jar>
    get() = named<Jar>("sourcesJar")

val TaskContainer.shadedJar: TaskProvider<Jar>
    get() = named<Jar>("shadedJar")