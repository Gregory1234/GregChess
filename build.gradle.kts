
plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("fabric-loom") apply false
}

allprojects {
    group = "gregc"
    version = "1.1"
}

tasks {
    project(":core").getTasksByName("test", true).forEach {
        it.outputs.upToDateWhen { false }
    }

    create<DefaultTask>("createSpigotJar") {
        group = "gregchess"
        dependsOn(":core:test")
        dependsOn(":bukkit:jar")
        doLast {
            copy {
                from(project(":bukkit").getTasksByName("jar", true))
                into(rootDir)
                rename {
                    "${rootProject.name}-$version-bukkit.jar"
                }
            }
        }
    }
    create<DefaultTask>("createFabricJar") {
        group = "gregchess"
        dependsOn(":core:test")
        dependsOn(":fabric:remapJar")
        doLast {
            copy {
                from(project(":fabric").getTasksByName("remapJar", true))
                into(rootDir)
                rename {
                    "${rootProject.name}-$version-fabric.jar"
                }
            }
        }
    }
}