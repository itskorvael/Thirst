plugins {
    // Lets the Gradle toolchain auto-download a JDK 25 when one is not installed
    // locally (Minecraft 26.1+ / Paper requires Java 25).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Thirst"
