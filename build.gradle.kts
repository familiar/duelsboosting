import dev.architectury.pack200.java.Pack200Adapter

plugins {
    kotlin("jvm")
    id("gg.essential.loom")
    id("io.github.juuxel.loom-quiltflower")
    id("dev.architectury.architectury-pack200")
}

group = "best.spaghetcodes"
version = "1.0.0"

loom {
    runConfigs {
        named("client") {
            ideConfigGenerated(true)
        }
    }

    forge {
        pack200Provider.set(Pack200Adapter())
    }
}

repositories {
    mavenCentral()
    maven("https://repo.essential.gg/repository/maven-public")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.minecraftforge.net/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    // Gson for webhook functionality (using MC's bundled version)
    compileOnly("com.google.code.gson:gson:2.2.4")
}

tasks {
    jar {
        manifest.attributes(
            mapOf(
                "ModSide" to "CLIENT",
                "FMLCorePluginContainsFMLMod" to "true",
                "ForceLoadAsMod" to "true"
            )
        )
        archiveClassifier.set("dev")
        
        // Exclude problematic metadata but keep Kotlin runtime
        exclude("META-INF/maven/**")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    remapJar {
        archiveClassifier.set("")
        
        // Same exclusions for remapped jar
        exclude("META-INF/maven/**")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("mcversion", "1.8.9")
        filesMatching("mcmod.info") {
            expand("version" to project.version, "mcversion" to "1.8.9")
        }
    }

    withType<JavaCompile> {
        options.release.set(8)
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            languageVersion = "1.8"
            apiVersion = "1.8"
        }
    }
}



