import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins { id("org.javelle.java-conventions") }

dependencies {
    implementation(project(":compiler-core"))
    implementation(project(":java-resolver"))
    implementation(project(":workspace-model"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform { excludeTags("native-jdk21") }
}

tasks.register<Test>("p06EndToEnd") {
    description = "Runs the real Javelle-to-javac-to-JVM P06 acceptance suite."
    group = "verification"
    useJUnitPlatform { excludeTags("native-jdk21") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    filter.includeTestsMatching("org.javelle.compiler.driver.P06CompilerDriverTest")
}

tasks.register<Test>("p06Release21") {
    description = "Runs the P06 Java --release 21 mixed-compilation profile."
    group = "verification"
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform { includeTags("native-jdk21") }
    filter.includeTestsMatching("org.javelle.compiler.driver.P06CompilerDriverTest.nativeJdk21RunsTheRealDriverPipeline")
}
