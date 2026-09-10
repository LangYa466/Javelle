pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "Javelle"

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
