plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "chat.tox.ktox"
    compileSdk = 37

    defaultConfig {
        applicationId = "chat.tox.ktox"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        // The ABIs native/build.sh builds by default.
        ndk.abiFilters += listOf("arm64-v8a", "x86_64")
    }

    // native/build.sh android puts libtox4j-c.so here.
    sourceSets["main"].jniLibs.directories.add("../native/out/android")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
}
