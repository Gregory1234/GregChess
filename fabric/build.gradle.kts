import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm")
    id("fabric-loom")
}

loom.runConfigs.forEach {
    it.runDir = "fabric/" + it.runDir
}

repositories {
    maven ("https://server.bbkr.space/artifactory/libs-release")
}

dependencies {
    val fabricMinecraftVersion: String by project
    minecraft("com.mojang:minecraft:$fabricMinecraftVersion")
    val yarnMappings: String by project
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    val fabricLoaderVersion: String by project
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    val fabricVersion: String by project
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    val fabricKotlinVersion: String by project
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
    val fabricLibGuiVersion: String by project
    modImplementation("io.github.cottonmc:LibGui:$fabricLibGuiVersion")
    include("io.github.cottonmc:LibGui:$fabricLibGuiVersion")
    implementation(project(":core"))
    include(project(":core"))
}

fun CopySpec.replace(vararg args: Pair<String, Any>) = filter<ReplaceTokens>("tokens" to mapOf(*args))

tasks {
    compileKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts",
                "-Xjvm-default=all",
                "-progressive")
        }
    }
    processResources {
        val fabricLoaderVersion: String by project
        val fabricKotlinVersion: String by project
        val fabricMinecraftVersion: String by project
        val jvmVersion: String by project
        from(sourceSets["main"].resources.srcDirs) {
            include("**/*.json")
            replace(
                "version" to version,
                "loader-version" to fabricLoaderVersion,
                "fabric-kotlin-version" to fabricKotlinVersion,
                "minecraft-version" to fabricMinecraftVersion.dropLastWhile { it != '.' } + "x",
                "java-min-version" to jvmVersion
            )
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    jar {
        exclude { it.file.extension == "kotlin_metadata" }
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}