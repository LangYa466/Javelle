import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        intellijPlatform { defaultRepositories() }
    }
}

rootProject.name = "Teyru"

include(
    "compiler-core",
    "workspace-model",
    "java-resolver",
    "compiler-driver",
    "compiler-cli",
    "language-tooling",
    "language-protocol",
    "language-server",
    "gradle-plugin",
    "intellij-plugin",
    "migration",
    "testkit",
    "compatibility-tests",
    "integration-tests",
    "release-tools",
)
