
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

    create<Copy>("createSpigotJar") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-bukkit:shadedJar")
        getByPath(":gregchess-bukkit:compileKotlin").mustRunAfter(":gregchess-core:test")
        getByPath(":bukkit-utils:compileKotlin").mustRunAfter(":gregchess-core:test")
        from(getByPath(":gregchess-bukkit:shadedJar"))
        into(rootDir)
        rename { "${rootProject.name}-$version-bukkit.jar" }
    }
    create<Copy>("createFabricJar") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-fabric:remapJar")
        getByPath(":gregchess-fabric:compileKotlin").mustRunAfter(":gregchess-core:test")
        from(getByPath(":gregchess-fabric:remapJar"))
        into(rootDir)
        rename { "${rootProject.name}-$version-fabric.jar" }
    }
    create<DefaultTask>("runFabricClient") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-fabric:runClient")
        getByPath(":gregchess-fabric:compileKotlin").mustRunAfter(":gregchess-core:test")
    }
    // TODO: add a way of running a spigot server with the plugin from a gradle task
}