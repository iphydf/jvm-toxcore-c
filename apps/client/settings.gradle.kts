rootProject.name = "tox4j-client"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// The binding from this repository.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("org.toktok:tox4j-c")).using(project(":lib"))
    }
}

include(":shared", ":androidApp", ":desktopApp")
