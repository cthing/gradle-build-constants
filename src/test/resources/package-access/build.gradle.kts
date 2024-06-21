import org.cthing.gradle.plugins.buildconstants.SourceAccess

plugins {
    java
    id("org.cthing.build-constants")
}

version = "1.2.3"
group = "org.cthing"

tasks {
    generateBuildConstants {
        classname = "org.cthing.test.Constants"
        sourceAccess = SourceAccess.PACKAGE
        buildTime = 1718946725000
    }
}
