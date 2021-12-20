
plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("fabric-loom") apply false
}

allprojects {
    group = "gregc.gregchess"
    version = "1.1"
}

tasks {
    project(":gregchess-core").getTasksByName("test", true).forEach {
        it.outputs.upToDateWhen { false }
    }

    create<DefaultTask>("createSpigotJar") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-bukkit:jar")
        doLast {
            copy {
                from(project(":gregchess-bukkit").getTasksByName("jar", true))
                into(rootDir)
                rename {
                    "${rootProject.name}-$version-bukkit.jar"
                }
            }
        }
    }
    create<DefaultTask>("createFabricJar") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-fabric:remapJar")
        doLast {
            copy {
                from(project(":gregchess-fabric").getTasksByName("remapJar", true))
                into(rootDir)
                rename {
                    "${rootProject.name}-$version-fabric.jar"
                }
            }
        }
    }
}