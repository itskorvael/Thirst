plugins {
    `java-library`
}

group = property("group") as String
version = property("version") as String

// Default target is Paper 26.1.2. Build for another Paper line with e.g.
//   ./gradlew build -PpaperApi=26.2.build.135-stable -PapiVersion=26.2
// which sets the compile API and the plugin's `api-version`, and tags the jar
// with a `paper-<apiVersion>` classifier.
val defaultApiVersion = "26.1"
val paperApi = providers.gradleProperty("paperApi").getOrElse("26.1.2.build.74-stable")
val apiVersion = providers.gradleProperty("apiVersion").getOrElse(defaultApiVersion)
// Paper 1.21.x servers run on Java 21; 26.x needs 25. Override with -PjavaVersion=21.
val javaVersion = providers.gradleProperty("javaVersion").getOrElse("25").toInt()

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
    // Paper 26.1.2 by default (year-based versioning; the "26.1" drop, hotfix
    // line 26.1.2); override with -PpaperApi, see the top of this file.
    // Runtime requires Java 25 — see the toolchain block below.
    compileOnly("io.papermc.paper:paper-api:$paperApi")

    // SQLite driver for per-chunk water-quality storage.
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
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
    options.compilerArgs.add("-Xlint:all,-processing")
}

tasks.processResources {
    val props = mapOf("version" to project.version, "apiVersion" to apiVersion)
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
    if (apiVersion != defaultApiVersion) {
        archiveClassifier.set("paper-$apiVersion")
    }
}
