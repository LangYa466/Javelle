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
    dependsOn(":workspace-model:jar")
    doFirst {
        val modelJar = project(":workspace-model").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        systemProperty("workspaceModelJar", modelJar.absolutePath)
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
