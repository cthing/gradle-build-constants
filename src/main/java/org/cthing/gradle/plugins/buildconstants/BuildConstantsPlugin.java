/*
 * Copyright 2024 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */

package org.cthing.gradle.plugins.buildconstants;

import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;


/**
 * Plugin which generates a source file containing constants for project build information.
 */
public class BuildConstantsPlugin implements Plugin<Project> {

    /**
     * Applies the plugin to the specified project.
     *
     * @param project Project to which the plugin should be applied.
     */
    @Override
    public void apply(final Project project) {
        final Project rootProject = project.getRootProject();

        project.getPluginManager().apply(JavaPlugin.class);

        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName("main", sourceSet -> {
            // Use the Gradle naming scheme for the task name.
            final String taskName = sourceSet.getTaskName("generate", "BuildConstants");

            // Use the Gradle convention for generated source output directory naming (e.g. AntlrTask).
            final Provider<Directory> taskOutputDirectory =
                    project.getLayout()
                           .getBuildDirectory()
                           .dir("generated-src/build-constants/" + sourceSet.getName());

            // Create the constants generation task and configure it with all files that contribute to the
            // build information. That way, if one is changed, the task will regenerate the constants source
            // file.
            final TaskProvider<BuildConstantsTask> constantsTask =
                    project.getTasks().register(taskName, BuildConstantsTask.class, task -> {
                        task.setDescription(String.format("Generates constants for the %s project build",
                                                          rootProject.getName()));
                        task.getOutputDirectory().convention(taskOutputDirectory);
                        task.source(rootProject.getBuildFile());

                        final Path projectDir = rootProject.getProjectDir().toPath();
                        final Path gradleProperties = projectDir.resolve("gradle.properties");
                        if (Files.exists(gradleProperties)) {
                            task.source(gradleProperties);
                        }
                        final Path versionCatalog = projectDir.resolve("gradle").resolve("libs.versions.toml");
                        if (Files.exists(versionCatalog)) {
                            task.source(versionCatalog);
                        }
                    });

            // Add the generated constants source file to the source set
            sourceSet.getJava().srcDir(taskOutputDirectory);

            // Generate the constants source file before trying to compile it.
            project.getTasks()
                   .named(sourceSet.getCompileJavaTaskName())
                   .configure(compileTask -> compileTask.dependsOn(constantsTask));
        });
    }
}
