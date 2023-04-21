/*
 * Copyright 2020-2023 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 *  The Cyface Data Collector is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  The Cyface Data Collector is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with the Cyface Data Collector.  If not, see <http://www.gnu.org/licenses/>.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import java.net.URL
/**
 * The build gradle file for the Cyface Data Collector.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 1.0.0
 */
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    // This is required to configure the dokka base plugin to include images.
    // classpath("<plugin coordinates>:<plugin version>")
    classpath("org.jetbrains.dokka:dokka-base:1.7.10")
  }
}

plugins {
  id("eclipse")
  id("idea")
  //noinspection SpellCheckingInspection
  id("com.github.johnrengelman.shadow").version("7.1.2")
  // Plugin to display the Gradle task graph
  //noinspection SpellCheckingInspection
  id("org.barfuin.gradle.taskinfo").version("2.1.0")

  @Suppress("ForbiddenComment")
  // TODO: Remoe this as it only applies to Java
  id("java")
  id("application")
  id("maven-publish")
  kotlin("jvm").version("1.7.10")

  // For static code checks
  id("io.gitlab.arturbosch.detekt").version("1.22.0")
  // For Generation of Documentation
  id("org.jetbrains.dokka").version("1.7.10")
}
// Vert.x Gradle redeploy on file changes, see https://github.com/vert-x3/vertx-examples/tree/master/gradle-redeploy
application {
  mainClass.set("de.cyface.collector.Application")
}

group = "de.cyface"
version = "0.0.0" // Automatically overwritten by CI

val mainVerticleName = "de.cyface.collector.verticle.MainVerticle"
val watchForChange = "src/**/*"
val doOnChange = "./gradlew classes"

tasks.run.get().args(
  listOf(
    "run",
    mainVerticleName,
    "--redeploy=$watchForChange",
    "--launcher-class=${application.mainClass.get()}",
    "--on-redeploy=$doOnChange"
  )
)

@Suppress("ForbiddenComment")
// TODO: Remove this as it only applies to java
java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>() {
  options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "11"
  }
}

// If you increase this version, check in the next line if the manual mongo driver version is still necessary.
extra["vertxVersion"] = "4.3.6"
// The following is only required since Vert.x GridFS Client is not working correctly in Version 4.3.3.
// To check this run GridFSStorageIT
// We reported the problem to Vertx Github. A fix is scheduled for Vertx 4.4.2
// https://github.com/vert-x3/vertx-mongo-client/issues/291
extra["mongoDriverVersion"] = "4.8.0"
extra["micrometerVersion"] = "1.10.6"
extra["slf4jVersion"] = "2.0.7"
extra["commonsLangVersion"] = "3.12.0"
extra["logbackVersion"] = "1.4.6"
extra["cyfaceApiVersion"] = "2.1.2"
extra["cyfaceSerializationVersion"] = "2.3.6"
extra["gradleWrapperVersion"] = "7.6.1"
extra["googleCloudLibrariesVersion"] = "26.12.0"

// Versions of testing dependencies
extra["junitVersion"] = "5.9.2"
extra["mockitoVersion"] = "5.2.0"
@Suppress("ForbiddenComment")
// TODO: Remove the following. It belongs to java and should be replaced by hamKrest
extra["hamcrestVersion"] = "2.2"
extra["hamKrestVersion"] = "1.8.0.1"
extra["flapdoodleVersion"] = "3.5.3" // major upgrade available
extra["mockitoKotlinVersion"] = "4.1.0"
extra["dokkaVersion"] = "1.7.10"
extra["detektVersion"] = "1.22.0"

tasks.wrapper {
  gradleVersion = project.extra["gradleWrapperVersion"].toString()
}

