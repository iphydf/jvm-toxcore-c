import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
}

// native/build.sh desktop puts libtox4j-c.so in OS-ARCH directories here.
val nativeDir = rootDir.resolve("native/out/desktop")

compose.desktop {
    application {
        mainClass = "chat.tox.ktox.MainKt"
        // The packaged app has the library in its resources.
        jvmArgs += "-Djava.library.path=\$APPDIR/resources"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "ktox"
            packageVersion = "0.0.1"
            appResourcesRootDir = nativeDir
            linux.iconFile = file("src/main/resources/ktox.png")
        }
    }
}

// Running from Gradle, it comes straight from native/out.
tasks.withType<JavaExec>().configureEach {
    val os = System.getProperty("os.name").lowercase().let { if (it.startsWith("mac")) "macos" else it }
    val arch = System.getProperty("os.arch").let { if (it == "amd64" || it == "x86_64") "x64" else "arm64" }
    jvmArgumentProviders += CommandLineArgumentProvider { listOf("-Djava.library.path=${nativeDir.resolve("$os-$arch")}") }
}
