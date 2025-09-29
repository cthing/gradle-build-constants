/*
 * Copyright 2024 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */

package org.cthing.gradle.plugins.buildconstants;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;


/**
 * Performs reading of the build information and generation of the build constants class.
 */
public abstract class BuildConstantsTask extends SourceTask {

    private static final Logger LOGGER = Logging.getLogger(BuildConstantsTask.class);

    public BuildConstantsTask() {
        setGroup("Generate Constants");

        final Project project = getProject();
        final Project rootProject = project.getRootProject();
        getSourceAccess().convention(SourceAccess.PUBLIC);
        getProjectName().convention(project.provider(rootProject::getName));
        getProjectVersion().convention(project.provider(rootProject::getVersion));
        getProjectGroup().convention(project.provider(rootProject::getGroup));
        getBuildTime().convention(System.currentTimeMillis());
    }

    /**
     * Obtains the fully qualified name for the generated class (e.g. org.cthing.myapp.PropertyConstants).
     *
     * @return Fully qualified class name.
     */
    @Input
    public abstract Property<String> getClassname();

    /**
     * Obtains the location on the filesystem for the generated class.
     *
     * @return Output directory.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Obtains the access modifier for the generated constants. The default is {@link SourceAccess#PUBLIC}.
     *
     * @return Access modifier for the generated constants.
     */
    @Input
    public abstract Property<SourceAccess> getSourceAccess();

    /**
     * Obtains the name of the project. The default is obtained from {@link Project#getName()}.
     *
     * @return Name of the project
     */
    @Input
    public abstract Property<String> getProjectName();

    /**
     * Obtains the version of the project. The default is obtained from {@link Project#getVersion()}.
     *
     * @return Version of the project
     */
    @Input
    public abstract Property<Object> getProjectVersion();

    /**
     * Obtains the group name of the project. The default is obtained from {@link Project#getGroup()}.
     *
     * @return Group name of the project
     */
    @Input
    public abstract Property<Object> getProjectGroup();

    /**
     * Obtains the time the project was built as the number of milliseconds since the Unix Epoch.
     * The default is the current time.
     *
     * @return Milliseconds since the Unix Epoch.
     */
    @Input
    public abstract Property<Long> getBuildTime();

    /**
     * Provides the capability to add custom constants to the source file. The constants will be written sorted
     * by name (i.e. key). Integer, long and boolean values are written as their respective types. All other types
     * are written using the value of their {@link Object#toString()}. If a value is {@code null}, the constant will
     * not be written. The following constant names <b>must not be used</b> for custom constants: "PROJECT_NAME",
     * "PROJECT_VERSION", "PROJECT_GROUP", "BUILD_TIME" and "BUILD_DATE".
     *
     * @return Map for custom constants and their values.
     */
    @Optional
    @Input
    public abstract MapProperty<String, Object> getAdditionalConstants();

    /**
     * Generates the build constants class.
     */
    @TaskAction
    public void generateConstants() {
        final Provider<String> pathname = getClassname().map(cname -> cname.replace('.', '/') + ".java");
        final File classFile = getOutputDirectory().file(pathname).get().getAsFile();
        final File parentFile = classFile.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) {
            throw new GradleException("Could not create directories " + parentFile);
        }

        final String cname = getClassname().get();
        final int pos = cname.lastIndexOf('.');
        assert pos != -1;
        final String packageName = cname.substring(0, pos);
        final String className = cname.substring(pos + 1);

        try (PrintWriter writer = new PrintWriter(classFile, StandardCharsets.UTF_8)) {
            LOGGER.info("Writing constants class {}.{}", packageName, className);
            writeConstants(writer, packageName, className);
        } catch (final IOException ex) {
            throw new TaskExecutionException(this, ex);
        }
    }

    /**
     * Performs the work of writing the Java class file containing the build information constants.
     *
     * @param writer  Writes the file
     * @param packageName  The Java package containing the class
     * @param className  Name of the top level class (not qualified by the package name)
     */
    private void writeConstants(final PrintWriter writer, final String packageName, final String className) {
        final String modifier = getSourceAccess().get() == SourceAccess.PUBLIC ? "public " : "";
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        writer.format("""
                      //
                      // DO NOT EDIT - File generated by the org.cthing.build-constants Gradle plugin.
                      //

                      package %s;

                      @SuppressWarnings("all")
                      %sfinal class %s {

                      """, packageName, modifier, className);

        writer.format("    %sstatic final String PROJECT_NAME = \"%s\";%n", modifier, getProjectName().get());
        writer.format("    %sstatic final String PROJECT_VERSION = \"%s\";%n", modifier, getProjectVersion().get());
        writer.format("    %sstatic final String PROJECT_GROUP = \"%s\";%n", modifier, getProjectGroup().get());
        writer.format("    %sstatic final long BUILD_TIME = %dL;%n", modifier, getBuildTime().get());
        writer.format("    %sstatic final String BUILD_DATE = \"%s\";%n", modifier,
                      dateFormat.format(new Date(getBuildTime().get())));

        getAdditionalConstants().keySet().get().stream().sorted().forEach(key -> {
            final Object value = getAdditionalConstants().getting(key).getOrNull();
            if (value != null) {
                if (value instanceof Integer v) {
                    writer.format("    %sstatic final int %s = %d;%n", modifier, key, v);
                } else if (value instanceof Long v) {
                    writer.format("    %sstatic final long %s = %dL;%n", modifier, key, v);
                } else if (value instanceof Boolean v) {
                    writer.format("    %sstatic final boolean %s = %b;%n", modifier, key, v);
                } else {
                    writer.format("    %sstatic final String %s = \"%s\";%n", modifier, key, value);
                }
            }
        });

        writer.format("""

                          private %s() { }
                      }
                      """, className);
    }
}
