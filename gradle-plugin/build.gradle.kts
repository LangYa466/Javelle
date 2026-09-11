import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("org.javelle.java-conventions")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":workspace-model"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    dependsOn(":workspace-model:jar", ":compiler-cli:installDist")
    doFirst {
        val modelJar = project(":workspace-model").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        systemProperty("workspaceModelJar", modelJar.absolutePath)
        systemProperty("javelleExecutable", project(":compiler-cli").layout.buildDirectory.file("install/javelle/bin/javelle").get().asFile.absolutePath)
        systemProperty("javelleRepoRoot", rootProject.projectDir.absolutePath)
        systemProperty("javelleJUnitFiles", configurations.testRuntimeClasspath.get().files.filter { it.name.contains("junit") || it.name.contains("opentest4j") || it.name.contains("apiguardian") }.joinToString(File.pathSeparator) { it.absolutePath })
        systemProperty("javelleJdk21Home", javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.get().metadata.installationPath.asFile.absolutePath)
        systemProperty("javelleJdk25Home", javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }.get().metadata.installationPath.asFile.absolutePath)
    }
}

tasks.register<Test>("p09TestKit") {
    group = "verification"
    description = "Runs P09 external Gradle consumer acceptance tests."
    dependsOn(tasks.test)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("p09-testkit") }
    doFirst {
        systemProperty("javelleExecutable", project(":compiler-cli").layout.buildDirectory.file("install/javelle/bin/javelle").get().asFile.absolutePath)
        systemProperty("javelleRepoRoot", rootProject.projectDir.absolutePath)
        systemProperty("javelleJUnitFiles", configurations.testRuntimeClasspath.get().files.filter { it.name.contains("junit") || it.name.contains("opentest4j") || it.name.contains("apiguardian") }.joinToString(File.pathSeparator) { it.absolutePath })
        systemProperty("javelleJdk21Home", javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.get().metadata.installationPath.asFile.absolutePath)
        systemProperty("javelleJdk25Home", javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }.get().metadata.installationPath.asFile.absolutePath)
    }
}

gradlePlugin {
    plugins {
        create("javelle") {
            id = "org.javelle"
            implementationClass = "org.javelle.gradle.JavellePlugin"
        }
    }
}
