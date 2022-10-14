@file:Suppress("NOTHING_TO_INLINE")

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.GradleDokkaSourceSetBuilder
import java.net.URL

inline fun GradleDokkaSourceSetBuilder.gregchessSourceLink(project: Project) = sourceLink {
    val relPath = project.rootProject.projectDir.toPath().relativize(project.projectDir.toPath())
    localDirectory.set(project.projectDir.resolve("src"))
    remoteUrl.set(URL("https://github.com/Gregory1234/GregChess/tree/master/$relPath/src"))
    remoteLineSuffix.set("#L")
}

inline fun GradleDokkaSourceSetBuilder.externalDocumentationLinkElementList(url: String) = externalDocumentationLink {
    this.url.set(URL(url))
    this.packageListUrl.set(URL(url + "element-list"))
}
