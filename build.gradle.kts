// Shared configuration for all modules. Module-specific setup lives in each
// module's own build.gradle.kts (core, desktop, android).
allprojects {
    group = "io.github.m1omg.shrink"
    version = providers.gradleProperty("shrink.version").getOrElse("1.0.0")
}
