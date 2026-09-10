plugins { id("org.javelle.java-conventions") }

dependencies {
    implementation(project(":language-tooling"))
    implementation(project(":language-protocol"))
    implementation(project(":workspace-model"))
}
