plugins { id("org.javelle.java-conventions") }

dependencies {
    implementation(project(":compiler-driver"))
    implementation(project(":workspace-model"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
