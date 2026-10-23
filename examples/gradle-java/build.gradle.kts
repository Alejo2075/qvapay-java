// Kotlin DSL consumer of the QvaPay Java SDK.
plugins {
    java
    application
}

repositories {
    // Pre-release: the SDK is installed locally with `./mvnw install` from the repository root.
    mavenLocal()
    // Once the SDK is published, mavenCentral() is all you need (remove mavenLocal()).
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation("io.github.alejo2075:qvapay-java:0.1.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "example.InvoiceDemo"
}

tasks.test {
    useJUnitPlatform()
}
