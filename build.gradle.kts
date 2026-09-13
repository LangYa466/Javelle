import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.Exec

plugins {
    base
    alias(libs.plugins.spotless) apply false
}

group = "dev.teyru"
version = "0.1.0-SNAPSHOT"

val expectedProductionEdges = mapOf(
    "compiler-core" to emptySet(),
    "workspace-model" to emptySet(),
    "language-protocol" to emptySet(),
    "java-resolver" to setOf("compiler-core", "workspace-model"),
    "compiler-driver" to setOf("compiler-core", "java-resolver", "workspace-model"),
    "compiler-cli" to setOf("compiler-driver"),
    "language-tooling" to setOf("compiler-driver", "workspace-model"),
    "language-server" to setOf("language-tooling", "language-protocol", "workspace-model"),
    "gradle-plugin" to setOf("workspace-model"),
    "intellij-plugin" to setOf("language-protocol"),
    "migration" to setOf("compiler-core", "compiler-driver"),
    "testkit" to emptySet(),
    "compatibility-tests" to emptySet(),
    "integration-tests" to emptySet(),
    "release-tools" to emptySet(),
)

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "com.diffplug.spotless")

    configure<SpotlessExtension> {
        java {
            googleJavaFormat(rootProject.libs.versions.google.java.format.get())
            target("src/**/*.java")
        }
    }

    dependencyLocking { lockAllConfigurations() }
}

fun actualProductionEdges(): Map<String, Set<String>> =
    subprojects.associate { project ->
        val edges: Set<String> = project.configurations
            .findByName("implementation")
            ?.dependencies
            ?.filterIsInstance<ProjectDependency>()
            ?.map { it.path.substringAfterLast(':') }
            ?.toSet()
            .orEmpty()
        project.name to edges
    }

fun findCycle(graph: Map<String, Set<String>>): List<String>? {
    val visiting = linkedSetOf<String>()
    val visited = mutableSetOf<String>()
    fun visit(node: String): List<String>? {
        if (node in visiting) {
            val path = visiting.toList()
            return path.drop(path.indexOf(node)) + node
        }
        if (!visited.add(node)) return null
        visiting.add(node)
        for (dependency in graph[node].orEmpty()) visit(dependency)?.let { return it }
        visiting.remove(node)
        return null
    }
    return graph.keys.firstNotNullOfOrNull(::visit)
}

val verifyArchitecture = tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Verifies the frozen production project DAG and forbidden source imports."
    doLast {
        val actual = actualProductionEdges()
        check(actual == expectedProductionEdges) {
            "Project dependency graph differs. expected=$expectedProductionEdges actual=$actual"
        }
        check(findCycle(actual) == null) { "Production project cycle: ${findCycle(actual)}" }

        val forbiddenImports = mapOf(
            "compiler-core" to listOf("org.gradle.", "com.intellij.", "org.eclipse.lsp4j."),
            "workspace-model" to listOf("org.gradle.", "com.intellij.", "org.eclipse.lsp4j."),
            "language-protocol" to listOf("dev.teyru.compiler", "dev.teyru.semantic"),
            "language-server" to listOf("com.intellij."),
            "intellij-plugin" to listOf("dev.teyru.compiler"),
            "gradle-plugin" to listOf("dev.teyru.compiler"),
        )
        forbiddenImports.forEach { (module, prefixes) ->
            fileTree("$module/src/main/java").matching { include("**/*.java") }.forEach { source ->
                source.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        prefixes.firstOrNull { line.trimStart().startsWith("import $it") }?.let { prefix ->
                            error("Forbidden import $prefix at ${source.path}:${index + 1}")
                        }
                    }
                }
            }
        }

        // Exercise the cycle detector independently so a broken detector cannot green the real DAG.
        check(findCycle(mapOf("a" to setOf("b"), "b" to setOf("a"))) == listOf("a", "b", "a"))
        logger.lifecycle("ARCHITECTURE_OK modules=${actual.size} edges=${actual.values.sumOf(Set<String>::size)}")
    }
}

