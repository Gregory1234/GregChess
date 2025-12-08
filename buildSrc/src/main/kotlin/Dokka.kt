@file:Suppress("NOTHING_TO_INLINE")

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.engine.parameters.DokkaSourceSetSpec

inline fun DokkaSourceSetSpec.gregchessSourceLink(project: Project) = sourceLink {
    val relPath = project.rootProject.projectDir.toPath().relativize(project.projectDir.toPath())
    localDirectory.set(project.projectDir.resolve("src"))
    remoteUrl("https://github.com/Gregory1234/GregChess/tree/master/$relPath/src")
    remoteLineSuffix.set("#L")
}

inline fun DokkaSourceSetSpec.externalDocumentationLink(name: String, url: String) = externalDocumentationLinks.register(name) {
    url(url)
}

inline fun DokkaSourceSetSpec.externalDocumentationLinkElementList(name: String, url: String) = externalDocumentationLinks.register(name) {
    url(url)
    packageListUrl(url + "element-list")
}


inline fun DokkaSourceSetSpec.addLinks(project: Project) {
    gregchessSourceLink(project)
    externalDocumentationLinkElementList("spigot-api", "https://hub.spigotmc.org/javadocs/spigot/")
    externalDocumentationLink("kotlinx.coroutines", "https://kotlinlang.org/api/kotlinx.coroutines/")
    externalDocumentationLink("kotlinx.serialization", "https://kotlinlang.org/api/kotlinx.serialization/")
    externalDocumentationLinks.register("kotlinx-datetime") { // https://youtrack.jetbrains.com/issue/KT-63926
        url("https://kotlinlang.org/api/kotlinx-datetime/")
        packageListUrl("https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/package-list")
    }
    externalDocumentationLinkElementList("slf4j", "https://www.slf4j.org/apidocs/")
}