import org.apache.tools.ant.filters.ReplaceTokens
import java.net.URL

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("fabric-loom")
    id("org.jetbrains.dokka")
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
    val fabricDrawerVersion: String by project
    modImplementation("io.github.natanfudge:fabric-drawer:$fabricDrawerVersion")
    include("io.github.natanfudge:fabric-drawer:$fabricDrawerVersion")
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
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
                "-Xlambdas=indy",
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
    withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
        dokkaSourceSets {
            configureEach {
                externalDocumentationLink {
                    url.set(URL("https://cottonmc.github.io/docs/libgui/"))
                    packageListUrl.set(URL("https://cottonmc.github.io/docs/libgui/element-list"))
                }
                externalDocumentationLink {
                    val yarnMappings: String by project
                    url.set(URL("https://maven.fabricmc.net/docs/yarn-$yarnMappings/"))
                    packageListUrl.set(URL("https://maven.fabricmc.net/docs/yarn-$yarnMappings/element-list"))
                }
            }
        }
    }
}