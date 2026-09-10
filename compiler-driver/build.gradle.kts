plugins { id("org.javelle.java-conventions") }

dependencies {
    implementation(project(":compiler-core"))
    implementation(project(":java-resolver"))
    implementation(project(":workspace-model"))
}