val verifyManifests = tasks.register("verifyManifests") {
    group = "verification"
    description = "Performs bounded structural checks on tracked recovery and compatibility manifests."
    doLast {
        val required = listOf(
            ".agent/TASKS.json",
            ".agent/OWNERSHIP.json",
            "compatibility/lombok-baseline.json",
            "compatibility/lsp-methods.json",
            "compatibility/lsp-dto-gaps.json",
            "third-party/dependencies.json",
            "third-party/frontend-components.json",
        )
        required.forEach { path ->
            val text = file(path).readText(Charsets.UTF_8).trim()
            check(text.startsWith("{") && text.endsWith("}")) { "Invalid JSON object boundary: $path" }
        }
        logger.lifecycle("MANIFEST_BOUNDARIES_OK files=${required.size}")
    }
}

fun governanceTask(name: String, command: String) = tasks.register<Exec>(name) {
    group = "verification"
    commandLine("python3", ".agent/tools/governance.py", command)
}

val verifyGovernance = governanceTask("verifyGovernance", "validate")
val verifyGovernanceSelftest = governanceTask("verifyGovernanceSelftest", "selftest")
val verifyLineEndings = governanceTask("verifyLineEndings", "line-endings")
val verifyGovernanceArchive = governanceTask("verifyGovernanceArchive", "archive-repro")
val verifyProductArchives = governanceTask("verifyProductArchives", "product-archives")

val buildLogicClasses = gradle.includedBuild("build-logic").task(":classes")
val buildLogicTests = gradle.includedBuild("build-logic").task(":architectureBoundaryTest")
val verifyCompiledArchitecture = tasks.register<JavaExec>("verifyCompiledArchitecture") {
    group = "verification"
    dependsOn(buildLogicClasses, subprojects.map { it.tasks.named("classes") })
    classpath = files("build-logic/build/classes/java/main")
    mainClass.set("dev.teyru.buildlogic.architecture.ArchitectureBoundaryChecker")
    args(rootDir.absolutePath)
}

val verifyQuick = tasks.register("verifyQuick") {
    group = "verification"
    description = "Runs implemented P02 compilation, unit, format, architecture, and manifest checks."
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn(
        verifyArchitecture,
        verifyManifests,
        verifyGovernance,
        verifyGovernanceSelftest,
        verifyLineEndings,
        verifyGovernanceArchive,
        verifyProductArchives,
        verifyCompiledArchitecture,
        buildLogicTests,
    )
    doLast { logger.lifecycle("VERIFY_QUICK_PASS scope=P02 later_suites=NOT_VERIFIED") }
}

val verifyImplementedGates = tasks.register("verifyImplementedGates") {
    group = "verification"
    description = "Runs the P09/P10/P11 gates that are now implemented: the Gradle plugin TestKit consumer suite, the LSP black-box suite, and the headless IntelliJ plugin integration suite."
    dependsOn(
        ":gradle-plugin:p09TestKit",
        ":language-server:test",
        ":language-tooling:test",
        ":intellij-plugin:test",
        ":intellij-plugin:verifyPluginStructure",
    )
}

val verifyAllReadiness = tasks.register("verifyAllReadiness") {
    group = "verification"
    dependsOn(verifyImplementedGates)
    doLast {
        val missing = listOf(
            "compiler-java-differential",
            "lombok-differential",
            "idea-ui-debug",
            "website-doc-examples",
            "security-license-sbom",
        )
        throw GradleException("INCOMPLETE " + missing.joinToString(prefix = "[", postfix = "]") { "{gateId='$it',status='NOT_IMPLEMENTED'}" })
    }
}

tasks.register("verifyAll") {
    group = "verification"
    dependsOn(verifyQuick, verifyAllReadiness)
}

tasks.register("releaseCheck") {
    group = "verification"
    dependsOn(verifyQuick)
    doLast {
        throw GradleException(
            "INCOMPLETE [{gateId='verify-all',status='NOT_IMPLEMENTED'}," +
                "{gateId='signed-archives',status='NOT_IMPLEMENTED'}," +
                "{gateId='license-sbom',status='NOT_IMPLEMENTED'}," +
                "{gateId='clean-consumer',status='NOT_IMPLEMENTED'}," +
                "{gateId='release-metadata',status='NOT_IMPLEMENTED'}]"
        )
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}
