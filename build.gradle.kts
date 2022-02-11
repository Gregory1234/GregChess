
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

tasks {
    project(":gregchess-core").getTasksByName("test", true).forEach {
        it.outputs.upToDateWhen { false }
    }

    register<Copy>("createSpigotJar") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-bukkit:shadedJar")
        getByPath(":gregchess-bukkit:compileKotlin").mustRunAfter(":gregchess-core:test")
        getByPath(":bukkit-utils:compileKotlin").mustRunAfter(":gregchess-core:test")
        from(getByPath(":gregchess-bukkit:shadedJar"))
        into(rootDir)
        rename { "${rootProject.name}-$version-bukkit.jar" }
    }
    register<Copy>("createFabricJar") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-fabric:remapJar")
        getByPath(":gregchess-fabric:compileKotlin").mustRunAfter(":gregchess-core:test")
        from(getByPath(":gregchess-fabric:remapJar"))
        into(rootDir)
        rename { "${rootProject.name}-$version-fabric.jar" }
    }
    register<DefaultTask>("runFabricClient") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-fabric:runClient")
        getByPath(":gregchess-fabric:compileKotlin").mustRunAfter(":gregchess-core:test")
    }
    register<DefaultTask>("runPaperServer") {
        group = "gregchess"
        dependsOn(":gregchess-core:test")
        dependsOn(":gregchess-bukkit:launchMinecraftServer")
        getByPath(":gregchess-bukkit:compileKotlin").mustRunAfter(":gregchess-core:test")
    }
}