plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "io.github.m1omg.shrink.desktop.Main"
}
