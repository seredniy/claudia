plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = properties["pluginGroup"] as String
version = properties["pluginVersion"] as String

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
    }
}

dependencies {
    // IntelliJ Platform dependencies
    intellijPlatform {
        phpstorm(properties["platformVersion"] as String)

        // Terminal plugin for sending files to Claude Code
        bundledPlugin("org.jetbrains.plugins.terminal")

        // Required for plugin development
        instrumentationTools()

        // Disable for now to simplify build
        // pluginVerifier()
        // testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    // HTTP Client for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    // Testing - disabled for now to simplify build
    // testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    // testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = properties["pluginName"] as String
        version = properties["pluginVersion"] as String
        description = properties["pluginDescription"] as String

        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    // Plugin verification disabled for now to avoid dependency resolution issues
    // Re-enable later with: pluginVerifier() in dependencies
    /*
    pluginVerification {
        ides {
            ide(properties["platformType"] as String, properties["platformVersion"] as String)
        }
    }
    */
}

tasks {
    // Disable tests for now - re-enable when adding test dependencies
    /*
    test {
        useJUnitPlatform()
    }
    */

    buildSearchableOptions {
        enabled = false
    }

    buildPlugin {
        archiveBaseName.set("claudia")
    }
}
