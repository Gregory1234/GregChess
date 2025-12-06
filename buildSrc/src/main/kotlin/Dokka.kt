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
