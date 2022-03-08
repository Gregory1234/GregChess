
plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("fabric-loom") apply false
}

allprojects {
    group = "gregc.gregchess"
    version = "1.2"
}

repositories {
    mavenCentral()
}

val TaskContainer.test: TaskProvider<Test>
    get() = named<Test>("test")

val TaskContainer.compileKotlin: TaskProvider<Task>
    get() = named<Task>("compileKotlin")

tasks {
    val coreTasks = project.projects.gregchessCore.dependencyProject.tasks
    val bukkitTasks = project.projects.gregchessBukkit.dependencyProject.tasks
    val fabricTasks = project.projects.gregchessFabric.dependencyProject.tasks
    val bukkitUtilsTasks = project.projects.gregchessBukkitUtils.dependencyProject.tasks

    register<Copy>("createSpigotJar") {
        group = "gregchess"
        coreTasks.test {
            outputs.upToDateWhen { false }
        }
        dependsOn(coreTasks.test)
        bukkitTasks.compileKotlin.get().mustRunAfter(coreTasks.test)
        bukkitUtilsTasks.compileKotlin.get().mustRunAfter(coreTasks.test)
        from(bukkitTasks.shadedJar)
        into(rootDir)
        rename { "${rootProject.name}-$version-bukkit.jar" }
    }
    register<Copy>("createFabricJar") {
        group = "gregchess"
        coreTasks.test {
            outputs.upToDateWhen { false }
        }
        dependsOn(coreTasks.test)
        fabricTasks.compileKotlin.get().mustRunAfter(coreTasks.test)
        from(fabricTasks.named("remapShadedJar"))
        into(rootDir)
        rename { "${rootProject.name}-$version-fabric.jar" }
    }
    register<DefaultTask>("runFabricClient") {
        group = "gregchess"
        coreTasks.test {
            outputs.upToDateWhen { false }
        }
        dependsOn(coreTasks.test)
        dependsOn(fabricTasks["runClient"])
        fabricTasks.compileKotlin.get().mustRunAfter(coreTasks.test)
    }
    register<DefaultTask>("runPaperServer") {
        group = "gregchess"
        coreTasks.test {
            outputs.upToDateWhen { false }
        }
        dependsOn(coreTasks.test)
        dependsOn(bukkitTasks["launchMinecraftServer"])
        bukkitTasks.compileKotlin.get().mustRunAfter(coreTasks.test)
        bukkitUtilsTasks.compileKotlin.get().mustRunAfter(coreTasks.test)
    }
}