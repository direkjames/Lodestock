plugins {
    `java-library`
    `maven-publish`
}

// The API compiles for Java 21 so it works on every supported server.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // The server provides Paper at runtime. Lodestock itself carries these classes in its jar.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "lodestock-api"
            from(components["java"])
        }
    }
}
