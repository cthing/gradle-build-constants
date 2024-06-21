/*
 * Copyright 2024 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */

package org.cthing.gradle.plugins.buildconstants;

/**
 * Specifies the accessibility of the generated constants.
 */
public enum SourceAccess {

    /**
     * Generates constants that have public access. This is the default access.
     */
    PUBLIC,

    /**
     * Generates constants that are package private.
     */
    PACKAGE,
}
