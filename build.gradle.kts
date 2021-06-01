
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm")
    idea
}

group = "gregc"
version = "1.0"

val shaded: Configuration by configurations.creating

configurations["implementation"].extendsFrom(shaded)

dependencies {
    val spigotVersion: String by project

    api("org.spigotmc:spigot-api:$spigotVersion")
    shaded(kotlin("stdlib-jdk8"))
    implementation(project(":config"))
}

val generated = "$buildDir/generated"

kotlin {
    sourceSets["main"].kotlin.srcDir(File(generated))
}

idea.module {
    generatedSourceDirs.plusAssign(File(generated))
}

tasks {
    register<JavaExec>("regenerateConfig") {
        inputs.files(project(":config").kotlin.sourceSets["main"].kotlin.srcDirs)
        outputs.dir(generated)
        outputs.upToDateWhen { true }
        dependsOn += project(":config").tasks["build"]
        group = "kotlinpoet"
        main = "gregc.gregchess.config.MainKt"
        classpath = project(":config").sourceSets["main"].runtimeClasspath
        args(generated)
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
            freeCompilerArgs = listOf("-Xopt-in=kotlin.contracts.ExperimentalContracts")
        }
    }
    jar {
        archiveBaseName.set(project.name)
        destinationDirectory.set(file(rootDir))
        from ({ shaded.map { if(it.isDirectory) it else zipTree(it) } })
        exclude { it.file.extension == "kotlin_metadata" }
        from("$generated/config.yml")
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
