pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Google's repository is only asked for Android artifacts, so the desktop
        // build works on machines that cannot reach it.
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android\\..*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "shrink"

include(":core", ":desktop")

// The Android app is built only when an Android SDK is present (or when forced
// with -Pshrink.android=true). This keeps `./gradlew :desktop:run` working on
// computers without Android tooling. Use -Pshrink.android=false to skip it.
val androidMode = providers.gradleProperty("shrink.android").getOrElse("auto")
val localProperties = file("local.properties")
val androidSdkPresent = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProperties.isFile && localProperties.readText().contains("sdk.dir"))
if (androidMode == "true" || (androidMode == "auto" && androidSdkPresent)) {
    include(":android")
}
