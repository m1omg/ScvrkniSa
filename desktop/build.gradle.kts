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

// Tools used only while building (the icon renderer); not part of the app.
val packagingTools: SourceSet = sourceSets.create("packagingTools") {
    java.srcDir("src/packaging/java")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.jsvg)
    if (bundleFfmpeg) {
        // Only the native programs are needed, not the JNI bindings.
        runtimeOnly("org.bytedeco:ffmpeg:$ffmpegVersion:$shrinkPlatform-gpl") { isTransitive = false }
    }
    "packagingToolsImplementation"(libs.jsvg)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-options", "-Xlint:-this-escape"))
}

val os: OperatingSystem = OperatingSystem.current()
val jvmFlags = buildList {
    add("-Dapple.awt.application.appearance=system")
    add("-Dapple.awt.application.name=Shrink!")
    if (os.isLinux) {
        // Lets Main set the X11 window class, so the dock shows the right icon.
        add("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
    }
}

application {
    mainClass = "io.github.m1omg.shrink.desktop.Main"
    applicationName = "shrink"
    applicationDefaultJvmArgs = jvmFlags
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

// ---------------------------------------------------------------- packaging
//
// `./gradlew :desktop:jpackage` builds the app with its own Java runtime:
//   macOS   -> build/jpackage/out/Shrink!-<version>.dmg
//   Linux   -> .deb, .rpm (when the tools are installed) and a portable folder
//   Windows -> an .msi installer (when WiX Toolset 3 is installed) and a portable folder

val generateIcons by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Renders the app icon (PNG, ICNS, ICO) from the SVG logo."
    val svg = file("src/main/resources/io/github/m1omg/shrink/desktop/icons/logo.svg")
    val outDir = layout.buildDirectory.dir("icons")
    inputs.file(svg)
    outputs.dir(outDir)
    classpath = packagingTools.runtimeClasspath
    mainClass = "IconExport"
    jvmArgs("-Djava.awt.headless=true")
    args(svg.absolutePath, outDir.get().asFile.absolutePath)
}

val jpackageInput by tasks.registering(Sync::class) {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("jpackage/input"))
}

val appName = if (os.isMacOsX) "Shrink!" else "Shrink"
// jpackage wants a plain numeric version such as 1.0.0.
val appVersion = project.version.toString().replace(Regex("[^0-9.].*$"), "").ifEmpty { "1.0.0" }
val jpackageOut = layout.buildDirectory.dir("jpackage/out")

fun jpackageArgs(type: String): List<String> {
    val icons = layout.buildDirectory.dir("icons").get().asFile
    val args = mutableListOf(
        File(System.getProperty("java.home"), if (os.isWindows) "bin/jpackage.exe" else "bin/jpackage").absolutePath,
        "--type", type,
        "--name", appName,
        "--app-version", appVersion,
        "--vendor", "m1omg",
        "--description", "Compress, convert and edit videos",
        "--copyright", "Shrink! is free software under the GNU GPL v3",
        "--input", layout.buildDirectory.dir("jpackage/input").get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", application.mainClass.get(),
        "--dest", jpackageOut.get().asFile.absolutePath,
        // jdk.localedata with en+sk gives correct Slovak number and date formats.
        "--add-modules", "java.base,java.desktop,java.logging,java.prefs,jdk.localedata,jdk.accessibility,jdk.unsupported",
        "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files --include-locales=en,sk",
    )
    for (flag in jvmFlags) {
        args += listOf("--java-options", flag)
    }
    when {
        os.isMacOsX -> {
            args += listOf("--icon", File(icons, "shrink.icns").absolutePath,
                "--mac-package-identifier", "io.github.m1omg.shrink",
                "--mac-package-name", "Shrink!",
                "--mac-app-category", "public.app-category.video")
        }
        os.isWindows -> {
            args += listOf("--icon", File(icons, "shrink.ico").absolutePath)
            if (type == "msi") {
                // Installs for the current user only, so no administrator rights are needed.
                args += listOf("--win-per-user-install",
                    "--win-menu", "--win-menu-group", "Shrink!",
                    "--win-shortcut", "--win-shortcut-prompt",
                    "--win-dir-chooser",
                    // Never change: lets new versions replace old ones.
                    "--win-upgrade-uuid", "5f8a3c1e-2b7d-4e9a-9c64-0d1b7e3a52f4",
                    "--about-url", "https://github.com/m1omg/ScvrkniSa")
            }
        }
        else -> {
            args += listOf("--icon", File(icons, "shrink.png").absolutePath)
            if (type != "app-image") {
                args += listOf("--linux-package-name", "shrink",
                    "--linux-app-category", "video",
                    "--linux-menu-group", "AudioVideo;Video;",
                    "--linux-shortcut",
                    "--linux-deb-maintainer", "noreply@github.com",
                    "--resource-dir", file("packaging/linux").absolutePath)
                // The bundled FFmpeg sits inside a jar, where jpackage cannot see which
                // system libraries it uses, so they are listed here. Every desktop
                // distribution has them; this only matters on minimal systems.
                args += listOf("--linux-package-deps", if (type == "deb") {
                    "libasound2, libpulse0, libxcb1, libxcb-shm0, libstdc++6, libgcc-s1"
                } else {
                    "libasound.so.2()(64bit), libpulse.so.0()(64bit), libxcb.so.1()(64bit), " +
                        "libxcb-shm.so.0()(64bit), libstdc++.so.6()(64bit)"
                })
            }
        }
    }
    if (type != "app-image") {
        for (association in fileTree("packaging") { include("associations-*.properties") }.files.sorted()) {
            args += listOf("--file-associations", association.absolutePath)
        }
    }
    return args
}

fun commandExists(name: String): Boolean =
    System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, name).canExecute() }

// WiX Toolset 3 builds the Windows installer; jpackage finds it on the PATH or in
// its default folder (the WIX variable points there).
fun wixAvailable(): Boolean = commandExists("candle.exe") || System.getenv("WIX") != null

val packageTypes: List<String> = when {
    os.isMacOsX -> listOf("dmg")
    os.isWindows -> if (wixAvailable()) listOf("app-image", "msi") else listOf("app-image")
    else -> buildList {
        add("app-image")
        if (commandExists("dpkg-deb") && commandExists("fakeroot")) add("deb")
        if (commandExists("rpmbuild")) add("rpm")
    }
}

val cleanJpackage by tasks.registering(Delete::class) {
    delete(jpackageOut)
}

val jpackageTasks = packageTypes.map { type ->
    tasks.register<Exec>("jpackage${type.replace("-", "").replaceFirstChar { it.uppercase() }}") {
        group = "distribution"
        description = "Builds the $type package with jpackage."
        dependsOn(jpackageInput, generateIcons, cleanJpackage)
        doFirst { jpackageOut.get().asFile.mkdirs() }
        commandLine(jpackageArgs(type))
    }
}

tasks.register("jpackage") {
    group = "distribution"
    description = "Builds the native packages for this operating system."
    dependsOn(jpackageTasks)
}
