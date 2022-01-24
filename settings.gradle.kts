rootProject.name = "GregChess"
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        gradlePluginPortal()
    }
    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        val loomVersion: String by settings
        id("fabric-loom") version loomVersion
        val dokkaVersion: String by settings
        id("org.jetbrains.dokka") version dokkaVersion
        val minecraftServerGradlePluginVersion: String by settings
        id("dev.s7a.gradle.minecraft.server") version minecraftServerGradlePluginVersion
    }
}
include("gregchess-core", "gregchess-bukkit", "gregchess-fabric", "bukkit-utils")