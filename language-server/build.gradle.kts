plugins {
    id("org.javelle.java-conventions")
    application
}

dependencies {
    implementation(project(":language-tooling"))
    implementation(project(":language-protocol"))
    implementation(project(":workspace-model"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "org.javelle.language.server.JavelleLanguageServerMain"
    applicationName = "javelle-lsp"
}

tasks.test {
    dependsOn(tasks.installDist)
    doFirst { systemProperty("javelleLsp", layout.buildDirectory.file("install/javelle-lsp/bin/javelle-lsp").get().asFile.absolutePath) }
}
