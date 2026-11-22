plugins {
    kotlin("jvm") version "2.3.21"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // https://docs.gradle.org/current/userguide/publishing_maven.html
    `maven-publish`
}

group = "org.toktok"
version = "0.3.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use Kotlin Test test framework
            useKotlinTest("2.3.21")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "tox4j-c"
            from(components["java"])
        }
    }
}
