/*
 * Copyright 2024 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */

package org.cthing.gradle.plugins.buildconstants;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.commons.io.file.PathUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.params.provider.Arguments.arguments;


public class PluginIntegTest {

    private Path projectDir;

    public static Stream<Arguments> gradleVersionProvider() {
        return Stream.of(
                arguments("8.2"),
                arguments(GradleVersion.current().getVersion())
        );
    }

    @BeforeEach
    public void setup() throws IOException {
        final Path baseDir = Path.of(System.getProperty("buildDir"), "integTest");
        Files.createDirectories(baseDir);
        this.projectDir = Files.createTempDirectory(baseDir, null);
    }

    @ParameterizedTest
    @MethodSource("gradleVersionProvider")
    public void testGeneratePublic(final String gradleVersion) throws IOException {
        copyProject("public-access");

        final BuildResult result = createGradleRunner(gradleVersion).build();
        verifyBuild(result);

        final Class<?> cls = loadClass();
        assertThat(cls).isPublic().isFinal();

        verifyConstant(cls, "PROJECT_NAME", "testProject", SourceAccess.PUBLIC);
        verifyConstant(cls, "PROJECT_VERSION", "1.2.3", SourceAccess.PUBLIC);
        verifyConstant(cls, "PROJECT_GROUP", "org.cthing", SourceAccess.PUBLIC);
        verifyConstant(cls, "BUILD_TIME", 1718946725000L, SourceAccess.PUBLIC);
        verifyConstant(cls, "BUILD_DATE", "2024-06-21T05:12:05Z", SourceAccess.PUBLIC);
    }

    @ParameterizedTest
    @MethodSource("gradleVersionProvider")
    public void testGeneratePackage(final String gradleVersion) throws IOException {
        copyProject("package-access");

        final BuildResult result = createGradleRunner(gradleVersion).build();
        verifyBuild(result);

        final Class<?> cls = loadClass();
        assertThat(cls).isPackagePrivate().isFinal();

        verifyConstant(cls, "PROJECT_NAME", "testProject", SourceAccess.PACKAGE);
        verifyConstant(cls, "PROJECT_VERSION", "1.2.3", SourceAccess.PACKAGE);
        verifyConstant(cls, "PROJECT_GROUP", "org.cthing", SourceAccess.PACKAGE);
        verifyConstant(cls, "BUILD_TIME", 1718946725000L, SourceAccess.PACKAGE);
        verifyConstant(cls, "BUILD_DATE", "2024-06-21T05:12:05Z", SourceAccess.PACKAGE);
    }

    @ParameterizedTest
    @MethodSource("gradleVersionProvider")
    public void testConfiguredValues(final String gradleVersion) throws IOException {
        copyProject("configured-values");

        final BuildResult result = createGradleRunner(gradleVersion).build();
        verifyBuild(result);

        final Class<?> cls = loadClass();
        assertThat(cls).isPublic().isFinal();

        verifyConstant(cls, "PROJECT_NAME", "MyProject", SourceAccess.PUBLIC);
        verifyConstant(cls, "PROJECT_VERSION", "4.3.2", SourceAccess.PUBLIC);
        verifyConstant(cls, "PROJECT_GROUP", "com.cthing", SourceAccess.PUBLIC);
        verifyConstant(cls, "BUILD_TIME", 1718946725000L, SourceAccess.PUBLIC);
        verifyConstant(cls, "BUILD_DATE", "2024-06-21T05:12:05Z", SourceAccess.PUBLIC);
    }

    @ParameterizedTest
    @MethodSource("gradleVersionProvider")
    public void testAdditionalConstants(final String gradleVersion) throws IOException {
        copyProject("additional-constants");

        final BuildResult result = createGradleRunner(gradleVersion).build();
        verifyBuild(result);

        final Class<?> cls = loadClass();
        assertThat(cls).isPublic().isFinal();

        verifyConstant(cls, "PROJECT_NAME", "testProject", SourceAccess.PUBLIC);
        verifyConstant(cls, "PROJECT_VERSION", "1.2.3", SourceAccess.PUBLIC);
        verifyConstant(cls, "PROJECT_GROUP", "org.cthing", SourceAccess.PUBLIC);
        verifyConstant(cls, "BUILD_TIME", 1718946725000L, SourceAccess.PUBLIC);
        verifyConstant(cls, "BUILD_DATE", "2024-06-21T05:12:05Z", SourceAccess.PUBLIC);
        verifyConstant(cls, "ABC", "def", SourceAccess.PUBLIC);
        verifyConstant(cls, "CUSTOM1", "Hello", SourceAccess.PUBLIC);
        verifyConstant(cls, "CUSTOM2", "World", SourceAccess.PUBLIC);
        verifyConstant(cls, "CUSTOM3", true, SourceAccess.PUBLIC);
        verifyConstant(cls, "tuv", 2300L, SourceAccess.PUBLIC);
        verifyConstant(cls, "xyz", 17, SourceAccess.PUBLIC);
    }

    private void copyProject(final String projectName) throws IOException {
        final URL projectUrl = getClass().getResource("/" + projectName);
        assertThat(projectUrl).isNotNull();
        PathUtils.copyDirectory(Path.of(projectUrl.getPath()), this.projectDir);
    }

    private GradleRunner createGradleRunner(final String gradleVersion) {
        return GradleRunner.create()
                           .withProjectDir(this.projectDir.toFile())
                           .withArguments("generateBuildConstants", "build")
                           .withPluginClasspath()
                           .withGradleVersion(gradleVersion);
    }

    private void verifyBuild(final BuildResult result) {
        final BuildTask genTask = result.task(":generateBuildConstants");
        assertThat(genTask).isNotNull();
        assertThat(genTask.getOutcome()).as(result.getOutput()).isEqualTo(SUCCESS);

        final BuildTask buildTask = result.task(":build");
        assertThat(buildTask).isNotNull();
        assertThat(buildTask.getOutcome()).as(result.getOutput()).isEqualTo(SUCCESS);

        final Path actualSource = this.projectDir.resolve("build/generated-src/build-constants/main/org/cthing/test/Constants.java");
        assertThat(actualSource).isRegularFile();

        final Path expectedSource = this.projectDir.resolve("Constants.java");
        assertThat(actualSource).hasSameTextualContentAs(expectedSource, StandardCharsets.UTF_8);

        final Path classFile = this.projectDir.resolve("build/classes/java/main/org/cthing/test/Constants.class");
        assertThat(classFile).isRegularFile();
    }

    private void verifyConstant(final Class<?> cls, final String fieldName, final Object fieldValue,
                                    final SourceAccess access) throws IOException {
        try {
            assertThat(cls).hasDeclaredFields(fieldName);

            final Field field = cls.getDeclaredField(fieldName);
            assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
            if (access == SourceAccess.PUBLIC) {
                assertThat(Modifier.isPublic(field.getModifiers())).isTrue();
            } else {
                assertThat(Modifier.isPublic(field.getModifiers())).isFalse();

                field.setAccessible(true);
            }
            assertThat(field.get(null)).isEqualTo(fieldValue);
        } catch (final IllegalAccessException | NoSuchFieldException ex) {
            throw new IOException(ex);
        }
    }

    private Class<?> loadClass() throws IOException {
        final Path classesDir = this.projectDir.resolve("build/classes/java/main");
        try (URLClassLoader loader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() })) {
            return loader.loadClass("org.cthing.test.Constants");
        } catch (final ClassNotFoundException ex) {
            throw new IOException(ex);
        }
    }
}
