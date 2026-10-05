plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0"
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

repositories {
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    implementation(project(":core"))
    compileOnly("org.purpurmc.purpur:purpur-api:26.1.2.build.2583-stable")

    // Add after you get your bStats plugin ID (use the version bStats shows you):
    implementation("org.bstats:bstats-bukkit:3.2.1")
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
    // When you add bStats, uncomment this:
    relocate("org.bstats", "io.github.direkjames.lodestock.libs.bstats")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}