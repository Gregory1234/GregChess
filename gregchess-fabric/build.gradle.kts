plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("fabric-loom")
    id("org.jetbrains.dokka")
}

loom.runConfigs.forEach {
    it.runDir = "gregchess-fabric/" + it.runDir
}

repositories {
    maven ("https://server.bbkr.space/artifactory/libs-release") { name = "LibGui" }
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
    implementation(project(":gregchess-core"))
    include(project(":gregchess-core"))
}

tasks {
    compileJava {
        val jvmVersion: String by project
        sourceCompatibility = jvmVersion
        targetCompatibility = jvmVersion
    }
    compileKotlin {
        kotlinOptions {
            val jvmVersion: String by project
            jvmTarget = jvmVersion
            freeCompilerArgs = defaultKotlinArgs
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
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                gregchessSourceLink(project)
                externalDocumentationLinkElementList("https://cottonmc.github.io/docs/libgui/")
                val yarnMappings: String by project
                externalDocumentationLinkElementList("https://maven.fabricmc.net/docs/yarn-$yarnMappings/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-core/kotlinx-serialization-core/")
                externalDocumentationLink("https://kotlin.github.io/kotlinx.coroutines/")
            }
        }
    }
}