dependencies {
  // Internal Cyface Dependencies
  implementation("de.cyface:api:${project.extra["cyfaceApiVersion"]}")
  implementation("de.cyface:model:${project.extra["cyfaceSerializationVersion"]}")

  // Vertx Framework
  implementation("io.vertx:vertx-web:${project.extra["vertxVersion"]}")
  implementation("io.vertx:vertx-mongo-client:${project.extra["vertxVersion"]}")
  implementation("io.vertx:vertx-reactive-streams:${project.extra["vertxVersion"]}")
  // These two are required since Vert.x currently has a buggy version.
  implementation("org.mongodb:mongodb-driver-core:${project.extra["mongoDriverVersion"]}")
  implementation("org.mongodb:mongodb-driver-reactivestreams:${project.extra["mongoDriverVersion"]}")

  // Kotlin Support
  implementation("io.vertx:vertx-core:${project.extra["vertxVersion"]}")
  implementation("io.vertx:vertx-lang-kotlin:${project.extra["vertxVersion"]}")
  implementation(kotlin("stdlib-jdk8"))

  // Authentication
  implementation("io.vertx:vertx-auth-common:${project.extra["vertxVersion"]}")
  implementation("io.vertx:vertx-auth-mongo:${project.extra["vertxVersion"]}")
  implementation("io.vertx:vertx-auth-jwt:${project.extra["vertxVersion"]}")

  // Monitoring + Metrics
  implementation("io.vertx:vertx-micrometer-metrics:${project.extra["vertxVersion"]}")
  implementation("io.micrometer:micrometer-registry-prometheus:${project.extra["micrometerVersion"]}")

  // Logging
  implementation("ch.qos.logback:logback-classic:${project.extra["logbackVersion"]}")
  implementation("ch.qos.logback:logback-core:${project.extra["logbackVersion"]}")

  // Google Cloud Storage
  implementation(platform("com.google.cloud:libraries-bom:${project.extra["googleCloudLibrariesVersion"]}"))
  implementation("com.google.cloud:google-cloud-storage")

  // Utility
  implementation("org.apache.commons:commons-lang3:${project.extra["commonsLangVersion"]}") // Using Validate

  // Testing Dependencies
  testImplementation(platform("org.junit:junit-bom:${project.extra["junitVersion"]}"))
  testImplementation("org.junit.jupiter:junit-jupiter-params")  // Required for parameterized tests
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.junit.jupiter:junit-jupiter:${project.extra["junitVersion"]}")
  // Hamcrest and HamKrest for Kotlin
  testImplementation("org.hamcrest:hamcrest:${project.extra["hamcrestVersion"]}")
  testImplementation("com.natpryce:hamkrest:${project.extra["hamKrestVersion"]}")
  testImplementation(kotlin("reflect")) // Required by hamkrest
  testImplementation(kotlin("test"))
  testImplementation("org.mockito:mockito-core:${project.extra["mockitoVersion"]}")
  testImplementation("org.mockito:mockito-junit-jupiter:${project.extra["mockitoVersion"]}")
  testImplementation("org.mockito.kotlin:mockito-kotlin:${project.extra["mockitoKotlinVersion"]}")

  testImplementation("io.vertx:vertx-junit5:${project.extra["vertxVersion"]}")
  testImplementation("io.vertx:vertx-web-client:${project.extra["vertxVersion"]}")
  // This is required to run an embedded Mongo instance for integration testing.
  testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:${project.extra["flapdoodleVersion"]}")

  // Required to create inline documentation
  dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${project.extra["dokkaVersion"]}")
  dokkaHtmlPlugin("org.jetbrains.dokka:dokka-base:${project.extra["dokkaVersion"]}")

  // Required for Linting
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${project.extra["detektVersion"]}")
}

tasks.test {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")

    // Also show assert message (e.g. on the CI) when tests fail to identify cause
    showExceptions = true
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showCauses = true
    showStackTraces = true
    showStandardStreams = false
  }
}

// Definitions for the maven-publish Plugin
publishing {
  // The following repositories are used to publish artifacts to.
  repositories {
    maven {
      name = "github"
      url = uri("https://maven.pkg.github.com/cyface-de/data-collector")
      credentials {
        username = (project.findProperty("gpr.user") ?: System.getenv("USERNAME")) as String
        password = (project.findProperty("gpr.key") ?: System.getenv("PASSWORD")) as String
      }
    }
    maven {
      name = "local"
      url = uri("file://${rootProject.buildDir}/repo")
    }
  }
}

// The following repositories are used to load artifacts from.
repositories {
  mavenCentral()
  maven {
    name = "local"
    url = uri("file://${rootProject.buildDir}/repo")
  }
  maven {
    name = "github"
    url = uri("https://maven.pkg.github.com/cyface-de/serializer")
    credentials {
      username = (project.findProperty("gpr.user") ?: System.getenv("USERNAME")) as String
      password = (project.findProperty("gpr.key") ?: System.getenv("PASSWORD")) as String
    }
  }
  maven {
    name = "github"
    url = uri("https://maven.pkg.github.com/cyface-de/api")
    credentials {
      username = (project.findProperty("gpr.user") ?: System.getenv("USERNAME")) as String
      password = (project.findProperty("gpr.key") ?: System.getenv("PASSWORD")) as String
    }
  }
}

tasks.shadowJar {

  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles {
    include("META-INF/services/io.vertx.core.spi.VerticleFactory")
  }
}

/**
 * This is only used in dev environment.
 * <p>
 * This avoids copying a JAR into `src`.
 */
tasks.register<Copy>("copyToDockerBuildFolder") {
  dependsOn(tasks.shadowJar)
  into("./build/docker/")
  from("./src/main/docker/")
  from(tasks.shadowJar.get().outputs)
  rename("collector-(.*)-all.jar","collector-all.jar")
}

publishing {
  publications {
    //noinspection GroovyAssignabilityCheck
    create<MavenPublication>("publishExecutable") {
      //noinspection GroovyAssignabilityCheck
      from(components["java"])
    }
  }
}

// Begin detekt configuration
detekt {
  buildUponDefaultConfig = true // preconfigure defaults
  allRules = false // activate all available (even unstable) rules.
  config = files("$projectDir/config/detekt.yml") // point to custom config, overwriting default behavior
  //baseline = file("$projectDir/config/baseline.xml") // a way of suppressing issues before introducing detekt
}

tasks.withType<Detekt>().configureEach {
  reports {
    html.required.set(true) // observe findings in your browser with structure and code snippets
    // xml.required.set(true) // checkstyle like format mainly for integrations like Jenkins
    // txt.required.set(true) // similar to the console output, contains issue signature to manually edit baseline files
    // sarif.required.set(true) // SARIF format (https://sarifweb.azurewebsites.net/) integrate with Github Code Scan
    // md.required.set(true) // simple Markdown format
  }
}

tasks.withType<Detekt>().configureEach {
  jvmTarget = "11"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
  jvmTarget = "11"
}

// End detekt configuration

tasks.withType<DokkaTask>().configureEach {
  outputDirectory.set(file("doc/"))
  pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
    customAssets = listOf(file("doc/storage-service.png"))
  }
  dokkaSourceSets {
    named("main") {
      includes.from("README.md")
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(URL("https://github.com/cyface-de/data-collector/build/dokka/"))
      }
    }
  }
}
