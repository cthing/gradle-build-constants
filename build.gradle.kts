import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.Confidence
import org.cthing.projectversion.BuildType
import org.cthing.projectversion.ProjectVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

repositories {
    mavenCentral()
}

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    `java-gradle-plugin`
    checkstyle
    jacoco
    signing
    alias(libs.plugins.cthingVersioning)
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.versions)
}

version = ProjectVersion("2.0.0", BuildType.snapshot)
group = "org.cthing"
description = "A Gradle plugin that generates a Java source file with constants describing the build."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

gradlePlugin {
    website = "https://github.com/cthing/gradle-build-constants"
    vcsUrl = "https://github.com/cthing/gradle-build-constants"

    plugins {
        create("buildConstantsPlugin") {
            id = "org.cthing.build-constants"
            displayName = "Java constants from build information"
            description = "A Gradle plugin that generates a Java source file with constants describing the build."
            tags = listOf("build", "project", "constants")
            implementationClass = "org.cthing.gradle.plugins.buildconstants.BuildConstantsPlugin"
        }
    }
}

dependencies {
    implementation(libs.jspecify)

    testImplementation(libs.assertJ)
    testImplementation(libs.commonsIO)
    testImplementation(libs.junitApi)
    testImplementation(libs.junitParams)

    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.junitLauncher)

    spotbugsPlugins(libs.spotbugsContrib)
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    isIgnoreFailures = false
    configFile = file("dev/checkstyle/checkstyle.xml")
    configDirectory = file("dev/checkstyle")
    isShowViolations = true
}

spotbugs {
    toolVersion = libs.versions.spotbugs
    ignoreFailures = false
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = file("dev/spotbugs/suppressions.xml")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks {
    withType<JavaCompile> {
        options.release = libs.versions.java.get().toInt()
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Werror"))
    }

    withType<Jar> {
        manifest.attributes(mapOf("Implementation-Title" to project.name,
                                  "Implementation-Vendor" to "C Thing Software",
                                  "Implementation-Version" to project.version))
    }

    withType<Javadoc> {
        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        with(options as StandardJavadocDocletOptions) {
            breakIterator(false)
            encoding("UTF-8")
            bottom("Copyright &copy; $year C Thing Software")
            addStringOption("Xdoclint:all,-missing", "-quiet")
            addStringOption("Werror", "-quiet")
            memberLevel = JavadocMemberLevel.PUBLIC
            outputLevel = JavadocOutputLevel.QUIET
        }
    }

    check {
        dependsOn(buildHealth)
    }

    spotbugsMain {
        reports.create("html").required = true
    }

    spotbugsTest {
        isEnabled = false
    }

    publishPlugins {
        doFirst {
            if (!project.hasProperty("gradle.publish.key") || !project.hasProperty("gradle.publish.secret")) {
                throw GradleException("Gradle Plugin Portal credentials not defined")
            }
        }
    }

    withType<JacocoReport> {
        dependsOn("test")
        with(reports) {
            xml.required = false
            csv.required = false
            html.required = true
            html.outputLocation = layout.buildDirectory.dir("reports/jacoco")
        }
    }

    withType<Test> {
        useJUnitPlatform()

        systemProperty("buildDir", layout.buildDirectory.get().asFile)
    }

    withType<GenerateModuleMetadata> {
        enabled = false
    }

    withType<Sign>().configureEach {
        onlyIf("Signing credentials are present") {
            hasProperty("signing.keyId") && hasProperty("signing.password") && hasProperty("signing.secretKeyRingFile")
        }
    }

    dependencyUpdates {
        revision = "release"
        gradleReleaseChannel = "current"
        outputFormatter = "plain,xml,html"
        outputDir = layout.buildDirectory.dir("reports/dependencyUpdates").get().asFile.absolutePath

        rejectVersionIf {
            isNonStable(candidate.version)
        }
    }
}

publishing {
    val repoUrl = if ((version as ProjectVersion).isSnapshotBuild)
        findProperty("cthing.nexus.snapshotsUrl") else findProperty("cthing.nexus.candidatesUrl")
    if (repoUrl != null) {
        repositories {
            maven {
                name = "CThingMaven"
                setUrl(repoUrl)
                credentials {
                    username = property("cthing.nexus.user") as String
                    password = property("cthing.nexus.password") as String
                }
            }
        }
    }
}
