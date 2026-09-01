plugins {
    `java-library`
}

group = property("group") as String
version = property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
}

// Let tests use the same API jars the main sources compile against
// (paper-api / adventure / PlaceholderAPI), which are otherwise compileOnly.
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

dependencies {
    // Paper 26.1.2 (year-based versioning; the "26.1" drop, hotfix line 26.1.2).
    // Runtime requires Java 25 — see the toolchain block below.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")

    // SQLite driver for per-chunk water-quality storage (Phase 4).
    // Not shaded: it is pulled in at runtime by ThirstPluginLoader via Paper's
    // library loader, so it stays compileOnly here.
    compileOnly("org.xerial:sqlite-jdbc:3.53.4.0")

    // Optional soft-dependency for hud.external-segment (e.g. RealisticSeasons
    // temperature in the same action bar). Guarded at runtime by an
    // isPluginEnabled("PlaceholderAPI") check; declared soft in paper-plugin.yml.
    compileOnly("me.clip:placeholderapi:2.12.3")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        // Paper 26.1.x requires Java 25 at runtime; compile against the same.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:all,-processing")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveBaseName.set("Thirst")
}
