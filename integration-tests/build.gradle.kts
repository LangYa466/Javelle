plugins { id("dev.teyru.java-conventions") }

dependencies {
    testImplementation(project(":testkit"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
