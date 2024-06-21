plugins {
    java
    id("org.cthing.build-constants")
}

version = "1.2.3"
group = "org.cthing"

tasks {
    generateBuildConstants {
        classname = "org.cthing.test.Constants"
        buildTime = 1718946725000
        additionalConstants.put("xyz", 17)
        additionalConstants.put("tuv", 2300L)
        additionalConstants.put("CUSTOM3", true)
        additionalConstants.put("CUSTOM2", "World")
        additionalConstants.put("CUSTOM1", "Hello")
        additionalConstants.put("ABC", "def")
    }
}
