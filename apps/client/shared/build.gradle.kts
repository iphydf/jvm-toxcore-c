plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "chat.tox.ktox.shared"
        compileSdk = 37
        minSdk = 29
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.tox4j)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            implementation(libs.compose.material.icons)
            implementation(libs.coroutines.core)
            implementation(libs.sqlite.bundled)
            implementation(libs.zxing)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// The clients in the tests need the library from native/build.sh desktop.
tasks.withType<Test>().configureEach {
    val os = System.getProperty("os.name").lowercase().let { if (it.startsWith("mac")) "macos" else it }
    val arch = System.getProperty("os.arch").let { if (it == "amd64" || it == "x86_64") "x64" else "arm64" }
    systemProperty("java.library.path", rootDir.resolve("native/out/desktop/$os-$arch").path)
}
