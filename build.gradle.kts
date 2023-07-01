import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

logger.quiet("Java version: ${JavaVersion.current()}")
logger.quiet("Gradle version: ${gradle.gradleVersion}")

// TODO migrate to declarative plugins
buildscript {
  repositories {
    maven {
      url = uri("https://plugins.gradle.org/m2/")
    }
  }
  dependencies {
    classpath("com.diffplug.spotless", "spotless-plugin-gradle", "6.19.0")
    classpath("com.github.spotbugs.snom", "spotbugs-gradle-plugin", "5.0.14")
    classpath("com.asarkar.gradle", "build-time-tracker", "4.3.0")
    classpath("org.unbroken-dome.gradle-plugins", "gradle-testsets-plugin", "4.0.0")
  }
}

allprojects {
  group = "com.willmolloy"
  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "java")
  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
  }

  // Spotless (code formatting/linting)
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    java {
      removeUnusedImports()
      googleJavaFormat()
    }
  }

  // Checkstyle (static analysis - code quality/style)
  apply(plugin = "checkstyle")
  configure<CheckstyleExtension> {
    toolVersion = "10.12.0"
    configFile = rootProject.file("./checkstyle.xml")
    maxErrors = 0
    maxWarnings = 0
    isIgnoreFailures = false
  }

  // SpotBugs (static analysis - find possible bugs, performance issues etc.)
  apply(plugin = "com.github.spotbugs")
  configure<SpotBugsExtension> {
    // TODO
  }
  tasks.withType<SpotBugsTask> {
    reports {
      // TODO
    }
  }

  tasks.withType<Test> {
    // run tests in parallel, assumes they"re threadsafe
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    // use JUnit 5 engine
    useJUnitPlatform()
    testLogging {
      events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
      // log the full failure messages
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
      // log the overall results (based on https://stackoverflow.com/a/36130467/6122976)
      afterSuite(KotlinClosure2<TestDescriptor, TestResult, Any>({ desc, result ->
        if (desc.parent != null) { // will match the outermost suite
          println(
            "Results: ${result.resultType} " +
                "(${result.testCount} test${if (result.testCount > 1) "s" else ""}, " +
                "${result.successfulTestCount} passed, " +
                "${result.failedTestCount} failed, " +
                "${result.skippedTestCount} skipped)"
          )
        }
      }))
    }
    finalizedBy(tasks.withType<JacocoReport>())
  }

  // JaCoCo (code coverage reporting)
  apply(plugin = "jacoco")
  tasks.withType<JacocoReport> {
    reports {
      xml.required.set(true)
      html.required.set(true)
      csv.required.set(false)
    }
  }

  // Integration test support
  // TODO test-set plugin

  dependencies {
    val log4jVersion = "2.20.0"
    val guavaVersion = "32.0.1-jre"
    "implementation"("org.apache.logging.log4j:log4j-core:$log4jVersion")
    "implementation"("com.github.spotbugs:spotbugs-annotations:4.7.3")
    "implementation"("com.google.guava:guava:$guavaVersion")

    val junitVersion = "5.9.3"
    val truthVersion = "1.1.4"
    val mockitoVersion = "5.3.1"
    "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
    "testImplementation"("com.google.truth:truth:$truthVersion")
    "testImplementation"("com.google.truth.extensions:truth-java8-extension:$truthVersion")
    "testImplementation"("org.mockito:mockito-core:$mockitoVersion")
    "testImplementation"("org.mockito:mockito-junit-jupiter:$mockitoVersion")

    // dependency cleanup, exclusions and resolutions
    configurations.all {
      // using truth instead
      exclude("org.assertj")
      resolutionStrategy {
        // so the android version isn't pulled in
        force("com.google.guava:guava:$guavaVersion")
      }
    }
  }
}