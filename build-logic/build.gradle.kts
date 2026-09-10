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

tasks.register<JavaExec>("architectureBoundaryTest") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.javelle.buildlogic.architecture.ArchitectureBoundaryCheckerTest")
}
