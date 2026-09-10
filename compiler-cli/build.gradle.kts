plugins {
    id("org.javelle.java-conventions")
    application
}

dependencies {
    implementation(project(":compiler-driver"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("org.javelle.compiler.cli.JavelleCli")
    applicationName = "javelle"
}

sourceSets.main {
    resources.srcDir(rootProject.file("spec/diagnostics"))
    resources.srcDir(rootProject.file("spec/cli"))
}
sourceSets.test { resources.srcDir(rootProject.file("workspace-model/src/test/resources")) }

tasks.test {
    dependsOn(tasks.installDist)
    systemProperty("javelle.launcher", layout.buildDirectory.file("install/javelle/bin/javelle").get().asFile.absolutePath)
}

tasks.register("p07BlackBox") {
    group = "verification"
    dependsOn(tasks.test)
}
