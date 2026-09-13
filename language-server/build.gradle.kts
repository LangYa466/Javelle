plugins {
    id("dev.teyru.java-conventions")
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
    mainClass = "dev.teyru.language.server.TeyruLanguageServerMain"
    applicationName = "teyru-lsp"
}

tasks.test {
    dependsOn(tasks.installDist)
    doFirst { systemProperty("teyruLsp", layout.buildDirectory.file("install/teyru-lsp/bin/teyru-lsp").get().asFile.absolutePath) }
}
