
plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
}

allprojects {
    group = "gregc"
    version = "1.0"
    apply(plugin = "org.jetbrains.dokka")
}

tasks {
    create<DefaultTask>("createSpigotJar") {
        group = "gregchess"
        dependsOn(":bukkit:jar")
        doLast {
            copy {
                from(project(":bukkit").getTasksByName("jar", true))
                into(rootDir)
                rename {
                    "${rootProject.name}-$version.jar"
                }
            }
        }
    }
}