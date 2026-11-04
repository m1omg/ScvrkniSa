import org.gradle.internal.os.OperatingSystem

plugins {
    application
}

// ---------------------------------------------------------------- bundled FFmpeg
//
// FFmpeg comes from the bytedeco "ffmpeg" artifacts on Maven Central, which
// contain ready-made ffmpeg/ffprobe programs for each platform. Only the
// platform being built for is bundled. Override with -Pshrink.platform=...,
// e.g. -Pshrink.platform=macosx-x86_64 to package for Intel Macs.
fun hostPlatform(): String {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase()
    val cpu = if (arch == "aarch64" || arch == "arm64") "arm64" else "x86_64"
    return when {
        os.isMacOsX -> "macosx-$cpu"
        os.isWindows -> "windows-$cpu"
        else -> "linux-$cpu"
    }
}

val shrinkPlatform: String = providers.gradleProperty("shrink.platform").getOrElse(hostPlatform())
val bundleFfmpeg: Boolean = providers.gradleProperty("shrink.bundleFfmpeg").getOrElse("true").toBoolean()
val ffmpegVersion: String = libs.versions.bytedecoFfmpeg.get()

dependencies {
    implementation(project(":core"))
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.jsvg)
    if (bundleFfmpeg) {
        // Only the native programs are needed, not the JNI bindings.
        runtimeOnly("org.bytedeco:ffmpeg:$ffmpegVersion:$shrinkPlatform-gpl") { isTransitive = false }
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-options", "-Xlint:-this-escape"))
}

application {
    mainClass = "io.github.m1omg.shrink.desktop.Main"
    applicationName = "shrink"
    applicationDefaultJvmArgs = listOf(
        "-Dapple.awt.application.appearance=system",
        "-Dapple.awt.application.name=Shrink!",
        "-Dsun.java2d.metal=true",
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Shrink!",
            "Implementation-Version" to project.version,
            "Main-Class" to application.mainClass.get(),
        )
    }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<JavaExec>("run") {
    // Lets `./gradlew :desktop:run --args="video.mp4"` open a file directly.
    workingDir = rootProject.projectDir
}

extra["shrinkPlatform"] = shrinkPlatform
apply(from = "packaging.gradle.kts")
