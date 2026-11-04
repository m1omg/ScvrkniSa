plugins {
    `java-library`
}

// The core library is shared with the Android app, so it is compiled for Java 17
// and uses only APIs that Android (API 29+) provides.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-options"))
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Tests that need a real ffmpeg binary look for it through this property.
    systemProperty("shrink.ffmpeg", providers.gradleProperty("shrink.ffmpeg").getOrElse(""))
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
