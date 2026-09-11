plugins {
    id("org.javelle.java-conventions")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    implementation(project(":language-protocol"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaUltimate("2026.1.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id.set("org.javelle.ide")
        name.set("Javelle")
        version.set(project.version.toString())
        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set("261.*")
        }
    }
}

val bundledLsp = tasks.register<Sync>("syncBundledLsp") {
    dependsOn(":language-server:installDist")
    from(project(":language-server").layout.buildDirectory.dir("install/javelle-lsp"))
    into(layout.buildDirectory.dir("bundled-lsp"))
    filesMatching("**/bin/*") { permissions { unix("0755") } }
}

val buildPlugin = tasks.named<Zip>("buildPlugin") {
    dependsOn(bundledLsp)
    from(bundledLsp) {
        into("javelle-lsp")
        filesMatching("**/bin/*") { permissions { unix("0755") } }
    }
}

tasks.test {
    dependsOn(buildPlugin)
    systemProperty("java.awt.headless", "true")
    doFirst {
        systemProperty("javelleIntellijPluginZip", buildPlugin.get().archiveFile.get().asFile.absolutePath)
    }
}
