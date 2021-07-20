
plugins {
    kotlin("jvm") apply false
}

allprojects {
    group = "gregc"
    version = "1.0"
}

tasks {
    create<DefaultTask>("createSpigotJar") {
        group = "gregchess"
        dependsOn(":core:build")
        dependsOn(":bukkit:build")
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