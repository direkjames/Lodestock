plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

// Compile for Java 21 so the same jar runs on Java 21 (1.21.11) and Java 25 (26.x).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":core"))

    // Compile against the OLDEST supported version, so we can't use an API that 1.21.11 lacks.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Tests (the plugin itself uses the server's own SQLite driver)
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("Lodestock-Paper")
    relocate("org.bstats", "io.github.direkjames.lodestock.libs.bstats")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Keep this OUTSIDE the "tasks { }" block below.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks {
    runServer {
        // Pick the Minecraft version with -PmcVersion=26.1 (default 1.21.11).
        val mcVersion = providers.gradleProperty("mcVersion").getOrElse("1.21.11")
        minecraftVersion(mcVersion)
        // Each version gets its own folder, so worlds from different versions never mix.
        runDirectory.set(layout.projectDirectory.dir("run/$mcVersion"))
    }
}