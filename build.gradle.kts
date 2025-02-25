/*
 * Copyright 2020-2025 Cyface GmbH
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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

/**
 * The build gradle file for the Cyface Data Collector.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    // This is required to configure the dokka base plugin to include images.
    // classpath("<plugin coordinates>:<plugin version>")
    classpath("org.jetbrains.dokka:dokka-base:1.9.10")
  }
}

plugins {
  id("idea")
  // Plugin to create executable jars
  //noinspection SpellCheckingInspection
  id("com.github.johnrengelman.shadow").version("7.1.2")
  // Plugin to display the Gradle task graph
  //noinspection SpellCheckingInspection
  id("org.barfuin.gradle.taskinfo").version("2.1.0")

  id("application")
  id("maven-publish")
  kotlin("jvm").version("2.1.0")

  // For static code checks
  id("io.gitlab.arturbosch.detekt").version("1.23.0")
  // For Generation of Documentation
  id("org.jetbrains.dokka").version("1.9.10")
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
val conf = "conf.json"

tasks.run.get().args(
  listOf(
    "run",
    mainVerticleName,
    "--conf=$conf",
    "--redeploy=$watchForChange",
    "--launcher-class=${application.mainClass.get()}",
    "--on-redeploy=$doOnChange"
  )
)

kotlin {
  jvmToolchain(17)
}

val vertxVersion = "4.5.13"
val micrometerVersion = "1.10.6"
val commonsLangVersion = "3.12.0"
val logbackVersion = "1.4.14"
val gradleWrapperVersion = "7.6.3"
val googleCloudLibrariesVersion = "26.35.0"

// Versions of testing dependencies
val junitVersion = "5.9.2"
val mockitoVersion = "5.2.0"
@Suppress("ForbiddenComment")
// TODO: Remove the following. It belongs to java and should be replaced by hamKrest
val hamcrestVersion = "2.2"
val hamKrestVersion = "1.8.0.1"
val mockitoKotlinVersion = "4.1.0"
val dokkaVersion = "1.9.10"
val detektVersion = "1.23.1"
val cyfaceUploaderVersion = "1.4.1"
val testContainerVersion = "1.20.1"
val kotlinxVersion = "1.10.1"

tasks.wrapper {
  gradleVersion = gradleWrapperVersion.toString()
}

dependencies {
  // Vertx Framework
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-mongo-client:$vertxVersion")
  implementation("io.vertx:vertx-reactive-streams:$vertxVersion")

  // Kotlin Support
  implementation("io.vertx:vertx-core:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
  implementation(kotlin("stdlib-jdk8"))

  // Authentication
  implementation("io.vertx:vertx-auth-common:$vertxVersion")
  implementation("io.vertx:vertx-auth-mongo:$vertxVersion")
  implementation("io.vertx:vertx-auth-oauth2:$vertxVersion")

  // Monitoring + Metrics
  implementation("io.vertx:vertx-micrometer-metrics:$vertxVersion")
  implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

  // Logging
  implementation("ch.qos.logback:logback-classic:$logbackVersion")
  implementation("ch.qos.logback:logback-core:$logbackVersion")

  // Google Cloud Storage
  implementation(platform("com.google.cloud:libraries-bom:$googleCloudLibrariesVersion"))
  implementation("com.google.cloud:google-cloud-storage")

  // Utility
  implementation("org.apache.commons:commons-lang3:$commonsLangVersion") // Using Validate

  // Testing Dependencies
  testImplementation(platform("org.junit:junit-bom:$junitVersion"))
  testImplementation("org.junit.jupiter:junit-jupiter-params")  // Required for parameterized tests
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
  // Hamcrest and HamKrest for Kotlin
  testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")
  testImplementation("com.natpryce:hamkrest:$hamKrestVersion")
  testImplementation(kotlin("reflect")) // Required by hamkrest
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxVersion")
  testImplementation("org.mockito:mockito-core:$mockitoVersion")
  testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
  testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")

  testImplementation("io.vertx:vertx-junit5:$vertxVersion")
  testImplementation("io.vertx:vertx-web-client:$vertxVersion")
  testImplementation("de.cyface:uploader:$cyfaceUploaderVersion")
  testImplementation("org.testcontainers:testcontainers:$testContainerVersion")

  // Required to create inline documentation
  dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:$dokkaVersion")
  dokkaHtmlPlugin("org.jetbrains.dokka:dokka-base:$dokkaVersion")

  // Required for Linting
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("all")
  manifest {
    attributes(mapOf(
      "Main-Verticle" to mainVerticleName,
      "Main-Command" to "run",
    ))
  }
  mergeServiceFiles()
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
  config.from(files("$projectDir/config/detekt.yml")) // point to custom config, overwriting default behavior
  baseline = file("$projectDir/config/baseline.xml") // a way of suppressing issues before introducing detekt
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
