plugins {
    id("dev.teyru.java-conventions")
    application
}

dependencies {
    implementation(project(":compiler-driver"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("dev.teyru.compiler.cli.TeyruCli")
    applicationName = "teyru"
}

sourceSets.main {
    resources.srcDir(rootProject.file("spec/diagnostics"))
    resources.srcDir(rootProject.file("spec/cli"))
}
sourceSets.test { resources.srcDir(rootProject.file("workspace-model/src/test/resources")) }

tasks.test {
    dependsOn(tasks.installDist)
    systemProperty("teyru.launcher", layout.buildDirectory.file("install/teyru/bin/teyru").get().asFile.absolutePath)
}

tasks.register("p07BlackBox") {
    group = "verification"
    dependsOn(tasks.test)
}
