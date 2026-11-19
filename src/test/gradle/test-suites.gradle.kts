// Named test suites, selected by JUnit tags (see src/test/java/.../support/*.java).
//
// Activate with ONE line at the bottom of build.gradle.kts:
//     apply(from = "src/test/gradle/test-suites.gradle.kts")
//
// ./gradlew test               -> every in-process test (the default CI gate)
// ./gradlew unitTest           -> fast, no Spring context
// ./gradlew integrationTest    -> real HTTP stack through MockMvc, shared Spring context
// ./gradlew contractTest       -> response shapes, error envelope, status matrix, OpenAPI breaking-change guard
// ./gradlew architectureTest   -> layering and design rules
// ./gradlew smokeTest          -> seconds: is it alive and are the main doors open
// ./gradlew sanityTest         -> minutes: the core product flows still work end to end
// ./gradlew regressionTest     -> everything in-process, selected by tag

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

val testSourceSet = the<SourceSetContainer>()["test"]

fun registerSuite(taskName: String, suiteDescription: String, vararg tags: String) {
    tasks.register<Test>(taskName) {
        group = "verification"
        description = suiteDescription
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform { includeTags(*tags) }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }
}

registerSuite("unitTest", "Fast unit tests (no Spring context).", "unit")
registerSuite("integrationTest", "API tests through the full HTTP stack (MockMvc).", "integration")
registerSuite("contractTest", "API contract tests: shapes, errors, status matrix, OpenAPI baseline.", "contract")
registerSuite("architectureTest", "Architecture and design rules.", "architecture")
registerSuite("smokeTest", "Smoke suite: is the system alive?", "smoke")
registerSuite("sanityTest", "Sanity suite: do the core flows still work?", "sanity")
registerSuite(
    "regressionTest", "Full in-process regression: every tagged suite.",
    "unit", "integration", "contract", "architecture", "smoke", "sanity", "regression"
)
