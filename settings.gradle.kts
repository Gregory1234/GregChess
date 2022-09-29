rootProject.name = "GregChess"
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        gradlePluginPortal()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":gregchess-core")
project(":gregchess-core").projectDir = rootDir.resolve("core")
include(":gregchess-bukkit")
project(":gregchess-bukkit").projectDir = rootDir.resolve("bukkit")
include(":gregchess-fabric")
project(":gregchess-fabric").projectDir = rootDir.resolve("fabric")
include(":gregchess-bukkit-utils")
project(":gregchess-bukkit-utils").projectDir = rootDir.resolve("utils/bukkit")
include(":gregchess-registry")
project(":gregchess-registry").projectDir = rootDir.resolve("utils/registry")
include(":gregchess-core-utils")
project(":gregchess-core-utils").projectDir = rootDir.resolve("utils/core")
include(":gregchess-fabric-utils")
project(":gregchess-fabric-utils").projectDir = rootDir.resolve("utils/fabric")