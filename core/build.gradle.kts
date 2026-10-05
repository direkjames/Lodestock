plugins {
    `java-library`
}

// Core compiles for Java 21 so every platform (Java 21 and Java 25) can use it.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}