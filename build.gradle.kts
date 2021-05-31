
import org.apache.tools.ant.filters.ReplaceTokens
import java.io.ByteArrayOutputStream
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.jvm")
    idea
}

group = "gregc"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        name = "spigotmc-repo"
        url = URI("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "sonatype"
        url = URI("https://oss.sonatype.org/content/groups/public/")
    }
}

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project

    "api"("org.spigotmc:spigot-api:$spigotVersion")
    "shaded"(kotlin("stdlib-jdk8"))
    implementation(project(":config"))
}

val generated = "$buildDir/generated"
val maind = "$rootDir/src/main/kotlin"

kotlin{
    sourceSets["main"].kotlin.srcDirs(File(maind),File(generated))
}

idea.module {
    sourceDirs.plusAssign(File(maind))
    generatedSourceDirs.plusAssign(File(generated))
}

tasks {
    register<JavaExec>("regenerateConfig") {
        dependsOn += project(":config").tasks["build"]
        group = "kotlinpoet"
        main = "gregc.gregchess.config.MainKt"
        classpath = project(":config").sourceSets["main"].runtimeClasspath
        standardOutput = ByteArrayOutputStream()
        doLast {
            val outputDir = File("$generated/gregc/gregchess")
            val outputFile = File(outputDir, "Config.kt")
            if(!outputDir.exists()) {
                outputDir.mkdirs()
            }
            outputFile.writeText(standardOutput.toString())
        }
    }

    processResources {
        from(sourceSets["main"].resources.srcDirs) {
            filter<ReplaceTokens>("tokens" to mapOf("version" to version))
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    compileKotlin {
        dependsOn += project.tasks["regenerateConfig"]
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.contracts.ExperimentalContracts"
        }
    }
    jar {
        archiveBaseName.set(project.name)
        destinationDirectory.set(file(rootDir))
        from ({ shaded.map { if(it.isDirectory) it else zipTree(it) } })
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
