/*
 * Copyright 2024 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */

package org.cthing.gradle.plugins.buildconstants;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;


public class PluginApplyTest {

    @Test
    public void testApply(@TempDir final File projectDir) {
        final Project project = ProjectBuilder.builder().withName("testProject").withProjectDir(projectDir).build();
        project.getPluginManager().apply("org.cthing.build-constants");

        final Task mainTask = project.getTasks().findByName("generateBuildConstants");
        assertThat(mainTask).isNotNull().isInstanceOf(BuildConstantsTask.class);

        final BuildConstantsTask task = (BuildConstantsTask)mainTask;
        assertThat(task.getClassname().isPresent()).isFalse();
        assertThat(task.getOutputDirectory().get().getAsFile().getPath())
                .endsWith("build/generated-src/build-constants/main");
        assertThat(task.getSourceAccess().get()).isEqualTo(SourceAccess.PUBLIC);
        assertThat(task.getProjectName().get()).isEqualTo("testProject");
        assertThat(task.getProjectVersion().get()).hasToString("unspecified");
        assertThat(task.getProjectGroup().get()).hasToString("");
    }
}
