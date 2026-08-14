import org.apache.commons.lang3.SystemUtils
plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
//Constants:
val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val mixinGroup = "$baseGroup.mixin"
val modid: String by project
val jarName: String by project
val transformerFile = file("src/main/resources/accesstransformer.cfg")
// Toolchains:
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
// Minecraft configuration:
loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            // If you don't want mixins, remove these lines
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                // This argument causes a crash on macOS
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        // If you don't want mixins, remove this lines
        mixinConfig("mixins.$modid.json")
	    if (transformerFile.exists()) {
			println("Installing access transformer")
		    accessTransformer(transformerFile)
	    }
    }
    // If you don't want mixins, remove these lines
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}
sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}
// Dependencies:
repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    // If you don't want to log in with your real minecraft account, remove this line
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    // OneConfig adaptation layer (compile-only, runtime provided by OneConfig mod)
    maven("https://repo.polyfrost.cc/releases")
}
val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    // If you don't want mixins, remove these lines
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    annotationProcessor("org.ow2.asm:asm:9.2")
    annotationProcessor("org.ow2.asm:asm-commons:9.2")
    annotationProcessor("org.ow2.asm:asm-tree:9.2")
    // If you don't want to log in with your real minecraft account, remove this line
    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
    // OneConfig adaptation layer (compile-only - runtime provided by OneConfig mod installed separately)
    compileOnly("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+")
    // SLF4J logging (bundled in jar)
    shadowImpl("org.slf4j:slf4j-api:1.7.36")
    // Lombok (compile-time only)
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    // Music player dependencies (bundled in jar)
    shadowImpl("javazoom:jlayer:1.0.1")
    shadowImpl("org.jflac:jflac-codec:1.5.2")
    shadowImpl("com.squareup.okhttp3:okhttp:4.12.0")
    // ASM for string encryption (compile-time only, not bundled)
    compileOnly("org.ow2.asm:asm:9.2")
}
// Tasks:
tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
    // 使用 JDK 17 编译，但生成 1.8 字节码以兼容 Minecraft 1.8.9 运行时
    options.release.set(8)
    options.compilerArgs.addAll(listOf("-Xlint:none", "-Xdoclint:none"))
}
tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(jarName)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        // If you don't want mixins, remove these lines
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
	    if (transformerFile.exists())
			this["FMLAT"] = "${modid}_at.cfg"
    }
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    filesMatching(listOf("mcmod.info", "mixins.$modid.json","version.json")) {
        expand(inputs.properties)
    }
    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
}
val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}
tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}
tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    doLast {
        configurations.forEach {
            println("Copying dependencies into mod: ${it.files}")
        }
    }
    fun relocate(name: String) = relocate(name, "$baseGroup.deps.$name")
    relocate("javazoom")
    relocate("okhttp3")
    relocate("okio")
    relocate("org.slf4j")
}
tasks.assemble.get().dependsOn(tasks.remapJar)

val asmJar by configurations.creating
dependencies {
    asmJar("org.ow2.asm:asm:9.5")
}

val encryptStrings by tasks.registering(JavaExec::class) {
    dependsOn(tasks.compileJava)
    dependsOn(tasks.processResources)
    dependsOn(asmJar)
    
    val classesDir = sourceSets.main.get().output.classesDirs.singleFile
    
    classpath(asmJar)
    classpath(sourceSets.main.get().output.classesDirs)
    mainClass.set("elara.security.StringEncryptor")
    
    args(classesDir.absolutePath)
    
    doFirst {
        println("[StringEncryptor] Starting string encryption on: $classesDir")
    }
    
    doLast {
        println("[StringEncryptor] String encryption completed")
    }
}

tasks.shadowJar {
    // 字符串加密已移除 — 会破坏 OneConfig 反射注解扫描
}

val proguardJar by configurations.creating

dependencies {
    proguardJar("com.guardsquare:proguard-base:7.4.1")
}

val proguard by tasks.registering(JavaExec::class) {
    // 关键：必须在 remapJar 之后执行，输入为 remapJar 的输出（已转为 SRG 名称）
    // 否则混淆的是 MCP 名称，Forge 1.8.9 生产环境无法识别
    dependsOn(tasks.remapJar)
    dependsOn(proguardJar)

    val inputJar = tasks.remapJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("obfuscated").get().asFile
    val backupDir = layout.buildDirectory.dir("backups").get().asFile
    outputDir.mkdirs()
    backupDir.mkdirs()

    val outputJar = file("$outputDir/${jarName}-obfuscated.jar")
    val backupJar = file("$backupDir/${jarName}-${version}-remapped-backup-${System.currentTimeMillis()}.jar")

    classpath(proguardJar)
    mainClass.set("proguard.ProGuard")

    args("-injars", inputJar.absolutePath)
    args("-outjars", outputJar.absolutePath)

    val allLibJars = mutableSetOf<File>()
    configurations.runtimeClasspath.get().files.forEach { allLibJars.add(it) }
    configurations.compileClasspath.get().files.forEach { allLibJars.add(it) }
    // 关键修复：排除被 shade 进输入 JAR 的库（shadowImpl 配置中的依赖）
    // 这些库的 class 已经在输入 JAR 中（由 shadowJar 打包），ProGuard 会直接分析它们。
    // 如果不排除，ProGuard 会把它们当作 library 类，不复制到输出 JAR，
    // 导致 MixinTweaker 运行时类（org.spongepowered.**）、FLAC解码（org.jflac.**）等丢失，客户端无法启动。
    val shadedJars = configurations.named("shadowImpl").get().files
    allLibJars.removeAll(shadedJars)
    println("[ProGuard] Excluded ${shadedJars.size} shaded jars from libraryjars (already in input jar)")
    allLibJars.forEach {
        args("-libraryjars", it.absolutePath)
    }

    args("@proguard-rules.pro")

    doFirst {
        println("[ProGuard] Backing up remapped JAR to: $backupJar")
        inputJar.copyTo(backupJar, overwrite = true)
    }

    doLast {
        println("[ProGuard] Obfuscated JAR created: $outputJar")
        val finalDir = layout.buildDirectory.dir("libs").get().asFile
        finalDir.mkdirs()
        val finalJar = file("$finalDir/${jarName}-${version}-obfuscated.jar")
        outputJar.copyTo(finalJar, overwrite = true)
        println("[ProGuard] Final obfuscated JAR: $finalJar")
    }
}

tasks.register("buildObfuscated") {
    dependsOn(proguard)
    group = "build"
    description = "Builds and obfuscates the mod"
}
