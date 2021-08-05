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
    }
}
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        val jacksonOverrideVersion: String by settings
        classpath("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonOverrideVersion")
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}
include("core", "bukkit", "fabric")