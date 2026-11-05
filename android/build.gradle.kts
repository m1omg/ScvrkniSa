import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Turns the shared .properties translations (core module) into Android string
 * resources, so desktop and Android use exactly the same texts.
 * Keys "tool.trim.title" become "tool_trim_title"; "{0}" becomes "%1$s".
 */
abstract class GenerateStringsTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        convert("messages.properties", "values")
        convert("messages_sk.properties", "values-sk")
    }

    private fun convert(fileName: String, folder: String) {
        val props = Properties()
        inputDir.file(fileName).get().asFile.reader(Charsets.UTF_8).use { props.load(it) }
        val argument = Regex("\\{(\\d+)}")
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<!-- Generated from core/src/main/resources/.../$fileName. Do not edit. -->\n<resources>\n")
        for (key in props.stringPropertyNames().sorted()) {
            var value = props.getProperty(key)
            val formatted = argument.containsMatchIn(value)
            value = value.replace("\\", "\\\\")
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n")
            if (value.startsWith("@") || value.startsWith("?")) {
                value = "\\" + value
            }
            val name = key.replace('.', '_')
            if (formatted) {
                value = value.replace("%", "%%")
                value = argument.replace(value) { m -> "%" + (m.groupValues[1].toInt() + 1) + "\$s" }
                sb.append("    <string name=\"").append(name).append("\">").append(value).append("</string>\n")
            } else {
                sb.append("    <string name=\"").append(name).append("\" formatted=\"false\">")
                    .append(value).append("</string>\n")
            }
        }
        sb.append("</resources>\n")
        val target = outputDir.file("$folder/strings.xml").get().asFile
        target.parentFile.mkdirs()
        target.writeText(sb.toString(), Charsets.UTF_8)
    }
}

val generateStrings = tasks.register<GenerateStringsTask>("generateStrings") {
    inputDir.set(rootProject.layout.projectDirectory.dir("core/src/main/resources/io/github/m1omg/shrink/i18n"))
}

android {
    namespace = "io.github.m1omg.shrink.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.m1omg.shrink"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = project.version.toString()
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A fixed debug key (committed on purpose) so that every build can be installed
        // over the previous one without uninstalling. It is public: real releases should
        // use a private key, given through the SHRINK_KEYSTORE* variables below.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release builds are signed with a keystore from the environment when available
        // (see .github/workflows); otherwise with the debug key so the APK can be installed.
        val storePath = System.getenv("SHRINK_KEYSTORE")
        if (storePath != null && file(storePath).isFile) {
            create("release") {
                storeFile = file(storePath)
                storePassword = System.getenv("SHRINK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SHRINK_KEY_ALIAS")
                keyPassword = System.getenv("SHRINK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateStrings, GenerateStringsTask::outputDir)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.recyclerview)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.muxer)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.jump3r)

    // Instrumented tests (run on an emulator in CI: ./gradlew :android:connectedDebugAndroidTest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
}
