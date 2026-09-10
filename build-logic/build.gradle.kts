plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("javaConventions") {
            id = "org.javelle.java-conventions"
            implementationClass = "org.javelle.buildlogic.JavaConventionsPlugin"
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// The build daemon may run on the supported JDK 21 profile. Compile convention plugins to
// Java 21 bytecode while retaining JDK 25 as the pinned compiler toolchain.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.register<JavaExec>("architectureBoundaryTest") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.javelle.buildlogic.architecture.ArchitectureBoundaryCheckerTest")
}
