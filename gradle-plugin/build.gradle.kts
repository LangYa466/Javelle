plugins {
    id("org.javelle.java-conventions")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":workspace-model"))
}

gradlePlugin {
    plugins {
        create("javelle") {
            id = "org.javelle"
            implementationClass = "org.javelle.gradle.JavellePlugin"
        }
    }
}
